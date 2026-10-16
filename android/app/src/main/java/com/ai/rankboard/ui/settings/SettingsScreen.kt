package com.ai.rankboard.ui.settings

import android.content.Intent
import android.app.NotificationManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Dataset
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.runtime.DisposableEffect
import com.ai.rankboard.BuildConfig
import com.ai.rankboard.RankboardApp
import com.ai.rankboard.data.AppUpdateStatus
import com.ai.rankboard.data.AppUpdateNotifier
import com.ai.rankboard.data.UpdateNotifier
import com.ai.rankboard.data.FavoriteEntity
import com.ai.rankboard.data.SnapshotFrequency
import com.ai.rankboard.data.SnapshotUpdateStatus
import com.ai.rankboard.data.ThemeMode
import com.ai.rankboard.ui.common.openUrl
import com.google.gson.Gson
import com.google.gson.JsonParser
import java.text.SimpleDateFormat
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class FavoriteBackup(
    val app: String = "ai-rankboard",
    val version: Int = 1,
    val savedAt: Long = System.currentTimeMillis(),
    val favorites: List<FavoriteBackupItem> = emptyList(),
)

private data class FavoriteBackupItem(
    val slug: String,
    val name: String,
    val savedAt: Long,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenAbout: () -> Unit,
    onOpenFavorites: () -> Unit = {},
) {
    val app = LocalContext.current.applicationContext as RankboardApp
    val settings by app.settingsStore.settings.collectAsState()
    val snapshot by app.snapshotStore.snapshotFlow.collectAsState()
    val favorites by app.repository.favorites().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    val gson = remember { Gson() }

    val snapshotCheck by app.snapshotStore.checkState.collectAsState()
    val appUpdateCheck by app.appUpdateMonitor.state.collectAsState()
    val officialSnapshot = settings.snapshotUrl.isBlank() || settings.snapshotUrl == BuildConfig.SNAPSHOT_URL
    val officialAppUpdate = settings.appUpdateUrl.isBlank() || settings.appUpdateUrl == BuildConfig.APP_UPDATE_URL
    var snapshotMessage by remember { mutableStateOf("") }
    var cacheCount by remember { mutableStateOf(0) }
    var notificationsEnabled by remember { mutableStateOf(NotificationManagerCompat.from(app).areNotificationsEnabled()) }
    var blockedChannels by remember { mutableStateOf(emptyList<String>()) }
    var confirmReset by remember { mutableStateOf(false) }

    fun refreshNotificationStatus() {
        notificationsEnabled = NotificationManagerCompat.from(app).areNotificationsEnabled()
        val manager = app.getSystemService(NotificationManager::class.java)
        blockedChannels = listOf(
            Triple(settings.updateReminders, UpdateNotifier.CHANNEL_ID, "榜单通知"),
            Triple(settings.appUpdateReminders, AppUpdateNotifier.CHANNEL_ID, "应用更新通知"),
        ).filter { (enabled, channel, _) ->
            enabled && manager.getNotificationChannel(channel)?.importance == NotificationManager.IMPORTANCE_NONE
        }.map { it.third }
    }

    val notificationSettingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { refreshNotificationStatus() }
    LaunchedEffect(settings.updateReminders, settings.appUpdateReminders) { refreshNotificationStatus() }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, settings.updateReminders, settings.appUpdateReminders) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshNotificationStatus()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val result = runCatching {
                    withContext(Dispatchers.IO) {
                        val backup = FavoriteBackup(
                            favorites = app.repository.favoriteList().map {
                                FavoriteBackupItem(it.modelSlug, it.displayName, it.savedAt)
                            },
                        )
                        app.contentResolver.openOutputStream(uri)?.use { output ->
                            output.write(gson.toJson(backup).toByteArray(Charsets.UTF_8))
                        } ?: error("无法写入所选文件")
                    }
                }
                snapshotMessage = if (result.isSuccess) {
                    "收藏已导出"
                } else {
                    "导出失败：${result.exceptionOrNull()?.message}"
                }
            }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val result = runCatching {
                    withContext(Dispatchers.IO) {
                        val content = app.contentResolver.openInputStream(uri)?.use { input ->
                            input.readBytes().decodeToString()
                        } ?: error("无法读取所选文件")
                        val root = JsonParser.parseString(content).asJsonObject
                        val array = root.getAsJsonArray("favorites")
                        array.mapNotNull { element ->
                            val item = element.asJsonObject
                            val slug = item.get("slug")?.asString ?: return@mapNotNull null
                            FavoriteEntity(
                                modelSlug = slug,
                                displayName = item.get("name")?.asString ?: slug,
                                savedAt = item.get("savedAt")?.asLong ?: System.currentTimeMillis(),
                            )
                        }
                    }
                }
                val imported = result.getOrDefault(emptyList())
                if (imported.isNotEmpty()) {
                    app.repository.importFavorites(imported)
                    snapshotMessage = "已导入 ${imported.size} 个收藏"
                } else {
                    snapshotMessage = result.exceptionOrNull()?.let { "导入失败：${it.message}" } ?: "备份文件中没有收藏"
                }
            }
        }
    }

    LaunchedEffect(snapshot) {
        cacheCount = runCatching { app.repository.cachedEntryCount() }.getOrDefault(0)
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
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                SettingsGroup(title = "外观", icon = Icons.Filled.Palette) {
                    OptionRow(
                        label = "主题模式",
                        options = ThemeMode.entries.map { mode ->
                            mode to when (mode) {
                                ThemeMode.SYSTEM -> "跟随系统"
                                ThemeMode.LIGHT -> "浅色"
                                ThemeMode.DARK -> "深色"
                            }
                        },
                        selected = settings.themeMode,
                        onSelect = app.settingsStore::setThemeMode,
                    )
                    SwitchRow(
                        title = "动态取色",
                        subtitle = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            "跟随系统壁纸生成配色"
                        } else {
                            "需要 Android 12 或更高版本"
                        },
                        checked = settings.dynamicColor,
                        onCheckedChange = app.settingsStore::setDynamicColor,
                    )
                }
            }

            item {
                SettingsGroup(title = "榜单界面", icon = Icons.Filled.Tune) {
                    OptionRow(
                        label = "启动时进入",
                        options = listOf(
                            "overall" to "综合",
                            "coding" to "代码",
                            "writing" to "写作",
                            "multimodal" to "多模态",
                            "month" to "月榜",
                            "quarter" to "季榜",
                            "year" to "年榜",
                        ),
                        selected = settings.defaultTab,
                        onSelect = app.settingsStore::setDefaultTab,
                    )
                    SwitchRow(
                        title = "紧凑列表",
                        subtitle = "显示更多模型，减少纵向留白",
                        checked = settings.compactList,
                        onCheckedChange = app.settingsStore::setCompactList,
                    )
                    SwitchRow(
                        title = "榜单速览",
                        subtitle = "在列表顶部显示前三名和数据状态",
                        checked = settings.showOverview,
                        onCheckedChange = app.settingsStore::setShowOverview,
                    )
                }
            }

            item {
                SettingsGroup(title = "榜单数据", icon = Icons.Filled.Dataset) {
                    UpdateSummary(
                        headline = if (snapshotCheck.checking) "正在同步榜单…" else "${snapshot.models.size} 个模型 · ${snapshot.boards.size} 个榜单",
                        detail = "数据时间 ${snapshot.generatedAt.take(19).replace("T", " ")}\n" +
                            "${snapshot.sources.size} 个数据源 · " +
                            if (officialSnapshot) "官方数据源" else "自定义数据源",
                        checkedAt = snapshotCheck.checkedAt,
                        message = snapshotCheck.result?.message,
                        isError = snapshotCheck.result?.status == SnapshotUpdateStatus.ERROR,
                    )
                    UpdateAction(
                        label = if (snapshotCheck.checking) "同步中" else "立即同步",
                        checking = snapshotCheck.checking,
                        icon = Icons.Filled.Refresh,
                        onClick = { scope.launch { app.repository.refreshSnapshot() } },
                    )
                    OptionRow(
                        label = "自动更新频率",
                        options = SnapshotFrequency.entries.map { it to it.label },
                        selected = settings.snapshotFrequency,
                        onSelect = app.settingsStore::setSnapshotFrequency,
                    )
                    SwitchRow(
                        title = "榜单更新通知",
                        subtitle = "关闭通知仍会自动同步榜单",
                        checked = settings.updateReminders,
                        onCheckedChange = app.settingsStore::setUpdateReminders,
                    )
                    if (!officialSnapshot) {
                        TextButton(onClick = { app.settingsStore.setSnapshotUrl("") }) {
                            Text("恢复官方数据源")
                        }
                    }
                }
            }

            item {
                SettingsGroup(title = "应用更新", icon = Icons.Filled.Security) {
                    val available = appUpdateCheck.result?.takeIf { it.status == AppUpdateStatus.AVAILABLE }?.info
                    UpdateSummary(
                        headline = "当前版本 v${BuildConfig.VERSION_NAME}",
                        detail = if (officialAppUpdate) "官方稳定版 · 自动切换备用下载源" else "自定义更新源",
                        checkedAt = appUpdateCheck.checkedAt,
                        message = if (appUpdateCheck.checking) "正在检查新版本…" else appUpdateCheck.result?.message,
                        isError = appUpdateCheck.result?.status == AppUpdateStatus.ERROR && !appUpdateCheck.checking,
                    )
                    UpdateAction(
                        label = when {
                            appUpdateCheck.checking -> "检查中"
                            available != null -> "查看新版本 v${available.versionName}"
                            else -> "检查新版本"
                        },
                        checking = appUpdateCheck.checking,
                        icon = if (available != null) Icons.Filled.SystemUpdate else Icons.Filled.Refresh,
                        onClick = {
                            if (available != null) {
                                app.appUpdateMonitor.setAvailable(available, manual = true)
                            } else {
                                scope.launch {
                                    app.appUpdateMonitor.checkNow(
                                        enabled = settings.appUpdateReminders,
                                        url = settings.appUpdateUrl.ifBlank { BuildConfig.APP_UPDATE_URL },
                                        manual = true,
                                    )
                                }
                            }
                        },
                    )
                    SwitchRow(
                        title = "新版本自动提醒",
                        subtitle = "应用内弹窗，后台通过系统通知提醒",
                        checked = settings.appUpdateReminders,
                        onCheckedChange = app.settingsStore::setAppUpdateReminders,
                    )
                    if (!officialAppUpdate) {
                        TextButton(onClick = { app.settingsStore.setAppUpdateUrl("") }) {
                            Text("恢复官方更新源")
                        }
                    }
                }
            }

            if (settings.updateReminders || settings.appUpdateReminders) {
                item {
                    SettingsGroup(title = "推送通知", icon = Icons.Filled.Notifications) {
                        SettingsActionRow(
                            title = when {
                                !notificationsEnabled -> "系统通知未开启"
                                blockedChannels.isNotEmpty() -> "部分通知已关闭"
                                else -> "系统通知已开启"
                            },
                            detail = when {
                                !notificationsEnabled -> "后台更新提醒无法送达，应用内检查不受影响"
                                blockedChannels.isNotEmpty() -> "${blockedChannels.joinToString("、")}被系统关闭"
                                else -> "后台检查可能受省电策略延迟"
                            },
                            icon = Icons.Filled.Notifications,
                            label = if (notificationsEnabled) "管理" else "开启",
                            onClick = {
                                notificationSettingsLauncher.launch(
                                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                        .putExtra(Settings.EXTRA_APP_PACKAGE, app.packageName),
                                )
                            },
                        )
                    }
                }
            }

            item {
                SettingsGroup(title = "收藏与备份", icon = Icons.Filled.Star) {
                    SettingsLinkRow(
                        title = "我的收藏",
                        detail = "查看收藏的模型与各榜单成绩",
                        icon = Icons.Filled.Star,
                        onClick = onOpenFavorites,
                    )
                    TextRow(
                        title = "收藏数量",
                        value = "${favorites.size} 个",
                        detail = "备份保存在本机所选位置，不会上传服务器",
                    )
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedButton(
                            onClick = {
                                exportLauncher.launch("ai-rankboard-favorites-${System.currentTimeMillis()}.json")
                            },
                        ) {
                            Icon(Icons.Filled.Archive, contentDescription = null, modifier = Modifier.size(16.dp))
                            Text("导出", modifier = Modifier.padding(start = 6.dp))
                        }
                        OutlinedButton(
                            onClick = { importLauncher.launch(arrayOf("application/json", "text/*", "*/*")) },
                        ) {
                            Icon(Icons.Filled.Dataset, contentDescription = null, modifier = Modifier.size(16.dp))
                            Text("导入", modifier = Modifier.padding(start = 6.dp))
                        }
                    }
                    SettingsActionRow(
                        title = "清除离线缓存",
                        detail = "$cacheCount 条榜单缓存将被删除，收藏不受影响",
                        icon = Icons.Filled.CleaningServices,
                        label = "清除",
                        onClick = {
                            scope.launch {
                                app.repository.clearCache()
                                cacheCount = 0
                                snapshotMessage = "已清除离线缓存"
                            }
                        },
                    )
                    if (snapshotMessage.isNotBlank()) StatusText(snapshotMessage)
                }
            }

            item {
                SettingsGroup(title = "项目与帮助", icon = Icons.Filled.Info) {
                    SettingsLinkRow(
                        title = "关于 AI 排行榜",
                        detail = "版本、数据来源、项目链接",
                        icon = Icons.Filled.Info,
                        onClick = onOpenAbout,
                    )
                    SettingsLinkRow(
                        title = "反馈问题",
                        detail = "在 GitHub Issues 提交榜单解析或更新问题",
                        icon = Icons.Filled.BugReport,
                        onClick = { openUrl(app, "https://github.com/Aigooz/ai-rankboard/issues") },
                    )
                    SettingsActionRow(
                        title = "重置设置",
                        detail = "恢复默认外观与更新设置",
                        icon = Icons.Filled.CleaningServices,
                        label = "重置",
                        onClick = { confirmReset = true },
                    )
                }
            }
        }
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("重置设置？") },
            text = { Text("主题、默认榜单、更新频率和远端地址会恢复默认，收藏不会被删除。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        app.settingsStore.reset()
                        confirmReset = false
                        snapshotMessage = "已恢复默认设置"
                    },
                ) { Text("重置") }
            },
            dismissButton = {
                TextButton(onClick = { confirmReset = false }) { Text("取消") }
            },
        )
    }

}

