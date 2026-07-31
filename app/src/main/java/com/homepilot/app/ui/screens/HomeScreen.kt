package com.homepilot.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.homepilot.app.model.ButtonState
import com.homepilot.app.model.DeviceIcon
import com.homepilot.app.model.HomeButton
import com.homepilot.app.ui.components.AddDeviceDialog
import com.homepilot.app.ui.components.HomeButtonCard
import com.homepilot.app.util.DeviceIconMapper
import com.homepilot.app.viewmodel.HomeViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onNavigateToConfig: () -> Unit = {}
) {
    val homeButtons by viewModel.homeButtons.collectAsState()
    val deviceStates by viewModel.deviceStates.collectAsState()
    val deviceGroups by viewModel.deviceGroups.collectAsState()
    val isLoadingGroups by viewModel.isLoadingGroups.collectAsState()
    val homeButtonGroups by viewModel.homeButtonGroups.collectAsState()
    val expandedGroups by viewModel.expandedHomeGroups.collectAsState()

    var showAddDialog by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<String?>(null) }
    var deleteTarget by remember { mutableStateOf<String?>(null) }
    var iconPickTarget by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) { viewModel.loadDeviceGroups() }

    // Rename Dialog
    renameTarget?.let { eid ->
        val btn = homeButtons.find { it.entityId == eid }
        var name by remember(eid) { mutableStateOf(btn?.displayName ?: "") }
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("编辑名称") },
            text = { OutlinedTextField(name, { name = it }, label = { Text("按钮名称") }, singleLine = true,
                modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done)) },
            confirmButton = { Button(onClick = { viewModel.renameButton(eid, name); renameTarget = null }) { Text("确认") } },
            dismissButton = { OutlinedButton(onClick = { renameTarget = null }) { Text("取消") } }
        )
    }

    // Delete Dialog
    deleteTarget?.let { eid ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("移除按钮") },
            text = { Text("确定从首页移除此按钮？") },
            confirmButton = { Button(onClick = { viewModel.removeButton(eid); deleteTarget = null },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("移除") } },
            dismissButton = { OutlinedButton(onClick = { deleteTarget = null }) { Text("取消") } }
        )
    }

    // Icon Picker Dialog
    iconPickTarget?.let { eid ->
        val currentIcon = homeButtons.find { it.entityId == eid }?.iconName ?: "power"
        var selIcon by remember { mutableStateOf(currentIcon) }
        AlertDialog(
            onDismissRequest = { iconPickTarget = null },
            title = { Text("更换图标") },
            text = {
                LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    items(DeviceIcon.entries.chunked(4)) { row ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                            row.forEach { icon ->
                                val isSel = selIcon == icon.iconName
                                Surface(Modifier.size(56.dp).clip(CircleShape).clickable { selIcon = icon.iconName },
                                    shape = CircleShape,
                                    color = if (isSel) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                    border = if (isSel) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
                                ) { Box(contentAlignment = Alignment.Center) {
                                    Icon(DeviceIconMapper.toImageVector(icon), icon.label, Modifier.size(24.dp),
                                        tint = if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                                }}
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                }
            },
            confirmButton = { Button(onClick = { viewModel.updateButtonIcon(eid, selIcon); iconPickTarget = null }) { Text("确认") } },
            dismissButton = { OutlinedButton(onClick = { iconPickTarget = null }) { Text("取消") } }
        )
    }

    // Add Dialog
    if (showAddDialog) {
        LaunchedEffect(showAddDialog) { viewModel.loadDeviceGroups() }
        AddDeviceDialog(deviceGroups, isLoadingGroups,
            onDismiss = { showAddDialog = false },
            onConfirm = { eid, name, icon -> viewModel.addButton(eid, name, icon); showAddDialog = false })
    }

    ScreenContent(
        homeButtons = homeButtons,
        homeButtonGroups = homeButtonGroups,
        expandedGroups = expandedGroups,
        deviceStates = deviceStates,
        onToggleGroup = { viewModel.toggleHomeGroup(it) },
        onExecute = { viewModel.executeButton(it) },
        onRename = { renameTarget = it },
        onDelete = { deleteTarget = it },
        onIconPick = { iconPickTarget = it },
        onAddClick = { showAddDialog = true },
        onNavigateToConfig = onNavigateToConfig
    )
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun ScreenContent(
    homeButtons: List<HomeButton>,
    homeButtonGroups: Map<String, List<HomeButton>>,
    expandedGroups: Set<String>,
    deviceStates: Map<String, ButtonState>,
    onToggleGroup: (String) -> Unit,
    onExecute: (String) -> Unit,
    onRename: (String) -> Unit,
    onDelete: (String) -> Unit,
    onIconPick: (String) -> Unit,
    onAddClick: () -> Unit,
    onNavigateToConfig: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Home, null, Modifier.size(24.dp)); Spacer(Modifier.width(8.dp)); Text("HomePilot") }
                },
                actions = {
                    IconButton(onClick = onAddClick) { Icon(Icons.Default.Add, "添加") }
                    IconButton(onClick = onNavigateToConfig) { Icon(Icons.Default.Settings, "设置") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (homeButtons.isEmpty()) {
                EmptyState()
            } else {
                LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (homeButtonGroups.isEmpty()) {
                        // Flat list
                        items(homeButtons.chunked(2), key = { it[0].entityId }) { pair ->
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                pair.forEach { btn -> BtnWithMenu(btn, deviceStates, onExecute, onRename, onDelete, onIconPick, Modifier.weight(1f)) }
                                if (pair.size == 1) Spacer(Modifier.weight(1f))
                            }
                        }
                    } else {
                        // Area-grouped sections
                        homeButtonGroups.forEach { (areaName, buttons) ->
                            val expanded = areaName in expandedGroups
                            item(key = "h_$areaName") {
                                Surface(Modifier.fillMaxWidth().clickable { onToggleGroup(areaName) },
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    tonalElevation = 1.dp
                                ) {
                                    Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Folder, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                        Spacer(Modifier.width(10.dp))
                                        Column(Modifier.weight(1f)) {
                                            Text(areaName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                            Text("${buttons.size} 个设备", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                            if (expanded) {
                                items(buttons.chunked(2), key = { "r_${areaName}_${it[0].entityId}" }) { pair ->
                                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 8.dp)) {
                                        pair.forEach { btn -> BtnWithMenu(btn, deviceStates, onExecute, onRename, onDelete, onIconPick, Modifier.weight(1f)) }
                                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BtnWithMenu(
    button: HomeButton,
    deviceStates: Map<String, ButtonState>,
    onExecute: (String) -> Unit,
    onRename: (String) -> Unit,
    onDelete: (String) -> Unit,
    onIconPick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val state = deviceStates[button.entityId] ?: ButtonState.IDLE
    var showMenu by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        HomeButtonCard(
            button = button, deviceState = state,
            onClick = { onExecute(button.entityId) },
            onLongClick = { showMenu = true }
        )
        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }, offset = DpOffset(8.dp, 0.dp)) {
            DropdownMenuItem(text = { Text("编辑名称") }, onClick = { showMenu = false; onRename(button.entityId) },
                leadingIcon = { Icon(Icons.Default.Edit, null, Modifier.size(20.dp)) })
            DropdownMenuItem(text = { Text("更换图标") }, onClick = { showMenu = false; onIconPick(button.entityId) },
                leadingIcon = { Icon(Icons.Default.Image, null, Modifier.size(20.dp)) })
            DropdownMenuItem(text = { Text("移除", color = MaterialTheme.colorScheme.error) }, onClick = { showMenu = false; onDelete(button.entityId) },
                leadingIcon = { Icon(Icons.Default.Delete, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.error) })
        }
    }
}

@Composable
private fun EmptyState() {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(Modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.Home, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                Spacer(Modifier.height(16.dp))
                Text("首页暂无设备", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(8.dp))
                Text("点击右上角 + 按钮添加设备快捷按钮", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            }
        }
    }
}
