package com.homepilot.app.network

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.homepilot.app.model.ServerConfig
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import okhttp3.*
import java.util.concurrent.TimeUnit

class HaWebSocketClient(private val config: ServerConfig) {

    companion object {
        private const val TAG = "HA_WS"
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()

    /** Safe string extraction that handles JsonNull */
    private fun JsonObject.safeString(key: String): String? {
        val el = get(key)
        return if (el != null && !el.isJsonNull) el.asString else null
    }

    suspend fun fetchAreaMapping(): Result<Map<String, String>> = withContext(Dispatchers.IO) {
        runCatching {
            val wsUrl = config.baseUrl
                .replace("http://", "ws://")
                .replace("https://", "wss://")
                .replace("/api/", "/api/") + "websocket"

            Log.d(TAG, "Connecting to $wsUrl")
            val msgChannel = Channel<String>(Channel.UNLIMITED)
            val request = Request.Builder().url(wsUrl).build()
            val connected = CompletableDeferred<Unit>()

            val webSocket = client.newWebSocket(request, object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    Log.d(TAG, "WebSocket opened, HTTP ${response.code}")
                    connected.complete(Unit)
                }
                override fun onMessage(webSocket: WebSocket, text: String) { msgChannel.trySend(text) }
                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    Log.e(TAG, "WS failure: ${t.message}", t)
                    if (!connected.isCompleted) connected.completeExceptionally(t)
                    else msgChannel.close(t)
                }
                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    Log.d(TAG, "WS closed: $code $reason")
                    msgChannel.close()
                }
            })

            connected.await()
            Log.d(TAG, "Connected, awaiting auth_required")
            withTimeout(5000) { msgChannel.receive() }
            webSocket.send(gson.toJson(mapOf("type" to "auth", "access_token" to config.accessToken)))

            var authed = false
            while (true) {
                val msg = withTimeout(5000) { msgChannel.receive() }
                val json = JsonParser.parseString(msg).asJsonObject
                when (json.safeString("type")) {
                    "auth_ok" -> { authed = true; Log.d(TAG, "Auth OK"); break }
                    "auth_invalid" -> throw Exception("auth_invalid")
                }
            }

            // ── Step 1: Device registry ──
            Log.d(TAG, "Req device_registry/list")
            webSocket.send(gson.toJson(mapOf("id" to 1, "type" to "config/device_registry/list")))
            val deviceResp = withTimeout(10000) { msgChannel.receive() }
            val deviceRoot = JsonParser.parseString(deviceResp).asJsonObject

            val deviceToArea = mutableMapOf<String, String>()
            if (deviceRoot.safeString("success") == "true" || deviceRoot.get("success")?.asBoolean == true) {
                deviceRoot.getAsJsonArray("result")?.forEach { elem ->
                    val obj = elem.asJsonObject
                    val did = obj.safeString("id") ?: return@forEach
                    val aid = obj.safeString("area_id")
                    if (aid != null) deviceToArea[did] = aid
                }
            }
            Log.d(TAG, "Devices with area_id: ${deviceToArea.size}")

            // ── Step 2: Area registry ──
            Log.d(TAG, "Req area_registry/list")
            webSocket.send(gson.toJson(mapOf("id" to 2, "type" to "config/area_registry/list")))
            val areaResp = withTimeout(10000) { msgChannel.receive() }
            val areaRoot = JsonParser.parseString(areaResp).asJsonObject

            val areaNameMap = mutableMapOf<String, String>()
            if (areaRoot.safeString("success") == "true" || areaRoot.get("success")?.asBoolean == true) {
                areaRoot.getAsJsonArray("result")?.forEach { elem ->
                    val obj = elem.asJsonObject
                    val aid = obj.safeString("area_id") ?: return@forEach
                    areaNameMap[aid] = obj.safeString("name") ?: aid
                }
            }
            Log.d(TAG, "Areas: ${areaNameMap.size} -> ${areaNameMap.values}")

            // ── Step 3: Entity registry cross-reference ──
            if (deviceToArea.isEmpty()) {
                Log.w(TAG, "No devices with area_id found, skipping entity cross-ref")
                webSocket.close(1000, "OK")
                msgChannel.close()
                return@runCatching emptyMap<String, String>()
            }

            Log.d(TAG, "Req entity_registry/list for cross-ref")
            webSocket.send(gson.toJson(mapOf("id" to 3, "type" to "config/entity_registry/list")))
            val entityResp = withTimeout(10000) { msgChannel.receive() }
            val entityRoot = JsonParser.parseString(entityResp).asJsonObject

            val resultMap = mutableMapOf<String, String>()
            var count = 0
            if (entityRoot.safeString("success") == "true" || entityRoot.get("success")?.asBoolean == true) {
                entityRoot.getAsJsonArray("result")?.forEach { elem ->
                    val obj = elem.asJsonObject
                    val eid = obj.safeString("entity_id") ?: return@forEach
                    val did = obj.safeString("device_id")
                    if (did != null) {
                        val aid = deviceToArea[did]
                        if (aid != null) {
                            resultMap[eid] = areaNameMap[aid] ?: aid
                            count++
                        }
                    }
                }
            }
            Log.d(TAG, "Cross-ref result: $count entities mapped to areas")
            Log.d(TAG, "Sample: ${resultMap.entries.take(5)}")

            webSocket.close(1000, "OK")
            msgChannel.close()
            resultMap
        }.onFailure { e ->
            Log.e(TAG, "fetchAreaMapping failed: ${e.message}")
        }
    }
}
