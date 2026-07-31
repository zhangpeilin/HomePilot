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

    suspend fun getEntityOptions(entityId: String): Result<List<String>> = runCatching {
        val result = getEntityState(entityId).getOrThrow()
        val raw = result.attributes?.get("options")
        if (raw is List<*>) raw.filterIsInstance<String>()
        else emptyList()
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
        if (controllable.isEmpty()) return@runCatching emptyList()
        groupEntitiesByArea(controllable).let { groups ->
            if (groups.isNotEmpty()) return@runCatching groups
        }
        // Fallback: domain-based grouping
        controllable.groupBy { it.domain }.map { (domain, entities) ->
            DeviceGroup(name = domain.uppercase(), entities = entities)
        }
    }

    /**
     * Group any list of entities by area using WebSocket area mapping.
     */
    suspend fun groupEntitiesByArea(entities: List<Entity>): List<DeviceGroup> {
        val areaMapping = fetchAreaMapping()
        if (areaMapping.isEmpty()) return emptyList()

        val areaMap = mutableMapOf<String, MutableList<Entity>>()
        val noAreaList = mutableListOf<Entity>()

        for (entity in entities) {
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
        return groups
    }


    /**
     * Fetch entity_id → area_name mapping via WebSocket.
     */
    suspend fun fetchAreaMapping(): Map<String, String> {
        val config = serverConfig ?: return emptyMap()
        return try {
            val wsClient = HaWebSocketClient(config)
            wsClient.fetchAreaMapping().getOrNull() ?: emptyMap()
        } catch (e: Exception) {
            Log.w(TAG, "fetchAreaMapping failed: ${e.message}")
            emptyMap()
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

    suspend fun selectOption(entityId: String, option: String): Result<List<Entity>> = runCatching {
        val response = api.callService("select", "select_option", mapOf("entity_id" to entityId, "option" to option))
        if (response.isSuccessful) response.body() ?: emptyList()
        else throw Exception("选择失败 [${response.code()}]")
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
