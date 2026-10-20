package com.ai.rankboard

import android.os.Bundle
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.ai.rankboard.ui.about.AboutScreen
import com.ai.rankboard.ui.compare.CompareScreen
import com.ai.rankboard.ui.detail.DetailScreen
import com.ai.rankboard.ui.favorites.FavoritesScreen
import com.ai.rankboard.ui.home.HomeScreen
import com.ai.rankboard.ui.relay.RelayScreen
import com.ai.rankboard.ui.select.SelectScreen
import com.ai.rankboard.ui.settings.SettingsScreen
import com.ai.rankboard.ui.theme.RankboardTheme
import com.ai.rankboard.ui.trend.TrendScreen
import com.ai.rankboard.ui.common.AppUpdateDialog
import com.ai.rankboard.data.AppUpdateStatus
import com.ai.rankboard.data.AppUpdateInfo
import com.ai.rankboard.data.AppUpdateNotifier
import com.ai.rankboard.data.AppUpdater
import com.ai.rankboard.data.DownloadProgress
import kotlinx.coroutines.launch

private data class TopLevelDestination(
    val route: String,
    val icon: ImageVector,
    val label: String,
)

private val TOP_LEVEL_DESTINATIONS = listOf(
    TopLevelDestination("home", Icons.Filled.Home, "榜单"),
    TopLevelDestination("select", Icons.Filled.Savings, "选型"),
    TopLevelDestination("compare", Icons.AutoMirrored.Filled.CompareArrows, "对比"),
    TopLevelDestination("trends", Icons.AutoMirrored.Filled.TrendingUp, "趋势"),
    TopLevelDestination("relay", Icons.Filled.Hub, "中转"),
    TopLevelDestination("settings", Icons.Outlined.Settings, "设置"),
)

