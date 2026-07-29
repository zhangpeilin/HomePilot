package com.homepilot.app.model

data class ServiceCallRequest(
    val domain: String,
    val service: String,
    val entityId: String,
    val serviceData: Map<String, Any>? = null
) {
    fun toMap(): Map<String, Any> {
        val map = mutableMapOf<String, Any>("entity_id" to entityId)
        serviceData?.let { map.putAll(it) }
        return map
    }
}
