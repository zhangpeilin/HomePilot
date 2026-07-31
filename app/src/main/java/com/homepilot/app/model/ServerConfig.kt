package com.homepilot.app.model

data class ServerConfig(
    val host: String = "",
    val port: Int = 8123,
    val accessToken: String = "",
    val useTls: Boolean = false
) {
    val baseUrl: String
        get() = "${if (useTls) "https" else "http"}://$host:$port/api/"
}
