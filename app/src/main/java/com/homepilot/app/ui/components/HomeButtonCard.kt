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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.homepilot.app.model.ButtonState
import com.homepilot.app.model.HomeButton
import com.homepilot.app.util.DeviceIconMapper

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
    val alpha = when (deviceState) {
        ButtonState.SUCCESS -> 1f
        ButtonState.LOADING -> 1f
        ButtonState.IDLE -> 0.55f
        ButtonState.FAILED -> 0.40f
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .alpha(alpha)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = when {
                isOn -> MaterialTheme.colorScheme.primaryContainer
                isFailed -> MaterialTheme.colorScheme.errorContainer
                else -> MaterialTheme.colorScheme.surfaceVariant
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isOn) 4.dp else 1.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Icon / Loading circle
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(
                        when {
                            isOn -> MaterialTheme.colorScheme.primary.copy(alpha = 0.20f)
                            isFailed -> MaterialTheme.colorScheme.error.copy(alpha = 0.15f)
                            else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.08f)
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(28.dp),
                        strokeWidth = 3.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                } else {
                    Icon(
                        imageVector = DeviceIconMapper.toImageVector(button.icon),
                        contentDescription = button.displayName,
                        modifier = Modifier.size(28.dp),
                        tint = when {
                            isOn -> MaterialTheme.colorScheme.primary
                            isFailed -> MaterialTheme.colorScheme.error
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = button.displayName,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = when {
                    isLoading -> "执行中..."
                    isOn -> "已开启"
                    isFailed -> "设备离线"
                    else -> "已关闭"
                },
                style = MaterialTheme.typography.labelSmall,
                color = when {
                    isLoading -> MaterialTheme.colorScheme.tertiary
                    isOn -> MaterialTheme.colorScheme.primary
                    isFailed -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        }
    }
}
