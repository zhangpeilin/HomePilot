package com.homepilot.app.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.homepilot.app.model.DeviceGroup
import com.homepilot.app.model.Entity
import com.homepilot.app.network.HaStateSubscriber
import com.homepilot.app.network.RetrofitClient
import com.homepilot.app.repository.HomeAssistantRepository
import com.homepilot.app.util.PreferencesManager
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class DashboardViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "DASH_VM"
    }

    private val prefsManager = PreferencesManager(application)

    private val _controllableGroups = MutableStateFlow<List<DeviceGroup>>(emptyList())
    val controllableGroups: StateFlow<List<DeviceGroup>> = _controllableGroups

    private val _sensorGroups = MutableStateFlow<List<DeviceGroup>>(emptyList())
    val sensorGroups: StateFlow<List<DeviceGroup>> = _sensorGroups

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected

    private val _expandedControllable = MutableStateFlow<Set<String>>(emptySet())
    val expandedControllable: StateFlow<Set<String>> = _expandedControllable

    private val _expandedSensors = MutableStateFlow<Set<String>>(emptySet())
    val expandedSensors: StateFlow<Set<String>> = _expandedSensors

    // Entity state cache for real-time updates
    private val _entityCache = MutableStateFlow<Map<String, Entity>>(emptyMap())
    private var allControllable = listOf<Entity>()
    private var allSensors = listOf<Entity>()

    private var repository: HomeAssistantRepository? = null
    private var stateSubscriber: HaStateSubscriber? = null

    init {
        viewModelScope.launch {
            prefsManager.serverConfigFlow.collect { config ->
                if (config.host.isNotBlank() && config.accessToken.isNotBlank()) {
                    try {
                        val api = RetrofitClient.getApi(config)
                        repository = HomeAssistantRepository(api, config)
                        _isConnected.value = true
                        setupSubscriber(config)
                        refreshAll()
                    } catch (e: Exception) {
                        _isConnected.value = false
                        _error.value = "初始化连接失败: ${e.localizedMessage}"
                    }
                } else {
                    _isConnected.value = false
                }
            }
        }
    }

    private fun setupSubscriber(config: com.homepilot.app.model.ServerConfig) {
        stateSubscriber?.unsubscribe()
        stateSubscriber = HaStateSubscriber(
            config = config,
            scope = viewModelScope,
            onStateChanged = { entityId, newState ->
                Log.d(TAG, "Entity changed: $entityId → $newState")
                updateEntityState(entityId, newState)
            }
        )
    }

    private fun updateEntityState(entityId: String, newState: String) {
        val cache = _entityCache.value
        val entity = cache[entityId] ?: return
        val updated = entity.copy(state = newState)
        _entityCache.value = cache + (entityId to updated)

        // Update in place, preserving area grouping structure
        _controllableGroups.value = _controllableGroups.value.map { group ->
            group.copy(entities = group.entities.map {
                if (it.entityId == entityId) updated else it
            })
        }
        _sensorGroups.value = _sensorGroups.value.map { group ->
            group.copy(entities = group.entities.map {
                if (it.entityId == entityId) updated else it
            })
        }
    }

    fun refreshAll() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            repository?.let { repo ->
                repo.getAllEntities()
                    .onSuccess { entities ->
                        allControllable = entities.filter { it.domain in Entity.CONTROLLABLE_DOMAINS }
                        allSensors = entities.filter {
                            it.domain !in Entity.CONTROLLABLE_DOMAINS
                                    && it.domain !in Entity.SCENE_DOMAINS
                                    && it.domain != "zone"
                        }
                        _entityCache.value = entities.associateBy { it.entityId }
                        // Subscribe to all entities for real-time updates
                        stateSubscriber?.subscribe(entities.map { it.entityId }.toSet())

                        // Try area grouping
                        val ctrlGroups = repo.groupEntitiesByArea(allControllable)
                        _controllableGroups.value = ctrlGroups.ifEmpty {
                            allControllable.groupBy { it.domain }
                                .map { (d, list) -> DeviceGroup(name = d.uppercase(), entities = list) }
                        }

                        val sensorGroups = repo.groupEntitiesByArea(allSensors)
                        _sensorGroups.value = sensorGroups.ifEmpty {
                            allSensors.groupBy { it.domain }
                                .map { (d, list) -> DeviceGroup(name = d.uppercase(), entities = list) }
                        }

                        _expandedControllable.value = emptySet()
                        _expandedSensors.value = emptySet()
                    }
                    .onFailure { e ->
                        _error.value = e.message
                    }
            }
            _isLoading.value = false
        }
    }

    fun toggleControllableGroup(name: String) {
        val current = _expandedControllable.value.toMutableSet()
        if (current.contains(name)) current.remove(name) else current.add(name)
        _expandedControllable.value = current
    }

    fun toggleSensorGroup(name: String) {
        val current = _expandedSensors.value.toMutableSet()
        if (current.contains(name)) current.remove(name) else current.add(name)
        _expandedSensors.value = current
    }

    fun toggleEntity(entityId: String) {
        viewModelScope.launch {
            repository?.let { repo ->
                repo.toggleEntity(entityId)
                    .onSuccess { refreshAll() }
                    .onFailure { _error.value = it.message }
            }
        }
    }

    fun turnOn(entityId: String) {
        viewModelScope.launch {
            repository?.let { repo ->
                repo.turnOn(entityId)
                    .onSuccess { refreshAll() }
                    .onFailure { _error.value = it.message }
            }
        }
    }

    fun turnOff(entityId: String) {
        viewModelScope.launch {
            repository?.let { repo ->
                repo.turnOff(entityId)
                    .onSuccess { refreshAll() }
                    .onFailure { _error.value = it.message }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        stateSubscriber?.unsubscribe()
    }
}
