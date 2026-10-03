package com.ai.rankboard.ui.settings

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ai.rankboard.BuildConfig
import com.ai.rankboard.RankboardApp
import com.ai.rankboard.data.AppUpdater
import com.ai.rankboard.data.AppUpdateWorker
import com.ai.rankboard.data.ThemeMode
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenAbout: () -> Unit,
) {
    val app = LocalContext.current.applicationContext as RankboardApp
    val settings by app.settingsStore.settings.collectAsState()
    val snapshot by app.snapshotStore.snapshotFlow.collectAsState()
    val scope = rememberCoroutineScope()
    var refreshing by remember { mutableStateOf(false) }
    var updateMessage by remember { mutableStateOf("") }
    var remoteUrl by remember { mutableStateOf(settings.snapshotUrl) }
    var appUpdateChecking by remember { mutableStateOf(false) }
    var appUpdateMessage by remember { mutableStateOf("") }
    var canInstall by remember { mutableStateOf(AppUpdater.canInstall(app)) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {}
    val installPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { canInstall = AppUpdater.canInstall(app) }

    fun requestNotificationPermission() {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            app.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                SettingsGroup(title = "外观", icon = Icons.Filled.Palette) {
                    ThemeMode.entries.forEach { mode ->
                        val label = when (mode) {
                            ThemeMode.SYSTEM -> "跟随系统"
                            ThemeMode.LIGHT -> "浅色模式"
                            ThemeMode.DARK -> "深色模式"
                        }
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = settings.themeMode == mode,
                                onClick = { app.settingsStore.setThemeMode(mode) },
                            )
                            Text(label, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    SwitchRow(
                        title = "动态取色",
                        subtitle = "Android 12+ 跟随壁纸配色",
                        checked = settings.dynamicColor,
                        onCheckedChange = app.settingsStore::setDynamicColor,
                    )
                }
            }
            item {
                SettingsGroup(title = "数据更新", icon = Icons.Filled.Notifications) {
                    OutlinedTextField(
                        value = remoteUrl,
                        onValueChange = { remoteUrl = it },
                        label = { Text("远端快照地址") },
                        placeholder = { Text("https://example.com/leaderboards.json") },
                        supportingText = {
                            Text(
                                "系统会自动请求同一地址加 .sha256 做校验",
                                style = MaterialTheme.typography.labelSmall,
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 2.dp),
                        singleLine = true,
                    )
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = if (settings.snapshotUrl.isBlank()) {
                                "当前使用内置数据"
                            } else {
                                "当前地址已保存"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(
                            onClick = {
                                val savedUrl = remoteUrl.trim()
                                app.settingsStore.setSnapshotUrl(savedUrl)
                                app.snapshotStore.setDailyUpdateEnabled(
                                    settings.updateReminders,
                                    savedUrl,
                                )
                                updateMessage = if (savedUrl.isBlank()) "已清除远端地址" else "已保存远端地址"
                            },
                            enabled = remoteUrl.trim() != settings.snapshotUrl,
                        ) {
                            Text("保存地址")
                        }
                    }
                    SwitchRow(
                        title = "更新提醒",
                        subtitle = if (BuildConfig.SNAPSHOT_URL.isBlank()) {
                            "当前未配置远端快照地址，只使用内置数据"
                        } else {
                            "发现新快照时发送系统通知"
                        },
                        checked = settings.updateReminders,
                        onCheckedChange = { enabled ->
                            app.settingsStore.setUpdateReminders(enabled)
                            app.snapshotStore.setDailyUpdateEnabled(
                                enabled,
                                BuildConfig.SNAPSHOT_URL,
                            )
                            AppUpdateWorker.schedule(
                                app,
                                enabled,
                                settings.appUpdateUrl.ifBlank { BuildConfig.APP_UPDATE_URL },
                            )
                            if (enabled) requestNotificationPermission()
                        },
                    )
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Button(
                            onClick = {
                                scope.launch {
                                    refreshing = true
                                    updateMessage = ""
                                    val result = app.repository.refreshSnapshot()
                                    updateMessage = result.message
                                    refreshing = false
                                }
                            },
                            enabled = !refreshing,
                        ) {
                            if (refreshing) {
                                CircularProgressIndicator(Modifier.size(18.dp))
                            } else {
                                Icon(Icons.Filled.Refresh, contentDescription = null)
                            }
                            Text(
                                if (refreshing) "检查中" else "立即检查更新",
                                modifier = Modifier.padding(start = 6.dp),
                            )
                        }
                    }
                    if (updateMessage.isNotBlank()) {
                        Text(
                            updateMessage,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 14.dp),
                        )
                    }
                    Text(
                        text = "快照时间 " + snapshot.generatedAt.take(19).replace("T", " ") +
                            " · Schema v${snapshot.schemaVersion}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 14.dp),
                    )
                }
            }
            item {
                SettingsGroup(title = "应用更新", icon = Icons.Filled.Refresh) {
                    Text(
                        text = "当前版本 v${BuildConfig.VERSION_NAME}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 14.dp),
                    )
                    Text(
                        text = "启动和每日自动检查，发现更新后下载并通知安装。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp),
                    )
                    Text(
                        text = if (canInstall) {
                            "已允许从本应用安装更新"
                        } else {
                            "尚未允许安装应用更新"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 14.dp),
                    )
                    Button(
                        onClick = {
                            if (!canInstall) {
                                installPermissionLauncher.launch(AppUpdater.installPermissionIntent(app))
                            } else {
                                scope.launch {
                                    appUpdateChecking = true
                                    appUpdateMessage = ""
                                    val result = AppUpdater.updateAndInstall(
                                        app,
                                        settings.appUpdateUrl.ifBlank { BuildConfig.APP_UPDATE_URL },
                                    )
                                    appUpdateMessage = result.message
                                    appUpdateChecking = false
                                }
                            }
                        },
                        enabled = !appUpdateChecking,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp),
                    ) {
                        if (appUpdateChecking) {
                            CircularProgressIndicator(Modifier.size(18.dp))
                        } else {
                            Icon(Icons.Filled.Refresh, contentDescription = null)
                        }
                        Text(
                            if (appUpdateChecking) "检查应用更新中" else if (canInstall) "检查并安装更新" else "允许安装应用更新",
                            modifier = Modifier.padding(start = 6.dp),
                        )
                    }
                    if (appUpdateMessage.isNotBlank()) {
                        Text(
                            appUpdateMessage,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 14.dp),
                        )
                    }
                }
            }
            item {
                SettingsGroup(title = "项目", icon = Icons.Filled.Info) {
                    TextButton(
                        onClick = onOpenAbout,
                        modifier = Modifier.padding(horizontal = 8.dp),
                    ) {
                        Text("关于 AI 排行榜")
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsGroup(
    title: String,
    icon: ImageVector,
    content: @Composable () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(vertical = 8.dp)) {
            Row(
                Modifier.padding(horizontal = 14.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(title, style = MaterialTheme.typography.titleSmall)
            }
            content()
        }
    }
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(
                subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
