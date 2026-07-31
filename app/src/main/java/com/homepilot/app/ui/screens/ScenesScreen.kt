package com.homepilot.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.homepilot.app.ui.components.SceneCard
import com.homepilot.app.viewmodel.ScenesViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScenesScreen(
    viewModel: ScenesViewModel
) {
    val scenes by viewModel.scenes.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    val triggerResult by viewModel.triggerResult.collectAsState()

    var showConfirmDialog by remember { mutableStateOf<String?>(null) }

    // Snackbar
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(triggerResult) {
        triggerResult?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearTriggerResult()
        }
    }
    LaunchedEffect(error) {
        error?.let {
            snackbarHostState.showSnackbar(it)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.PlaylistPlay, contentDescription = null,
                            modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("场景与自动化")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.loadScenes() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "刷新")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when {
                isLoading && scenes.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("正在加载场景列表...")
                        }
                    }
                }
                scenes.isEmpty() -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    modifier = Modifier.size(48.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = "暂无场景",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "请在 Home Assistant 中创建场景或自动化",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
                else -> {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        item {
                            Text(
                                text = "点击「触发」按钮即可执行场景",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                        }
                        items(scenes, key = { it.entityId }) { scene ->
                            SceneCard(
                                scene = scene,
                                onTrigger = { showConfirmDialog = scene.entityId }
                            )
                        }
                    }
                }
            }
        }
    }

    // Confirmation dialog
    showConfirmDialog?.let { entityId ->
        val sceneName = scenes.find { it.entityId == entityId }?.friendlyName ?: entityId
        AlertDialog(
            onDismissRequest = { showConfirmDialog = null },
            icon = { Icon(Icons.Default.PlayCircle, contentDescription = null) },
            title = { Text("触发场景") },
            text = { Text("确认触发场景「$sceneName」？") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.triggerScene(entityId)
                        showConfirmDialog = null
                    }
                ) {
                    Text("确认触发")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showConfirmDialog = null }) {
                    Text("取消")
                }
            }
        )
    }
}
