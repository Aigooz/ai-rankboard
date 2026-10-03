package com.ai.rankboard

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.ai.rankboard.ui.about.AboutScreen
import com.ai.rankboard.ui.compare.CompareScreen
import com.ai.rankboard.ui.detail.DetailScreen
import com.ai.rankboard.ui.favorites.FavoritesScreen
import com.ai.rankboard.ui.home.HomeScreen
import com.ai.rankboard.ui.settings.SettingsScreen
import com.ai.rankboard.ui.theme.RankboardTheme

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
                NavHost(navController = navController, startDestination = "home") {
                    composable("home") {
                        HomeScreen(
                            onOpenModel = { slug -> navController.navigate("model/$slug") },
                            onOpenFavorites = { navController.navigate("favorites") },
                            onOpenCompare = { navController.navigate("compare") },
                            onOpenSettings = { navController.navigate("settings") },
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
