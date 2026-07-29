package com.homepilot.app.network

import com.google.gson.Gson
import com.google.gson.JsonParser
import com.homepilot.app.model.ServerConfig
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import okhttp3.*
import java.util.concurrent.TimeUnit

/**
 * HA WebSocket client.
 * Fetches device registry + entity registry to build entity_id → area_name mapping.
 * This is needed because in HA, areas are assigned to devices, not individual entities.
 */
class HaWebSocketClient(private val config: ServerConfig) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()

    suspend fun fetchAreaMapping(): Result<Map<String, String>> = withContext(Dispatchers.IO) {
        runCatching {
            val wsUrl = config.baseUrl
                .replace("http://", "ws://")
                .replace("https://", "wss://")
                .replace("/api/", "/api/") + "websocket"

            val msgChannel = Channel<String>(Channel.UNLIMITED)
            val request = Request.Builder().url(wsUrl).build()
            val connected = CompletableDeferred<Unit>()

            val webSocket = client.newWebSocket(request, object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) { connected.complete(Unit) }
                override fun onMessage(webSocket: WebSocket, text: String) { msgChannel.trySend(text) }
                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    if (!connected.isCompleted) connected.completeExceptionally(t)
                    else msgChannel.close(t)
                }
                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) { msgChannel.close() }
            })

            connected.await()
            withTimeout(5000) { msgChannel.receive() } // auth_required
            webSocket.send(gson.toJson(mapOf("type" to "auth", "access_token" to config.accessToken)))

            // Wait for auth_ok
            while (true) {
                val msg = withTimeout(5000) { msgChannel.receive() }
                val json = JsonParser.parseString(msg).asJsonObject
                when (json.get("type")?.asString) {
                    "auth_ok" -> break
                    "auth_invalid" -> throw Exception("WebSocket 认证失败")
                }
            }

            // Step 1: Fetch device registry → device_id → area_id mapping
            webSocket.send(gson.toJson(mapOf("id" to 1, "type" to "config/device_registry/list")))
            val deviceResult = withTimeout(10000) { msgChannel.receive() }
            val deviceJson = JsonParser.parseString(deviceResult).asJsonObject

            val deviceToArea = mutableMapOf<String, String>()   // deviceId → areaId
            val areaNameMap = mutableMapOf<String, String>()     // areaId → areaName

            if (deviceJson.get("success")?.asBoolean == true) {
                deviceJson.getAsJsonArray("result")?.forEach { elem ->
                    val obj = elem.asJsonObject
                    val deviceId = obj.get("id")?.asString ?: return@forEach
                    val areaId = obj.get("area_id")?.asString
                    if (areaId != null && areaId.isNotBlank()) {
                        deviceToArea[deviceId] = areaId
                    }
                }
            }

            // Step 2: Fetch area registry → area_id → area_name
            webSocket.send(gson.toJson(mapOf("id" to 2, "type" to "config/area_registry/list")))
            val areaResult = withTimeout(10000) { msgChannel.receive() }
            val areaJson = JsonParser.parseString(areaResult).asJsonObject
            if (areaJson.get("success")?.asBoolean == true) {
                areaJson.getAsJsonArray("result")?.forEach { elem ->
                    val obj = elem.asJsonObject
                    val areaId = obj.get("area_id")?.asString ?: return@forEach
                    val name = obj.get("name")?.asString ?: areaId
                    areaNameMap[areaId] = name
                }
            }

            // Step 3: Fetch entity registry → entity_id → device_id
            val resultMap = mutableMapOf<String, String>()
            if (deviceToArea.isNotEmpty()) {
                webSocket.send(gson.toJson(mapOf("id" to 3, "type" to "config/entity_registry/list")))
                val entityResult = withTimeout(10000) { msgChannel.receive() }
                val entityJson = JsonParser.parseString(entityResult).asJsonObject
                if (entityJson.get("success")?.asBoolean == true) {
                    entityJson.getAsJsonArray("result")?.forEach { elem ->
                        val obj = elem.asJsonObject
                        val entityId = obj.get("entity_id")?.asString ?: return@forEach
                        val deviceId = obj.get("device_id")?.asString
                        if (deviceId != null) {
                            val areaId = deviceToArea[deviceId]
                            if (areaId != null) {
                                val areaName = areaNameMap[areaId] ?: areaId
                                resultMap[entityId] = areaName
                            }
                        }
                    }
                }
            }

            webSocket.close(1000, "OK")
            msgChannel.close()
            resultMap.toMap()
        }
    }
}
