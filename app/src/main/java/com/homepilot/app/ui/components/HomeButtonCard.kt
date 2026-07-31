package com.homepilot.app.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.homepilot.app.model.ButtonState
import com.homepilot.app.model.HomeButton
import com.homepilot.app.util.DeviceIconMapper

private val AmberYellow = Color(0xFFFFC107)
private val AmberBg = Color(0xFFFFF3CD)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeButtonCard(
    button: HomeButton,
    deviceState: ButtonState,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val isOn = deviceState == ButtonState.SUCCESS
    val isLoading = deviceState == ButtonState.LOADING
    val isFailed = deviceState == ButtonState.FAILED
    val isLight = button.entityId.startsWith("light.")

    val alpha = when (deviceState) {
        ButtonState.SUCCESS -> 1f
        ButtonState.LOADING -> 1f
        ButtonState.IDLE -> 0.60f
        ButtonState.FAILED -> 0.40f
    }

    val iconBg = when {
        isLoading -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.08f)
        isOn && isLight -> AmberBg
        isOn -> MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
        isFailed -> MaterialTheme.colorScheme.error.copy(alpha = 0.12f)
        else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.08f)
    }
    val iconTint = when {
        isLoading -> MaterialTheme.colorScheme.onSurfaceVariant
        isOn && isLight -> AmberYellow
        isOn -> MaterialTheme.colorScheme.primary
        isFailed -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .alpha(alpha)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier.size(56.dp).clip(CircleShape).background(iconBg),
                contentAlignment = Alignment.Center
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(28.dp), strokeWidth = 3.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                } else {
                    Icon(
                        imageVector = DeviceIconMapper.toImageVector(button.icon),
                        contentDescription = button.displayName,
                        modifier = Modifier.size(28.dp),
                        tint = iconTint
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(button.displayName, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium,
                maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
            Spacer(Modifier.height(2.dp))
            Text(
                text = when { isLoading -> "执行中..."; isOn -> "已开启"; isFailed -> "设备离线"; else -> "已关闭" },
                style = MaterialTheme.typography.labelSmall,
                color = when { isLoading -> MaterialTheme.colorScheme.tertiary; isOn && isLight -> AmberYellow
                    isOn -> MaterialTheme.colorScheme.primary; isFailed -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.onSurfaceVariant },
                maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center
            )
        }
    }
}
