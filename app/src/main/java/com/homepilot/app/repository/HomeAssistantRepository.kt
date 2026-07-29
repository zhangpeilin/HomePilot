package com.homepilot.app.repository

import android.util.Log
import com.homepilot.app.model.Entity
import com.homepilot.app.model.DeviceGroup
import com.homepilot.app.model.ServerConfig
import com.homepilot.app.model.ServiceCallRequest
import com.homepilot.app.network.HomeAssistantApi
import com.homepilot.app.network.HaWebSocketClient

class HomeAssistantRepository(
    private val api: HomeAssistantApi,
    private val serverConfig: ServerConfig? = null
) {
    companion object {
        private const val TAG = "HA_REPO"
    }

    suspend fun getAllEntities(): Result<List<Entity>> = runCatching {
        val response = api.getAllStates()
        if (response.isSuccessful) {
            response.body() ?: emptyList()
        } else {
            throw Exception("获取设备列表失败 [${response.code()}] ${response.message()}")
        }
    }

    suspend fun getEntityState(entityId: String): Result<Entity> = runCatching {
        val response = api.getEntityState(entityId)
        if (response.isSuccessful) {
            response.body() ?: throw Exception("设备状态为空")
        } else {
            throw Exception("获取设备状态失败 [${response.code()}] ${response.message()}")
        }
    }

    suspend fun getControllableDevicesGrouped(): Result<List<DeviceGroup>> = runCatching {
        val allEntities = getAllEntities().getOrThrow()
        val controllable = allEntities.filter { it.domain in Entity.CONTROLLABLE_DOMAINS }
        Log.d(TAG, "getControllableDevicesGrouped: ${controllable.size} controllable, serverConfig=${serverConfig != null}")
        if (controllable.isEmpty()) return@runCatching emptyList()

        // Try WebSocket area mapping
        if (serverConfig != null) {
            Log.d(TAG, "Attempting WebSocket area mapping...")
            try {
                val wsClient = HaWebSocketClient(serverConfig)
                val areaMapping = wsClient.fetchAreaMapping().getOrNull()

                if (areaMapping != null && areaMapping.isNotEmpty()) {
                    Log.d(TAG, "WebSocket returned ${areaMapping.size} area mappings")
                    val areaMap = mutableMapOf<String, MutableList<Entity>>()
                    val noAreaList = mutableListOf<Entity>()

                    for (entity in controllable) {
                        val areaName = areaMapping[entity.entityId]
                        if (areaName != null) {
                            areaMap.getOrPut(areaName) { mutableListOf() }.add(entity)
                        } else {
                            noAreaList.add(entity)
                        }
                    }

                    val groups = areaMap.map { (areaName, entities) ->
                        DeviceGroup(name = areaName, entities = entities)
                    }.toMutableList()

                    if (noAreaList.isNotEmpty()) {
                        val ungroupedByDomain = noAreaList.groupBy { it.domain }
                            .map { (domain, entities) ->
                                DeviceGroup(name = "未归类 · $domain", entities = entities)
                            }
                        groups.addAll(ungroupedByDomain)
                    }
                    return@runCatching groups
                } else {
                    Log.w(TAG, "WebSocket returned empty mapping")
                }
            } catch (e: Exception) {
                Log.w(TAG, "WebSocket area mapping failed: ${e.message}")
            }
        }

        // Fallback: domain-based grouping
        Log.d(TAG, "Falling back to domain grouping")
        controllable.groupBy { it.domain }.map { (domain, entities) ->
            DeviceGroup(name = domain.uppercase(), entities = entities)
        }
    }

    suspend fun getStatesForButtons(entityIds: List<String>): Result<List<Entity>> = runCatching {
        val allResult = getAllEntities()
        val allEntities = allResult.getOrThrow()
        allEntities.filter { it.entityId in entityIds }
    }

    suspend fun toggleEntity(entityId: String): Result<List<Entity>> = runCatching {
        val domain = entityId.split(".").firstOrNull()
            ?: throw Exception("无效的实体ID: $entityId")
        val response = api.callService(domain, "toggle", mapOf("entity_id" to entityId))
        if (response.isSuccessful) response.body() ?: emptyList()
        else throw Exception("切换设备失败 [${response.code()}] ${response.message()}")
    }

    suspend fun turnOn(entityId: String): Result<List<Entity>> = runCatching {
        val domain = entityId.split(".").firstOrNull()
            ?: throw Exception("无效的实体ID: $entityId")
        val response = api.callService(domain, "turn_on", mapOf("entity_id" to entityId))
        if (response.isSuccessful) response.body() ?: emptyList()
        else throw Exception("开启设备失败 [${response.code()}] ${response.message()}")
    }

    suspend fun turnOff(entityId: String): Result<List<Entity>> = runCatching {
        val domain = entityId.split(".").firstOrNull()
            ?: throw Exception("无效的实体ID: $entityId")
        val response = api.callService(domain, "turn_off", mapOf("entity_id" to entityId))
        if (response.isSuccessful) response.body() ?: emptyList()
        else throw Exception("关闭设备失败 [${response.code()}] ${response.message()}")
    }

    suspend fun callService(request: ServiceCallRequest): Result<List<Entity>> = runCatching {
        val response = api.callService(request.domain, request.service, request.toMap())
        if (response.isSuccessful) response.body() ?: emptyList()
        else throw Exception("调用服务失败 [${response.code()}] ${response.message()}")
    }

    suspend fun triggerScene(sceneEntityId: String): Result<List<Entity>> = runCatching {
        val response = api.callService("scene", "turn_on", mapOf("entity_id" to sceneEntityId))
        if (response.isSuccessful) response.body() ?: emptyList()
        else throw Exception("触发场景失败 [${response.code()}] ${response.message()}")
    }

    suspend fun testConnection(): Result<Map<String, Any>> = runCatching {
        val response = api.getConfig()
        if (response.isSuccessful) {
            response.body() ?: throw Exception("配置信息为空")
        } else {
            throw Exception("连接失败 [${response.code()}] ${response.message()}")
        }
    }
}
