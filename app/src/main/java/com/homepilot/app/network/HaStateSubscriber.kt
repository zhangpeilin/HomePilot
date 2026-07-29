package com.homepilot.app.network

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.homepilot.app.model.ServerConfig
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ClosedSendChannelException
import okhttp3.*
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class HaStateSubscriber(
    private val config: ServerConfig,
    private val scope: CoroutineScope,
    private val onStateChanged: (entityId: String, newState: String) -> Unit
) {
    companion object {
        private const val TAG = "HA_SUB"
        private const val MAX_RETRY_DELAY = 30_000L
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()
    private val running = AtomicBoolean(false)
    private var reconnectJob: Job? = null

    fun subscribe(entityIds: Set<String>) {
        if (entityIds.isEmpty()) { unsubscribe(); return }
        running.set(true)
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            var delayMs = 1_000L
            while (currentCoroutineContext().isActive && running.get()) {
                try {
                    connectAndListen()
                    delay(5_000)
                    delayMs = 1_000L
                } catch (e: Exception) {
                    Log.w(TAG, "Lost connection: ${e.message}, reconnect in ${delayMs}ms")
                    delay(delayMs)
                    delayMs = (delayMs * 2).coerceAtMost(MAX_RETRY_DELAY)
                }
            }
        }
    }

    fun unsubscribe() {
        running.set(false)
        reconnectJob?.cancel()
    }

    private suspend fun connectAndListen() {
        val wsUrl = config.baseUrl
            .replace("http://", "ws://")
            .replace("https://", "wss://")
            .replace("/api/", "/api/") + "websocket"

        val connected = CompletableDeferred<Unit>()
        val msgChannel = Channel<String>(Channel.UNLIMITED)
        var webSocket: WebSocket? = null

        webSocket = client.newWebSocket(
            Request.Builder().url(wsUrl).build(),
            object : WebSocketListener() {
                override fun onOpen(ws: WebSocket, response: Response) { connected.complete(Unit) }
                override fun onMessage(ws: WebSocket, text: String) { msgChannel.trySend(text) }
                override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                    if (!connected.isCompleted) connected.completeExceptionally(t)
                    else msgChannel.close(t)
                }
                override fun onClosed(ws: WebSocket, code: Int, reason: String) { msgChannel.close() }
            }
        )

        withTimeout(10_000) { connected.await() }
        withTimeout(5_000) { msgChannel.receive() } // auth_required
        webSocket!!.send(gson.toJson(mapOf("type" to "auth", "access_token" to config.accessToken)))

        while (true) {
            val msg = withTimeout(5_000) { msgChannel.receive() }
            val json = JsonParser.parseString(msg).asJsonObject
            when (json.get("type")?.asString) {
                "auth_ok" -> break
                "auth_invalid" -> throw Exception("auth_invalid")
            }
        }

        webSocket!!.send(gson.toJson(mapOf(
            "id" to 1, "type" to "subscribe_events", "event_type" to "state_changed"
        )))
        withTimeout(5_000) { msgChannel.receive() }
        Log.d(TAG, "Listening for state_changed events...")

        // Use outer coroutine context for isActive
        while (currentCoroutineContext().isActive && running.get()) {
            val msg = try {
                withTimeout(Long.MAX_VALUE) { msgChannel.receive() }
            } catch (e: ClosedSendChannelException) {
                throw Exception("Channel closed")
            }
            val json = JsonParser.parseString(msg).asJsonObject
            if (json.get("type")?.asString != "event") continue

            val event = json.getAsJsonObject("event") ?: continue
            if (event.get("event_type")?.asString != "state_changed") continue

            val data = event.getAsJsonObject("data") ?: continue
            val entityId = data.get("entity_id")?.asString ?: continue

            val newState = data.getAsJsonObject("new_state")
            val state = newState?.get("state")?.asString ?: continue
            Log.d(TAG, "Event: $entityId → $state")
            onStateChanged(entityId, state)
        }
    }
}
