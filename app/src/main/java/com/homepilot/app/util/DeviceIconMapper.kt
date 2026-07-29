package com.homepilot.app.util

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector
import com.homepilot.app.model.DeviceIcon

object DeviceIconMapper {

    fun toImageVector(icon: DeviceIcon): ImageVector = when (icon) {
        DeviceIcon.LIGHTBULB -> Icons.Default.Lightbulb
        DeviceIcon.POWER -> Icons.Default.PowerSettingsNew
        DeviceIcon.TV -> Icons.Default.Tv
        DeviceIcon.AC -> Icons.Default.AcUnit
        DeviceIcon.FAN -> Icons.Default.Air
        DeviceIcon.LOCK -> Icons.Default.Lock
        DeviceIcon.SPEAKER -> Icons.Default.Speaker
        DeviceIcon.MUSIC -> Icons.Default.MusicNote
        DeviceIcon.SUN -> Icons.Default.WbSunny
        DeviceIcon.FIRE -> Icons.Default.Whatshot
        DeviceIcon.KITCHEN -> Icons.Default.Kitchen
        DeviceIcon.LAUNDRY -> Icons.Default.LocalLaundryService
        DeviceIcon.DOOR -> Icons.Default.DoorFront
        DeviceIcon.WINDOW -> Icons.Default.BlindsClosed
        DeviceIcon.USB -> Icons.Default.Usb
        DeviceIcon.BATTERY -> Icons.Default.BatteryFull
    }
}
