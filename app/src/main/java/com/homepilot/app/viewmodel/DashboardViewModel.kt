package com.homepilot.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.homepilot.app.model.Entity
import com.homepilot.app.network.RetrofitClient
import com.homepilot.app.repository.HomeAssistantRepository
import com.homepilot.app.util.PreferencesManager
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class DashboardViewModel(application: Application) : AndroidViewModel(application) {

    private val prefsManager = PreferencesManager(application)

    private val _entities = MutableStateFlow<List<Entity>>(emptyList())
    val entities: StateFlow<List<Entity>> = _entities

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected

    private var repository: HomeAssistantRepository? = null

    init {
        viewModelScope.launch {
            prefsManager.serverConfigFlow.collect { config ->
                if (config.host.isNotBlank() && config.accessToken.isNotBlank()) {
                    try {
                        val api = RetrofitClient.getApi(config)
                        repository = HomeAssistantRepository(api)
                        _isConnected.value = true
                        refreshEntities()
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

    fun refreshEntities() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            repository?.let { repo ->
                repo.getAllEntities()
                    .onSuccess { list ->
                        _entities.value = list
                    }
                    .onFailure { e ->
                        _error.value = e.message
                    }
            }
            _isLoading.value = false
        }
    }

    val controllableEntities: StateFlow<List<Entity>> = _entities.map { list ->
        list.filter { it.domain in Entity.CONTROLLABLE_DOMAINS }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val sensorEntities: StateFlow<List<Entity>> = _entities.map { list ->
        list.filter { it.domain !in Entity.CONTROLLABLE_DOMAINS && it.domain !in Entity.SCENE_DOMAINS }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun toggleEntity(entityId: String) {
        viewModelScope.launch {
            repository?.let { repo ->
                repo.toggleEntity(entityId)
                    .onSuccess { refreshEntities() }
                    .onFailure { _error.value = it.message }
            }
        }
    }

    fun turnOn(entityId: String) {
        viewModelScope.launch {
            repository?.let { repo ->
                repo.turnOn(entityId)
                    .onSuccess { refreshEntities() }
                    .onFailure { _error.value = it.message }
            }
        }
    }

    fun turnOff(entityId: String) {
        viewModelScope.launch {
            repository?.let { repo ->
                repo.turnOff(entityId)
                    .onSuccess { refreshEntities() }
                    .onFailure { _error.value = it.message }
            }
        }
    }
}