class MainActivity : ComponentActivity() {
    private var notificationCheckRequest by mutableStateOf(0)

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra(AppUpdateNotifier.EXTRA_CHECK_UPDATE, false)) {
            notificationCheckRequest++
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (intent.getBooleanExtra(AppUpdateNotifier.EXTRA_CHECK_UPDATE, false)) {
            notificationCheckRequest++
        }
        enableEdgeToEdge()
        setContent {
            val app = applicationContext as RankboardApp
            val settings by app.settingsStore.settings.collectAsState()
            RankboardTheme(
                themeMode = settings.themeMode,
                dynamicColor = settings.dynamicColor,
            ) {
            val navController = rememberNavController()
            val backStackEntry by navController.currentBackStackEntryAsState()
            val currentRoute = backStackEntry?.destination?.route
            val context = LocalContext.current
            val pendingUpdate by app.appUpdateMonitor.pendingUpdate.collectAsState()
            var updateInstalling by remember { mutableStateOf(false) }
            var downloadProgress by remember { mutableStateOf<DownloadProgress?>(null) }
            var updateMessage by remember { mutableStateOf("") }
            val updateScope = rememberCoroutineScope()
            var awaitingInstallPermission by rememberSaveable { mutableStateOf(false) }
            var permissionVersionCode by rememberSaveable { mutableStateOf(0) }

            fun installUpdate(info: AppUpdateInfo) {
                updateScope.launch {
                    updateInstalling = true
                    downloadProgress = DownloadProgress(0L, info.sizeBytes, 0L)
                    updateMessage = ""
                    try {
                        val result = AppUpdater.downloadAndInstall(context, info) { progress ->
                            downloadProgress = progress
                        }
                        if (result.status == AppUpdateStatus.DOWNLOADED) {
                            app.appUpdateMonitor.clearPending()
                        } else {
                            updateMessage = result.message
                        }
                    } finally {
                        downloadProgress = null
                        updateInstalling = false
                    }
                }
            }

            val installPermissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.StartActivityForResult(),
            ) {
                val info = app.appUpdateMonitor.pendingUpdate.value
                val shouldContinue = awaitingInstallPermission
                awaitingInstallPermission = false
                if (shouldContinue && info != null && info.versionCode == permissionVersionCode) {
                    if (AppUpdater.canInstall(context)) {
                        installUpdate(info)
                    } else {
                        updateMessage = "未获得安装权限，未开始下载。可重新授权后重试。"
                    }
                }
            }
            val notificationPermissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission(),
            ) {}

            LaunchedEffect(Unit) {
                if (
                    (settings.updateReminders || settings.appUpdateReminders) &&
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    app.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
                    PackageManager.PERMISSION_GRANTED
                ) {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }

            LaunchedEffect(pendingUpdate?.versionCode) {
                updateMessage = ""
            }
            LaunchedEffect(notificationCheckRequest) {
                if (notificationCheckRequest > 0) {
                    app.appUpdateMonitor.checkNow(
                        enabled = settings.appUpdateReminders,
                        url = settings.appUpdateUrl.ifBlank { BuildConfig.APP_UPDATE_URL },
                        manual = true,
                    )
                }
            }

                Scaffold(
                    contentWindowInsets = WindowInsets(0, 0, 0, 0),
                    bottomBar = {
                        if (currentRoute in TOP_LEVEL_DESTINATIONS.map { it.route }) {
                            NavigationBar {
                                TOP_LEVEL_DESTINATIONS.forEach { destination ->
                                    NavigationBarItem(
                                        selected = currentRoute == destination.route,
                                        onClick = {
                                            navController.navigate(destination.route) {
                                                popUpTo(navController.graph.findStartDestination().id) {
                                                    saveState = true
                                                }
                                                launchSingleTop = true
                                                restoreState = true
                                            }
                                        },
                                        icon = {
                                            Icon(destination.icon, contentDescription = destination.label)
                                        },
                                        label = { Text(destination.label) },
                                    )
                                }
                            }
                        }
                    },
                ) { padding ->
                    NavHost(
                        navController = navController,
                        startDestination = "home",
                        modifier = Modifier.padding(padding),
                    ) {
                        composable("home") {
                            HomeScreen(
                                onOpenModel = { slug -> navController.navigate("model/$slug") },
                                defaultTab = settings.defaultTab,
                                compactList = settings.compactList,
                                showOverview = settings.showOverview,
                            )
                        }
                        composable("settings") {
                            SettingsScreen(
                                onBack = { navController.popBackStack() },
                                onOpenAbout = { navController.navigate("about") },
                                onOpenFavorites = { navController.navigate("favorites") },
                            )
                        }
                        composable("about") {
                            AboutScreen(
                                onBack = { navController.popBackStack() },
                            )
                        }
                        composable("model/{slug}") { entry ->
                            DetailScreen(
                                slug = entry.arguments?.getString("slug").orEmpty(),
                                onOpenModel = { slug -> navController.navigate("model/${android.net.Uri.encode(slug)}") },
                                onBack = { navController.popBackStack() },
                            )
                        }
                        composable("favorites") {
                            FavoritesScreen(
                                onOpenModel = { slug -> navController.navigate("model/$slug") },
                                onBack = { navController.popBackStack() },
                            )
                        }
                        composable("compare") {
                            CompareScreen(
                                onBack = { navController.popBackStack() },
                                onOpenModel = { slug -> navController.navigate("model/$slug") },
                            )
                        }
                        composable("select") {
                            SelectScreen(
                                onOpenModel = { slug -> navController.navigate("model/$slug") },
                                onBack = { navController.popBackStack() },
                            )
                        }
                        composable("relay") {
                            RelayScreen()
                        }
                        composable("trends") {
                            TrendScreen(
                                onOpenModel = { slug -> navController.navigate("model/$slug") },
                                onBack = { navController.popBackStack() },
                            )
                        }
                    }
                }

                pendingUpdate?.let { info ->
                    AppUpdateDialog(
                        info = info,
                        installing = updateInstalling || awaitingInstallPermission,
                        progress = downloadProgress,
                        message = updateMessage,
                        onDismiss = {
                            if (!updateInstalling && !awaitingInstallPermission) {
                                app.appUpdateMonitor.dismiss(info)
                                updateMessage = ""
                            }
                        },
                        onConfirm = {
                            if (!AppUpdater.canInstall(context)) {
                                awaitingInstallPermission = true
                                permissionVersionCode = info.versionCode
                                installPermissionLauncher.launch(AppUpdater.installPermissionIntent(context))
                            } else {
                                installUpdate(info)
                            }
                        },
                    )
                }
            }
        }
    }
}
