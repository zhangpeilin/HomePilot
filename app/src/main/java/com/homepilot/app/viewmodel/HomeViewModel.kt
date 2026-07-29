package com.homepilot.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.homepilot.app.model.*
import com.homepilot.app.network.HaStateSubscriber
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

    private val _deviceGroups = MutableStateFlow<List<DeviceGroup>>(emptyList())
    val deviceGroups: StateFlow<List<DeviceGroup>> = _deviceGroups

    private val _isLoadingGroups = MutableStateFlow(false)
    val isLoadingGroups: StateFlow<Boolean> = _isLoadingGroups

    private val _repoReady = MutableStateFlow(false)
    private val _selectOptions = MutableStateFlow<Map<String, List<String>>>(emptyMap())
    val selectOptions: StateFlow<Map<String, List<String>>> = _selectOptions

    private var repository: HomeAssistantRepository? = null
    private var currentConfig: ServerConfig? = null
    private var stateSubscriber: HaStateSubscriber? = null
    private var refreshJob: Job? = null

    init {
        scope.launch {
            prefsManager.serverConfigFlow.collect { config ->
                if (config.host.isNotBlank()) {
                    currentConfig = config
                    try {
                        val api = RetrofitClient.getApi(config)
                        repository = HomeAssistantRepository(api, config)
                        _repoReady.value = true
                        loadDeviceGroups()
                        // Start state subscriber
                        setupStateSubscriber(config)
                    } catch (e: Exception) {
                        _repoReady.value = false
                    }
                }
            }
        }
        scope.launch {
            homeButtons.collect { buttons ->
                if (buttons.isNotEmpty()) {
                    refreshDeviceStates(buttons)
                    // Update subscriber with current entity IDs
                    stateSubscriber?.subscribe(buttons.map { it.entityId }.toSet())
                    loadSelectOptions(buttons)
                } else {
                    refreshJob?.cancel()
                    stateSubscriber?.subscribe(emptySet())
                    _deviceStates.value = emptyMap()
                }
            }
        }
    }

    private fun setupStateSubscriber(config: ServerConfig) {
        stateSubscriber?.unsubscribe()
        stateSubscriber = HaStateSubscriber(
            config = config,
            scope = scope,
            onStateChanged = { entityId, haState ->
                val btnState = haStateToButtonState(haState)
                _deviceStates.value = _deviceStates.value + (entityId to btnState)
            }
        )
    }

    private fun loadSelectOptions(buttons: List<HomeButton>) {
        scope.launch {
            val selectButtons = buttons.filter { it.entityId.startsWith("select.") }
            val optionsMap = mutableMapOf<String, List<String>>()
            for (btn in selectButtons) {
                repository?.getEntityOptions(btn.entityId)
                    ?.onSuccess { opts -> optionsMap[btn.entityId] = opts }
            }
            _selectOptions.value = optionsMap
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

    // ─── Execute device command ─────────────────────────────────

    fun executeButton(entityId: String) {
        scope.launch {
            // Capture state BEFORE setting to LOADING (needed for select toggle logic)
            val previousState = _deviceStates.value[entityId] ?: ButtonState.IDLE
            _deviceStates.value = _deviceStates.value + (entityId to ButtonState.LOADING)

            if (entityId.startsWith("select.")) {
                // Select entity: smart toggle (off ↔ first non-off option)
                val currentState = previousState
                val options = _selectOptions.value[entityId] ?: emptyList()
                val offOption = options.firstOrNull { it == "关" || it == "off" || it == "关闭" }
                val onOptions = options.filter { it != offOption }

                val targetOption = if (currentState == ButtonState.SUCCESS) {
                    offOption ?: onOptions.firstOrNull() ?: return@launch
                } else {
                    onOptions.firstOrNull() ?: return@launch
                }

                repository?.let { repo ->
                    repo.selectOption(entityId, targetOption)
                        .onSuccess { delay(300); refreshSingleState(entityId) }
                        .onFailure { _deviceStates.value = _deviceStates.value + (entityId to ButtonState.FAILED) }
                }
            } else {
                repository?.let { repo ->
                    repo.toggleEntity(entityId)
                        .onSuccess { delay(300); refreshSingleState(entityId) }
                        .onFailure { _deviceStates.value = _deviceStates.value + (entityId to ButtonState.FAILED) }
                } ?: run { _deviceStates.value = _deviceStates.value + (entityId to ButtonState.FAILED) }
            }
        }
    }

    fun selectOption(entityId: String, option: String) {
        scope.launch {
            _deviceStates.value = _deviceStates.value + (entityId to ButtonState.LOADING)
            repository?.let { repo ->
                repo.selectOption(entityId, option)
                    .onSuccess { delay(300); refreshSingleState(entityId) }
                    .onFailure { _deviceStates.value = _deviceStates.value + (entityId to ButtonState.FAILED) }
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
                    .onFailure { _deviceStates.value = _deviceStates.value + (entityId to ButtonState.FAILED) }
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

    // ─── Device groups for dialog ───────────────────────────────

    fun loadDeviceGroups() {
        scope.launch {
            if (repository == null) {
                _isLoadingGroups.value = true
                withTimeoutOrNull(10_000) { _repoReady.first { it } }
                if (repository == null) { _isLoadingGroups.value = false; return@launch }
            }
            _isLoadingGroups.value = true
            repository!!.getControllableDevicesGrouped()
                .onSuccess { _deviceGroups.value = it }
                .onFailure { fallbackDomainGrouping() }
            _isLoadingGroups.value = false
        }
    }

    private suspend fun fallbackDomainGrouping() {
        repository?.getAllEntities()?.onSuccess { entities ->
            _deviceGroups.value = entities.filter { it.domain in Entity.CONTROLLABLE_DOMAINS }
                .groupBy { it.domain }.map { (d, list) -> DeviceGroup(name = d.uppercase(), entities = list) }
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
        "关" -> ButtonState.IDLE         // Chinese "off" for select entities
        "照明开" -> ButtonState.SUCCESS   // Chinese "light on" for 浴霸
        else -> if (haState != "off" && haState != "") ButtonState.SUCCESS else ButtonState.IDLE
    }

    override fun onCleared() {
        super.onCleared()
        stateSubscriber?.unsubscribe()
    }
}
