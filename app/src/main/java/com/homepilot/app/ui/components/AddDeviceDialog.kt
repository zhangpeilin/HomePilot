package com.homepilot.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.homepilot.app.model.DeviceGroup
import com.homepilot.app.model.DeviceIcon
import com.homepilot.app.model.Entity
import com.homepilot.app.util.DeviceIconMapper

enum class AddDialogStep { SELECT_GROUP, SELECT_DEVICE, CONFIGURE }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddDeviceDialog(
    deviceGroups: List<DeviceGroup>,
    isLoading: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (entityId: String, displayName: String, iconName: String) -> Unit
) {
    var step by remember { mutableStateOf(AddDialogStep.SELECT_GROUP) }
    var selectedGroup by remember { mutableStateOf<DeviceGroup?>(null) }
    var selectedEntity by remember { mutableStateOf<Entity?>(null) }
    var displayName by remember { mutableStateOf("") }
    var selectedIconName by remember { mutableStateOf("power") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = when (step) {
                    AddDialogStep.SELECT_GROUP -> "选择区域"
                    AddDialogStep.SELECT_DEVICE -> "选择设备"
                    AddDialogStep.CONFIGURE -> "设置按钮"
                }
            )
        },
        text = {
            when (step) {
                AddDialogStep.SELECT_GROUP -> GroupSelectionStep(
                    groups = deviceGroups,
                    isLoading = isLoading,
                    onGroupClick = { group ->
                        selectedGroup = group
                        step = AddDialogStep.SELECT_DEVICE
                    }
                )

                AddDialogStep.SELECT_DEVICE -> DeviceSelectionStep(
                    group = selectedGroup,
                    onDeviceClick = { entity ->
                        selectedEntity = entity
                        displayName = entity.friendlyName ?: entity.entityId
                        step = AddDialogStep.CONFIGURE
                    }
                )

                AddDialogStep.CONFIGURE -> ConfigureStep(
                    entity = selectedEntity,
                    displayName = displayName,
                    selectedIconName = selectedIconName,
                    onNameChange = { displayName = it },
                    onIconChange = { selectedIconName = it }
                )
            }
        },
        confirmButton = {
            when (step) {
                AddDialogStep.SELECT_GROUP -> {
                    TextButton(onClick = onDismiss) { Text("取消") }
                }
                AddDialogStep.SELECT_DEVICE -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { step = AddDialogStep.SELECT_GROUP }) {
                            Text("返回区域")
                        }
                    }
                }
                AddDialogStep.CONFIGURE -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { step = AddDialogStep.SELECT_DEVICE }) {
                            Text("返回")
                        }
                        Button(
                            onClick = {
                                selectedEntity?.let { entity ->
                                    onConfirm(entity.entityId, displayName, selectedIconName)
                                }
                            }
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("添加")
                        }
                    }
                }
            }
        },
        dismissButton = null
    )
}

// ── Step 1: Area / Group selection ──────────────────────────────

@Composable
private fun GroupSelectionStep(
    groups: List<DeviceGroup>,
    isLoading: Boolean,
    onGroupClick: (DeviceGroup) -> Unit
) {
    if (isLoading) {
        Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(8.dp))
                Text("加载设备列表...")
            }
        }
    } else if (groups.isEmpty()) {
        Box(modifier = Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
            Text("没有可添加的设备", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    } else {
        LazyColumn(
            modifier = Modifier.heightIn(max = 400.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(groups, key = { it.name }) { group ->
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onGroupClick(group) },
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Folder,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = group.name,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "${group.entities.size} 个设备",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = "选择 →",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

// ── Step 2: Device selection ────────────────────────────────────

@Composable
private fun DeviceSelectionStep(
    group: DeviceGroup?,
    onDeviceClick: (Entity) -> Unit
) {
    if (group == null) {
        Box(modifier = Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
            Text("请先选择区域", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    } else {
        Column {
            Text(
                text = "区域：${group.name}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            LazyColumn(
                modifier = Modifier.heightIn(max = 350.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(group.entities, key = { it.entityId }) { entity ->
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onDeviceClick(entity) },
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surface
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = entity.friendlyName ?: entity.entityId,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = entity.entityId,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Text(
                                text = entity.state,
                                style = MaterialTheme.typography.labelMedium,
                                color = if (entity.isOn) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

// ── Step 3: Configure name + icon ──────────────────────────────

@Composable
private fun ConfigureStep(
    entity: Entity?,
    displayName: String,
    selectedIconName: String,
    onNameChange: (String) -> Unit,
    onIconChange: (String) -> Unit
) {
    if (entity == null) return

    Column {
        // Name input
        OutlinedTextField(
            value = displayName,
            onValueChange = onNameChange,
            label = { Text("按钮名称") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done)
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "设备ID: ${entity.entityId}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "选择图标",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            modifier = Modifier.heightIn(max = 260.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(DeviceIcon.entries) { icon ->
                val isSelected = selectedIconName == icon.iconName
                Surface(
                    modifier = Modifier
                        .size(58.dp)
                        .clip(CircleShape)
                        .clickable { onIconChange(icon.iconName) },
                    shape = CircleShape,
                    color = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceVariant,
                    border = if (isSelected)
                        BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
                    else null
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = DeviceIconMapper.toImageVector(icon),
                            contentDescription = icon.label,
                            modifier = Modifier.size(24.dp),
                            tint = if (isSelected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
