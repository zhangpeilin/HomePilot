package com.homepilot.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.homepilot.app.model.ButtonState
import com.homepilot.app.ui.components.AddDeviceDialog
import com.homepilot.app.ui.components.HomeButtonCard
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

    var showAddDialog by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf<String?>(null) }
    var showDeleteDialog by remember { mutableStateOf<String?>(null) }

    // Initial load and reload when returning to this screen
    LaunchedEffect(Unit) { viewModel.loadDeviceGroups() }

    // ── Rename Dialog ──
    val renameTarget = showRenameDialog
    if (renameTarget != null) {
        val button = homeButtons.find { it.entityId == renameTarget }
        var newName by remember(renameTarget) { mutableStateOf(button?.displayName ?: "") }
        AlertDialog(
            onDismissRequest = { showRenameDialog = null },
            title = { Text("编辑名称") },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("按钮名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done)
                )
            },
            confirmButton = {
                Button(onClick = { viewModel.renameButton(renameTarget, newName); showRenameDialog = null }) { Text("确认") }
            },
            dismissButton = { OutlinedButton(onClick = { showRenameDialog = null }) { Text("取消") } }
        )
    }

    // ── Delete Dialog ──
    val deleteTarget = showDeleteDialog
    if (deleteTarget != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = null },
            title = { Text("移除按钮") },
            text = { Text("确定从首页移除这个设备按钮？") },
            confirmButton = {
                Button(
                    onClick = { viewModel.removeButton(deleteTarget); showDeleteDialog = null },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("移除") }
            },
            dismissButton = { OutlinedButton(onClick = { showDeleteDialog = null }) { Text("取消") } }
        )
    }

    // ── Add Device Dialog ──
    if (showAddDialog) {
        // Refresh device groups every time dialog opens
        LaunchedEffect(showAddDialog) { viewModel.loadDeviceGroups() }
        AddDeviceDialog(
            deviceGroups = deviceGroups,
            isLoading = isLoadingGroups,
            onDismiss = { showAddDialog = false },
            onConfirm = { entityId, displayName, iconName ->
                viewModel.addButton(entityId, displayName, iconName)
                showAddDialog = false
            }
        )
    }

    // ── Main UI ──
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Home, contentDescription = null, modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("HomePilot")
                    }
                },
                actions = {
                    IconButton(onClick = { showAddDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = "添加设备")
                    }
                    IconButton(onClick = onNavigateToConfig) {
                        Icon(Icons.Default.Settings, contentDescription = "设置")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (homeButtons.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize().padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(
                            modifier = Modifier.padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(Icons.Default.Home, contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("首页暂无设备", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Medium)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("点击右上角 + 按钮添加设备快捷按钮",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center)
                        }
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(homeButtons, key = { it.entityId }) { button ->
                        val state = deviceStates[button.entityId] ?: ButtonState.IDLE
                        var showMenu by remember { mutableStateOf(false) }
                        Box {
                            HomeButtonCard(
                                button = button,
                                deviceState = state,
                                onClick = { viewModel.executeButton(button.entityId) },
                                onLongClick = { showMenu = true }
                            )
                            DropdownMenu(
                                expanded = showMenu,
                                onDismissRequest = { showMenu = false },
                                offset = DpOffset(8.dp, 0.dp)
                            ) {
                                DropdownMenuItem(
                                    text = { Text("编辑名称") },
                                    onClick = { showMenu = false; showRenameDialog = button.entityId },
                                    leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(20.dp)) }
                                )
                                DropdownMenuItem(
                                    text = { Text("移除", color = MaterialTheme.colorScheme.error) },
                                    onClick = { showMenu = false; showDeleteDialog = button.entityId },
                                    leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.error) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
