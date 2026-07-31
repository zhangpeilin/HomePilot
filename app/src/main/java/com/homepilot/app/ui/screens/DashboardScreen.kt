package com.homepilot.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.homepilot.app.model.DeviceGroup
import com.homepilot.app.model.Entity
import com.homepilot.app.ui.components.EntityCard
import com.homepilot.app.viewmodel.DashboardViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
    onNavigateToConfig: () -> Unit = {}
) {
    val controllableGroups by viewModel.controllableGroups.collectAsState()
    val sensorGroups by viewModel.sensorGroups.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    val isConnected by viewModel.isConnected.collectAsState()
    val expandedCtrl by viewModel.expandedControllable.collectAsState()
    val expandedSensors by viewModel.expandedSensors.collectAsState()

    var selectedTab by remember { mutableStateOf(0) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.PowerSettingsNew, contentDescription = null, modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("设备")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.refreshAll() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "刷新")
                    }
                    IconButton(onClick = onNavigateToConfig) {
                        Icon(Icons.Default.Settings, contentDescription = "设置")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (!isConnected) {
                Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("请先配置 Home Assistant 服务器", style = MaterialTheme.typography.titleMedium)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("点击右上角设置图标进行配置", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(onClick = onNavigateToConfig) { Text("去配置") }
                        }
                    }
                }
            } else {
                TabRow(selectedTabIndex = selectedTab, containerColor = MaterialTheme.colorScheme.surface) {
                    Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 },
                        icon = { Icon(Icons.Default.PowerSettingsNew, contentDescription = null) },
                        text = { Text("可控设备") })
                    Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 },
                        icon = { Icon(Icons.Default.Sensors, contentDescription = null) },
                        text = { Text("传感器") })
                }

                when (selectedTab) {
                    0 -> GroupedDeviceList(
                        groups = controllableGroups,
                        expandedGroups = expandedCtrl,
                        isLoading = isLoading,
                        error = error,
                        onToggleGroup = { viewModel.toggleControllableGroup(it) },
                        onToggle = { viewModel.toggleEntity(it) },
                        emptyMessage = "没有可控设备"
                    )
                    1 -> GroupedDeviceList(
                        groups = sensorGroups,
                        expandedGroups = expandedSensors,
                        isLoading = isLoading,
                        error = error,
                        onToggleGroup = { viewModel.toggleSensorGroup(it) },
                        onToggle = {},
                        emptyMessage = "没有传感器数据",
                        readOnly = true
                    )
                }
            }
        }
    }
}

@Composable
private fun GroupedDeviceList(
    groups: List<DeviceGroup>,
    expandedGroups: Set<String>,
    isLoading: Boolean,
    error: String?,
    onToggleGroup: (String) -> Unit,
    onToggle: (String) -> Unit,
    emptyMessage: String,
    readOnly: Boolean = false
) {
    if (isLoading && groups.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(16.dp))
                Text("正在加载设备列表...")
            }
        }
    } else if (error != null && groups.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Error, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(36.dp))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(error, color = MaterialTheme.colorScheme.onErrorContainer)
                }
            }
        }
    } else if (groups.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(emptyMessage, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    } else {
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            var deviceCount = 0
            groups.forEach { group ->
                val isExpanded = group.name in expandedGroups

                // Header
                item(key = "header_${group.name}") {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onToggleGroup(group.name) },
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        tonalElevation = 1.dp
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Folder,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = group.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "${group.entities.size} 个设备",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Icon(
                                imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = if (isExpanded) "折叠" else "展开",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Devices (animated)
                if (isExpanded) {
                    items(group.entities, key = { it.entityId }) { entity ->
                        AnimatedVisibility(
                            visible = true,
                            enter = expandVertically(),
                            exit = shrinkVertically()
                        ) {
                            EntityCard(
                                entity = entity,
                                onToggle = { onToggle(entity.entityId) },
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }
                    }
                }
                deviceCount += group.entities.size
            }
        }
    }
}
