package com.homepilot.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.homepilot.app.model.*
import com.homepilot.app.network.RetrofitClient
import com.homepilot.app.repository.HomeAssistantRepository
import com.homepilot.app.util.PreferencesManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val prefsManager = PreferencesManager(application)
    private val scope = viewModelScope

    val homeButtons: StateFlow<List<HomeButton>> = prefsManager.homeButtonsFlow
        .stateIn(scope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _deviceStates = MutableStateFlow<Map<String, ButtonState>>(emptyMap())
    val deviceStates: StateFlow<Map<String, ButtonState>> = _deviceStates

    // Device groups for the Add dialog
    private val _deviceGroups = MutableStateFlow<List<DeviceGroup>>(emptyList())
    val deviceGroups: StateFlow<List<DeviceGroup>> = _deviceGroups

    private val _isLoadingGroups = MutableStateFlow(false)
    val isLoadingGroups: StateFlow<Boolean> = _isLoadingGroups

    // Track whether the repo has been initialized
    private val _repoReady = MutableStateFlow(false)

    private var repository: HomeAssistantRepository? = null
    private var currentConfig: ServerConfig? = null
    private var refreshJob: Job? = null

    init {
        // Observe server config → initialize repository
        scope.launch {
            prefsManager.serverConfigFlow.collect { config ->
                if (config.host.isNotBlank()) {
                    currentConfig = config
                    try {
                        val api = RetrofitClient.getApi(config)
                        repository = HomeAssistantRepository(api, config)
                        _repoReady.value = true
                        // Auto-load device groups when repo is ready
                        loadDeviceGroups()
                    } catch (e: Exception) {
                        _repoReady.value = false
                    }
                }
            }
        }
        // Observe button list changes → refresh device states
        scope.launch {
            homeButtons.collect { buttons ->
                if (buttons.isNotEmpty()) {
                    refreshDeviceStates(buttons)
                    startPeriodicRefresh(buttons)
                } else {
                    refreshJob?.cancel()
                    _deviceStates.value = emptyMap()
                }
            }
        }
    }

    // ─── Button management ──────────────────────────────────────

    fun addButton(entityId: String, displayName: String, iconName: String) {
        scope.launch {
            val current = homeButtons.value.toMutableList()
            if (current.any { it.entityId == entityId }) return@launch
            current.add(HomeButton(entityId, displayName, iconName))
            prefsManager.saveHomeButtons(current)
        }
    }

    fun removeButton(entityId: String) {
        scope.launch {
            val current = homeButtons.value.toMutableList()
            current.removeAll { it.entityId == entityId }
            prefsManager.saveHomeButtons(current)
        }
    }

    fun renameButton(entityId: String, newName: String) {
        scope.launch {
            val current = homeButtons.value.toMutableList()
            val idx = current.indexOfFirst { it.entityId == entityId }
            if (idx >= 0) {
                current[idx] = current[idx].copy(displayName = newName)
                prefsManager.saveHomeButtons(current)
            }
        }
    }

    fun updateButtonIcon(entityId: String, newIconName: String) {
        scope.launch {
            val current = homeButtons.value.toMutableList()
            val idx = current.indexOfFirst { it.entityId == entityId }
            if (idx >= 0) {
                current[idx] = current[idx].copy(iconName = newIconName)
                prefsManager.saveHomeButtons(current)
            }
        }
    }

    // ─── Execute device command ─────────────────────────────────

    fun executeButton(entityId: String) {
        scope.launch {
            _deviceStates.value = _deviceStates.value + (entityId to ButtonState.LOADING)
            repository?.let { repo ->
                repo.toggleEntity(entityId)
                    .onSuccess {
                        delay(300)
                        refreshSingleState(entityId)
                    }
                    .onFailure {
                        _deviceStates.value = _deviceStates.value + (entityId to ButtonState.FAILED)
                    }
            } ?: run {
                _deviceStates.value = _deviceStates.value + (entityId to ButtonState.FAILED)
            }
        }
    }

    fun refreshSingleState(entityId: String) {
        scope.launch {
            repository?.let { repo ->
                repo.getEntityState(entityId)
                    .onSuccess { entity ->
                        _deviceStates.value = _deviceStates.value + (entityId to haStateToButtonState(entity.state))
                    }
                    .onFailure {
                        _deviceStates.value = _deviceStates.value + (entityId to ButtonState.FAILED)
                    }
            }
        }
    }

    private suspend fun refreshDeviceStates(buttons: List<HomeButton>) {
        val entityIds = buttons.map { it.entityId }
        repository?.let { repo ->
            repo.getStatesForButtons(entityIds)
                .onSuccess { entities ->
                    val stateMap = entities.associate { entity ->
                        entity.entityId to haStateToButtonState(entity.state)
                    }
                    _deviceStates.value = _deviceStates.value + stateMap
                }
        }
    }

    private fun startPeriodicRefresh(buttons: List<HomeButton>) {
        refreshJob?.cancel()
        refreshJob = scope.launch {
            while (isActive) {
                delay(30_000)
                refreshDeviceStates(buttons)
            }
        }
    }

    // ─── Device groups for dialog ───────────────────────────────

    /**
     * Load device groups for the Add dialog.
     * Waits for repository to be ready if needed.
     */
    fun loadDeviceGroups() {
        scope.launch {
            // If repo not ready yet, wait for it (max 10s)
            if (repository == null) {
                _isLoadingGroups.value = true
                withTimeoutOrNull(10_000) {
                    _repoReady.first { it }
                }
                if (repository == null) {
                    _isLoadingGroups.value = false
                    _deviceGroups.value = emptyList()
                    return@launch
                }
            }

            _isLoadingGroups.value = true
            val repo = repository!!

            repo.getControllableDevicesGrouped()
                .onSuccess { groups ->
                    _deviceGroups.value = groups
                }
                .onFailure { error ->
                    // Fallback: domain-level grouping
                    repo.getAllEntities().onSuccess { entities ->
                        val controllable = entities.filter {
                            it.domain in Entity.CONTROLLABLE_DOMAINS
                        }
                        _deviceGroups.value = controllable.groupBy { it.domain }
                            .map { (domain, list) ->
                                DeviceGroup(name = domain.uppercase(), entities = list)
                            }
                    }
                }

            _isLoadingGroups.value = false
        }
    }

    // ─── Helpers ────────────────────────────────────────────────

    fun getButtonState(entityId: String): ButtonState {
        return _deviceStates.value[entityId] ?: ButtonState.IDLE
    }

    private fun haStateToButtonState(haState: String): ButtonState = when (haState) {
        "on" -> ButtonState.SUCCESS
        "off" -> ButtonState.IDLE
        "unavailable" -> ButtonState.FAILED
        else -> ButtonState.IDLE
    }
}
