package com.ai.rankboard

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.ai.rankboard.ui.cover.CoverScreen
import com.ai.rankboard.ui.compare.CompareScreen
import com.ai.rankboard.ui.detail.DetailScreen
import com.ai.rankboard.ui.favorites.FavoritesScreen
import com.ai.rankboard.ui.home.HomeScreen
import com.ai.rankboard.ui.theme.RankboardTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            RankboardTheme {
                val navController = rememberNavController()
                NavHost(navController = navController, startDestination = "cover") {
                    composable("cover") {
                        CoverScreen(
                            onEnter = {
                                navController.navigate("home") {
                                    popUpTo("cover") { inclusive = true }
                                }
                            },
                        )
                    }
                    composable("home") {
                        HomeScreen(
                            onOpenModel = { slug -> navController.navigate("model/$slug") },
                            onOpenFavorites = { navController.navigate("favorites") },
                            onOpenCompare = { navController.navigate("compare") },
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
