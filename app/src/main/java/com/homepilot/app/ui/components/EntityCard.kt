package com.homepilot.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.homepilot.app.model.Entity
import com.homepilot.app.ui.theme.DeviceOff
import com.homepilot.app.ui.theme.DeviceOn

@Composable
fun EntityCard(
    entity: Entity,
    onToggle: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val isOn = entity.isOn
    val surfaceColor by animateColorAsState(
        targetValue = if (isOn) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surface,
        label = "cardBg"
    )
    val borderColor by animateColorAsState(
        targetValue = if (isOn) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.outline,
        label = "cardBorder"
    )

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = surfaceColor),
        border = BorderStroke(1.dp, borderColor),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isOn) 4.dp else 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Device icon
            Box(
                modifier = Modifier
                    .size(48.dp),
                contentAlignment = Alignment.Center
            ) {
                FilledIconToggleButton(
                    checked = isOn,
                    onCheckedChange = { onToggle() },
                    modifier = Modifier.size(48.dp),
                    shape = CircleShape,
                    colors = IconButtonDefaults.filledIconToggleButtonColors(
                        checkedContainerColor = DeviceOn,
                        containerColor = DeviceOff.copy(alpha = 0.15f)
                    )
                ) {
                    Icon(
                        imageVector = getEntityIcon(entity.domain),
                        contentDescription = if (isOn) "设备开启" else "设备关闭",
                        tint = if (isOn) Color.White else DeviceOff
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Entity info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entity.friendlyName ?: entity.entityId,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${entity.domain}.${entity.entityId.split(".").lastOrNull() ?: ""}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                EntityStateBadge(state = entity.state)
            }
        }
    }
}

@Composable
private fun EntityStateBadge(state: String) {
    val (bgColor, textColor, label) = when (state) {
        "on" -> Triple(DeviceOn.copy(alpha = 0.15f), DeviceOn, "开启")
        "off" -> Triple(DeviceOff.copy(alpha = 0.15f), DeviceOff, "关闭")
        "unavailable" -> Triple(MaterialTheme.colorScheme.error.copy(alpha = 0.15f),
            MaterialTheme.colorScheme.error, "离线")
        else -> Triple(MaterialTheme.colorScheme.outline.copy(alpha = 0.15f),
            MaterialTheme.colorScheme.onSurfaceVariant, state)
    }

    Surface(
        shape = RoundedCornerShape(4.dp),
        color = bgColor
    ) {
        Text(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = textColor,
            fontWeight = FontWeight.Medium
        )
    }
}

fun getEntityIcon(domain: String): ImageVector {
    // Simple icon mapping - can be extended
    return when (domain) {
        "light" -> Icons.Default.Lightbulb
        "switch" -> Icons.Default.PowerSettingsNew
        else -> Icons.Default.PowerSettingsNew
    }
}
