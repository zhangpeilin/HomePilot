package com.homepilot.app.model

enum class ButtonState {
    IDLE,       // device is OFF, dim
    LOADING,    // command in progress, show spinner
    SUCCESS,    // device is ON, bright
    FAILED      // device unavailable
}

enum class DeviceIcon(val iconName: String, val label: String) {
    LIGHTBULB("lightbulb", "灯泡"),
    POWER("power", "电源开关"),
    TV("tv", "电视"),
    AC("ac", "空调"),
    FAN("fan", "风扇"),
    LOCK("lock", "门锁"),
    SPEAKER("speaker", "音响"),
    MUSIC("music", "音乐"),
    SUN("sun", "阳光"),
    FIRE("fire", "壁炉"),
    KITCHEN("kitchen", "厨房"),
    LAUNDRY("laundry", "洗衣"),
    DOOR("door", "门窗"),
    WINDOW("window", "窗帘"),
    USB("usb", "USB"),
    BATTERY("battery", "电池");

    companion object {
        fun fromName(name: String): DeviceIcon =
            entries.find { it.iconName == name } ?: POWER
    }
}

// Persisted config - no state field
data class HomeButton(
    val entityId: String,
    val displayName: String,
    val iconName: String = "power"
) {
    val icon: DeviceIcon get() = DeviceIcon.fromName(iconName)
}
