package com.homepilot.app.model

import com.google.gson.annotations.SerializedName

data class Entity(
    @SerializedName("entity_id")
    val entityId: String,
    val state: String,
    val attributes: Map<String, Any>? = null,
    @SerializedName("last_changed")
    val lastChanged: String? = null,
    @SerializedName("last_updated")
    val lastUpdated: String? = null,
    val context: ContextData? = null
) {
    val domain: String get() = entityId.split(".").firstOrNull() ?: ""
    val friendlyName: String?
        get() = attributes?.get("friendly_name") as? String

    val isOn: Boolean get() = state == "on"
    val isOff: Boolean get() = state == "off"

    companion object {
        val CONTROLLABLE_DOMAINS = listOf(
            "light", "switch", "fan", "cover", "lock",
            "climate", "media_player", "vacuum", "humidifier"
        )
        val SCENE_DOMAINS = listOf("scene", "automation", "script")
    }
}

data class ContextData(
    val id: String,
    @SerializedName("parent_id")
    val parentId: String? = null,
    @SerializedName("user_id")
    val userId: String? = null
)
