package com.ai.rankboard.ui.settings

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
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
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Security
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
import androidx.compose.material3.OutlinedTextField
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
import com.ai.rankboard.data.AppUpdateStatus
import com.ai.rankboard.data.AppUpdateWorker
import com.ai.rankboard.data.FavoriteEntity
import com.ai.rankboard.data.SnapshotFrequency
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
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }

    var refreshing by remember { mutableStateOf(false) }
    var snapshotMessage by remember { mutableStateOf("") }
    var cacheCount by remember { mutableStateOf(0) }
    var remoteUrl by remember { mutableStateOf(settings.snapshotUrl) }
    var appUpdateUrl by remember { mutableStateOf(settings.appUpdateUrl) }
    var appUpdateChecking by remember { mutableStateOf(false) }
    var appUpdateMessage by remember { mutableStateOf("") }
    var canInstall by remember { mutableStateOf(AppUpdater.canInstall(app)) }
    var confirmReset by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {}
    val installPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { canInstall = AppUpdater.canInstall(app) }
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

    fun requestNotificationPermission() {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            app.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    fun scheduleSnapshotUpdates(enabled: Boolean) {
        app.snapshotStore.setDailyUpdateEnabled(
            enabled,
            settings.snapshotUrl.ifBlank { BuildConfig.SNAPSHOT_URL },
            settings.snapshotFrequency.days,
        )
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
                    TextRow(
                        title = "快照状态",
                        value = if (app.snapshotStore.isDownloaded) "已使用远端快照" else "内置快照",
                        detail = "${snapshot.generatedAt.take(19).replace("T", " ")} · Schema v${snapshot.schemaVersion}",
                    )
                    TextRow(
                        title = "数据规模",
                        value = "${snapshot.models.size} 个模型",
                        detail = "${snapshot.boards.size} 个榜单 · ${snapshot.sources.size} 个数据源 · $cacheCount 条缓存",
                    )
                    OutlinedTextField(
                        value = remoteUrl,
                        onValueChange = { remoteUrl = it },
                        label = { Text("远端快照地址") },
                        placeholder = { Text("https://example.com/leaderboards.json") },
                        supportingText = { Text("系统会请求同一地址加 .sha256 校验数据完整性") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp),
                    )
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Button(
                            onClick = {
                                val savedUrl = remoteUrl.trim()
                                app.settingsStore.setSnapshotUrl(savedUrl)
                                scheduleSnapshotUpdates(settings.updateReminders)
                                snapshotMessage = if (savedUrl.isBlank()) "已清除远端地址" else "已保存远端地址"
                            },
                            enabled = remoteUrl.trim() != settings.snapshotUrl,
                        ) { Text("保存") }
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    refreshing = true
                                    snapshotMessage = ""
                                    val result = app.repository.refreshSnapshot()
                                    snapshotMessage = result.message
                                    refreshing = false
                                }
                            },
                            enabled = !refreshing,
                        ) {
                            if (refreshing) {
                                CircularProgressIndicator(Modifier.size(15.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                            }
                            Text("立即检查", modifier = Modifier.padding(start = 6.dp))
                        }
                    }
                    OptionRow(
                        label = "自动更新频率",
                        options = SnapshotFrequency.entries.map { it to it.label },
                        selected = settings.snapshotFrequency,
                        onSelect = { frequency ->
                            app.settingsStore.setSnapshotFrequency(frequency)
                            scheduleSnapshotUpdates(settings.updateReminders)
                        },
                    )
                    SwitchRow(
                        title = "更新提醒",
                        subtitle = "发现新榜单数据时发送系统通知",
                        checked = settings.updateReminders,
                        onCheckedChange = { enabled ->
                            app.settingsStore.setUpdateReminders(enabled)
                            scheduleSnapshotUpdates(enabled)
                            AppUpdateWorker.schedule(
                                app,
                                enabled,
                                settings.appUpdateUrl.ifBlank { BuildConfig.APP_UPDATE_URL },
                            )
                            if (enabled) requestNotificationPermission()
                        },
                    )
                    if (snapshotMessage.isNotBlank()) {
                        StatusText(snapshotMessage)
                    }
                }
            }

            item {
                SettingsGroup(title = "应用更新", icon = Icons.Filled.Security) {
                    TextRow(
                        title = "当前版本",
                        value = "v${BuildConfig.VERSION_NAME}",
                        detail = "版本号 ${BuildConfig.VERSION_CODE} · 自动检查更新",
                    )
                    OutlinedTextField(
                        value = appUpdateUrl,
                        onValueChange = { appUpdateUrl = it },
                        label = { Text("应用更新清单地址") },
                        placeholder = { Text(BuildConfig.APP_UPDATE_URL) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp),
                    )
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Button(
                            onClick = {
                                val savedUrl = appUpdateUrl.trim()
                                app.settingsStore.setAppUpdateUrl(savedUrl)
                                AppUpdateWorker.schedule(
                                    app,
                                    settings.updateReminders,
                                    savedUrl.ifBlank { BuildConfig.APP_UPDATE_URL },
                                )
                                appUpdateMessage = if (savedUrl.isBlank()) "已恢复默认更新源" else "已保存更新源"
                            },
                            enabled = appUpdateUrl.trim() != settings.appUpdateUrl,
                        ) { Text("保存") }
                        OutlinedButton(
                            onClick = {
                                if (!canInstall) {
                                    installPermissionLauncher.launch(AppUpdater.installPermissionIntent(app))
                                } else {
                                    scope.launch {
                                        appUpdateChecking = true
                                        appUpdateMessage = ""
                                        val result = app.appUpdateMonitor.checkNow(
                                            settings.updateReminders,
                                            settings.appUpdateUrl.ifBlank { BuildConfig.APP_UPDATE_URL },
                                        )
                                        if (result != null && result.status != AppUpdateStatus.AVAILABLE) {
                                            appUpdateMessage = result.message
                                        }
                                        appUpdateChecking = false
                                    }
                                }
                            },
                            enabled = !appUpdateChecking,
                        ) {
                            if (appUpdateChecking) {
                                CircularProgressIndicator(Modifier.size(15.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Filled.Restore, contentDescription = null, modifier = Modifier.size(16.dp))
                            }
                            Text(
                                if (appUpdateChecking) {
                                    "检查中"
                                } else if (canInstall) {
                                    "检查并安装"
                                } else {
                                    "允许安装"
                                },
                                modifier = Modifier.padding(start = 6.dp),
                            )
                        }
                    }
                    TextRow(
                        title = "安装权限",
                        value = if (canInstall) "已允许" else "未授权",
                        detail = "安装应用内更新时需要允许“安装未知应用”",
                    )
                    if (appUpdateMessage.isNotBlank()) {
                        StatusText(appUpdateMessage)
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
                        AppUpdateWorker.schedule(app, true, BuildConfig.APP_UPDATE_URL)
                        app.snapshotStore.setDailyUpdateEnabled(true, BuildConfig.SNAPSHOT_URL, 1)
                        remoteUrl = ""
                        appUpdateUrl = ""
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