@Composable
private fun SettingsGroup(
    title: String,
    icon: ImageVector,
    content: @Composable () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(vertical = 12.dp)) {
            Row(
                Modifier.padding(horizontal = 14.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Surface(
                    shape = RoundedCornerShape(9.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier
                            .padding(6.dp)
                            .size(16.dp),
                    )
                }
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            }
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 8.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
            )
            content()
        }
    }
}

@Composable
private fun UpdateSummary(
    headline: String,
    detail: String,
    checkedAt: Long,
    message: String?,
    isError: Boolean = false,
) {
    val formatter = remember { SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()) }
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(headline, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            if (checkedAt > 0) "上次检查 ${formatter.format(checkedAt)}" else "尚未完成检查",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (!message.isNullOrBlank()) {
            Text(
                message,
                style = MaterialTheme.typography.bodySmall,
                color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun UpdateAction(label: String, checking: Boolean, icon: ImageVector, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = !checking,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        if (checking) {
            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
        } else {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(8.dp))
        Text(label)
    }
}

@Composable
private fun <T> OptionRow(
    label: String,
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            items(options.size) { index ->
                val (value, optionLabel) = options[index]
                FilterChip(
                    selected = selected == value,
                    onClick = { onSelect(value) },
                    label = { Text(optionLabel, maxLines = 1) },
                )
            }
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

@Composable
private fun TextRow(
    title: String,
    value: String,
    detail: String,
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
                detail,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            value,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun SettingsActionRow(
    title: String,
    detail: String,
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(
                detail,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(onClick = onClick) { Text(label) }
    }
}

@Composable
private fun SettingsLinkRow(
    title: String,
    detail: String,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(
                detail,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(
            Icons.AutoMirrored.Filled.OpenInNew,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(14.dp),
        )
    }
}

@Composable
private fun StatusText(message: String) {
    Text(
        message,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 14.dp, vertical = 2.dp),
    )
}
