package com.homepilot.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.homepilot.app.model.ServerConfig
import com.homepilot.app.network.RetrofitClient
import com.homepilot.app.repository.HomeAssistantRepository
import com.homepilot.app.util.PreferencesManager
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

sealed class ConnectionState {
    data object Idle : ConnectionState()
    data object Testing : ConnectionState()
    data class Success(val message: String) : ConnectionState()
    data class Error(val message: String) : ConnectionState()
}

class ServerConfigViewModel(application: Application) : AndroidViewModel(application) {

    private val prefsManager = PreferencesManager(application)

    val serverConfig: StateFlow<ServerConfig> = prefsManager.serverConfigFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ServerConfig())

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Idle)
    val connectionState: StateFlow<ConnectionState> = _connectionState

    fun testConnection(config: ServerConfig) {
        viewModelScope.launch {
            _connectionState.value = ConnectionState.Testing
            try {
                val api = RetrofitClient.getApi(config)
                val repo = HomeAssistantRepository(api)
                repo.testConnection()
                    .onSuccess { configResult ->
                        val version = configResult["version"] ?: "未知"
                        _connectionState.value = ConnectionState.Success(
                            "✅ 连接成功！HA 版本: $version"
                        )
                    }
                    .onFailure { e ->
                        _connectionState.value = ConnectionState.Error(
                            "❌ 连接失败: ${e.message}"
                        )
                    }
            } catch (e: Exception) {
                _connectionState.value = ConnectionState.Error(
                    "❌ 连接失败: ${e.localizedMessage ?: "未知错误"}"
                )
            }
        }
    }

    fun saveConfig(config: ServerConfig) {
        viewModelScope.launch {
            prefsManager.saveServerConfig(config)
        }
    }

    fun clearConfig() {
        viewModelScope.launch {
            prefsManager.clearServerConfig()
            RetrofitClient.reset()
        }
    }
}
