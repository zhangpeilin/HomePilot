package com.homepilot.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.homepilot.app.model.ServerConfig
import com.homepilot.app.viewmodel.ConnectionState
import com.homepilot.app.viewmodel.ServerConfigViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerConfigScreen(
    viewModel: ServerConfigViewModel,
    homeGroupingEnabled: Boolean = true,
    homeExpandDefault: Boolean = true,
    onGroupingToggle: (Boolean) -> Unit = {},
    onExpandToggle: (Boolean) -> Unit = {},
    onConfigSaved: () -> Unit = {}
) {
    val config by viewModel.serverConfig.collectAsState()
    val connectionState by viewModel.connectionState.collectAsState()

    var host by remember(config) { mutableStateOf(config.host) }
    var port by remember(config) { mutableStateOf(config.port.toString()) }
    var token by remember(config) { mutableStateOf(config.accessToken) }
    var useTls by remember(config) { mutableStateOf(config.useTls) }
    var showToken by remember { mutableStateOf(false) }

    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp)
    ) {
        Text(
            text = "服务器配置",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Text(
            text = "配置 Home Assistant 服务器连接信息",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        // Host
        OutlinedTextField(
            value = host,
            onValueChange = { host = it },
            label = { Text("服务器地址") },
            placeholder = { Text("192.168.1.100 或 homeassistant.local") },
            leadingIcon = { Icon(Icons.Default.Dns, contentDescription = null) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Port
        OutlinedTextField(
            value = port,
            onValueChange = { port = it.filter { c -> c.isDigit() } },
            label = { Text("端口") },
            placeholder = { Text("8123") },
            leadingIcon = { Icon(Icons.Default.Tag, contentDescription = null) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )

        Spacer(modifier = Modifier.height(12.dp))

        // TLS Toggle
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (useTls) Icons.Default.Lock else Icons.Default.LockOpen,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = "使用 HTTPS",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f)
            )
            Switch(
                checked = useTls,
                onCheckedChange = { useTls = it }
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Access Token
        OutlinedTextField(
            value = token,
            onValueChange = { token = it },
            label = { Text("Long-Lived Access Token") },
            placeholder = { Text("从 HA 用户资料页面获取") },
            leadingIcon = { Icon(Icons.Default.Key, contentDescription = null) },
            trailingIcon = {
                IconButton(onClick = { showToken = !showToken }) {
                    Icon(
                        imageVector = if (showToken) Icons.Default.VisibilityOff
                        else Icons.Default.Visibility,
                        contentDescription = if (showToken) "隐藏令牌" else "显示令牌"
                    )
                }
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            visualTransformation = if (showToken) VisualTransformation.None
            else PasswordVisualTransformation()
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Connection test result
        when (val state = connectionState) {
            is ConnectionState.Idle -> { /* nothing */ }
            is ConnectionState.Testing -> {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "正在测试连接...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            is ConnectionState.Success -> {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(state.message)
                    }
                }
            }
            is ConnectionState.Error -> {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Error, contentDescription = null,
                            tint = MaterialTheme.colorScheme.error)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = state.message,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Action buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = {
                    val portInt = port.toIntOrNull() ?: 8123
                    viewModel.testConnection(ServerConfig(host, portInt, token, useTls))
                },
                modifier = Modifier.weight(1f),
                enabled = host.isNotBlank() && token.isNotBlank()
            ) {
                Icon(Icons.Default.NetworkCheck, contentDescription = null,
                    modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("测试连接")
            }

            Button(
                onClick = {
                    val portInt = port.toIntOrNull() ?: 8123
                    viewModel.saveConfig(ServerConfig(host, portInt, token, useTls))
                    onConfigSaved()
                },
                modifier = Modifier.weight(1f),
                enabled = host.isNotBlank() && token.isNotBlank()
            ) {
                Icon(Icons.Default.Save, contentDescription = null,
                    modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("保存配置")
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Home grouping toggle
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "首页按区域分组",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = if (homeGroupingEnabled) "已启用：首页按钮按区域收纳" else "已关闭：全部平铺显示",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = homeGroupingEnabled,
                    onCheckedChange = onGroupingToggle
                )
            }
        }

        // 展开/折叠默认状态（仅在分组开启时有效）
        if (homeGroupingEnabled) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "分组默认展开",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = if (homeExpandDefault) "区域分组默认全部展开" else "区域分组默认全部折叠",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = homeExpandDefault,
                        onCheckedChange = onExpandToggle
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Help card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.tertiaryContainer
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "如何获取 Access Token？",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "1. 打开 Home Assistant Web 界面\n" +
                            "2. 点击左下角用户头像 → 资料\n" +
                            "3. 滚动到「长期访问令牌」\n" +
                            "4. 点击「创建令牌」并复制",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}
