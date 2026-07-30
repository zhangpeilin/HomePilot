package com.homepilot.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.homepilot.app.model.DeviceGroup
import com.homepilot.app.model.Entity
import com.homepilot.app.network.RetrofitClient
import com.homepilot.app.repository.HomeAssistantRepository
import com.homepilot.app.util.PreferencesManager
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class DashboardViewModel(application: Application) : AndroidViewModel(application) {

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

    // Expanded/collapsed state: set of area names that are expanded
    private val _expandedControllable = MutableStateFlow<Set<String>>(emptySet())
    val expandedControllable: StateFlow<Set<String>> = _expandedControllable

    private val _expandedSensors = MutableStateFlow<Set<String>>(emptySet())
    val expandedSensors: StateFlow<Set<String>> = _expandedSensors

    private var repository: HomeAssistantRepository? = null

    init {
        viewModelScope.launch {
            prefsManager.serverConfigFlow.collect { config ->
                if (config.host.isNotBlank() && config.accessToken.isNotBlank()) {
                    try {
                        val api = RetrofitClient.getApi(config)
                        repository = HomeAssistantRepository(api, config)
                        _isConnected.value = true
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

    fun refreshAll() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            repository?.let { repo ->
                repo.getAllEntities()
                    .onSuccess { entities ->
                        val controllable = entities.filter {
                            it.domain in Entity.CONTROLLABLE_DOMAINS
                        }
                        val sensors = entities.filter {
                            it.domain !in Entity.CONTROLLABLE_DOMAINS
                                    && it.domain !in Entity.SCENE_DOMAINS
                                    && it.domain != "zone"
                        }

                        // Try area grouping
                        val ctrlGroups = repo.groupEntitiesByArea(controllable)
                        _controllableGroups.value = ctrlGroups.ifEmpty {
                            controllable.groupBy { it.domain }
                                .map { (d, list) -> DeviceGroup(name = d.uppercase(), entities = list) }
                        }

                        val sensorGroups = repo.groupEntitiesByArea(sensors)
                        _sensorGroups.value = sensorGroups.ifEmpty {
                            sensors.groupBy { it.domain }
                                .map { (d, list) -> DeviceGroup(name = d.uppercase(), entities = list) }
                        }

                        // Default: all collapsed
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
}
