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

class ScenesViewModel(application: Application) : AndroidViewModel(application) {

    private val prefsManager = PreferencesManager(application)

    private val _scenes = MutableStateFlow<List<Entity>>(emptyList())
    val scenes: StateFlow<List<Entity>> = _scenes

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val _triggerResult = MutableStateFlow<String?>(null)
    val triggerResult: StateFlow<String?> = _triggerResult

    private var repository: HomeAssistantRepository? = null

    init {
        viewModelScope.launch {
            prefsManager.serverConfigFlow.collect { config ->
                if (config.host.isNotBlank()) {
                    try {
                        val api = RetrofitClient.getApi(config)
                        repository = HomeAssistantRepository(api)
                        loadScenes()
                    } catch (e: Exception) {
                        _error.value = "初始化失败: ${e.localizedMessage}"
                    }
                }
            }
        }
    }

    fun loadScenes() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            repository?.let { repo ->
                repo.getAllEntities()
                    .onSuccess { list ->
                        _scenes.value = list.filter { e ->
                            e.entityId.startsWith("scene.")
                        }
                    }
                    .onFailure { e ->
                        _error.value = e.message
                    }
            }
            _isLoading.value = false
        }
    }

    fun triggerScene(sceneEntityId: String) {
        viewModelScope.launch {
            repository?.let { repo ->
                repo.triggerScene(sceneEntityId)
                    .onSuccess {
                        _triggerResult.value = "场景已触发"
                    }
                    .onFailure { e ->
                        _error.value = "触发失败: ${e.message}"
                    }
            }
        }
    }

    fun clearTriggerResult() {
        _triggerResult.value = null
    }
}
