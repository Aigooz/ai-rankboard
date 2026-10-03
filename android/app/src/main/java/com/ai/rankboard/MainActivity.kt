package com.ai.rankboard

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import com.ai.rankboard.ui.settings.SettingsScreen
import com.ai.rankboard.ui.theme.RankboardTheme

private data class TopLevelDestination(
    val route: String,
    val icon: ImageVector,
    val label: String,
)

private val TOP_LEVEL_DESTINATIONS = listOf(
    TopLevelDestination("home", Icons.Filled.Home, "榜单"),
    TopLevelDestination("compare", Icons.AutoMirrored.Filled.CompareArrows, "对比"),
    TopLevelDestination("favorites", Icons.Outlined.StarBorder, "收藏"),
    TopLevelDestination("settings", Icons.Outlined.Settings, "设置"),
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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

                Scaffold(
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
                            )
                        }
                        composable("settings") {
                            SettingsScreen(
                                onBack = { navController.popBackStack() },
                                onOpenAbout = { navController.navigate("about") },
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
                            )
                        }
                    }
                }
            }
        }
    }
}
