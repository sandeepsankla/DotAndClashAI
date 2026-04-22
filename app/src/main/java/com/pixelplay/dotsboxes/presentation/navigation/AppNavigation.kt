package com.pixelplay.dotsboxes.presentation.navigation

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.pixelplay.dotsboxes.DotsBoxesApp
import com.pixelplay.dotsboxes.domain.model.BoardSkin
import com.pixelplay.dotsboxes.domain.model.DailyLoginInfo
import com.pixelplay.dotsboxes.domain.model.Difficulty
import com.pixelplay.dotsboxes.domain.model.GameMode
import com.pixelplay.dotsboxes.domain.model.PlayerStats
import com.pixelplay.dotsboxes.presentation.screen.GameScreen
import com.pixelplay.dotsboxes.presentation.screen.HomeScreen
import com.pixelplay.dotsboxes.presentation.viewmodel.GameConfig
import com.pixelplay.dotsboxes.presentation.viewmodel.GameViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

sealed class Screen(val route: String) {
    object Home : Screen("home")
    object Game : Screen("game/{gridSize}/{mode}/{difficulty}/{p1}/{p2}") {
        fun buildRoute(
            gridSize: Int, mode: GameMode, difficulty: Difficulty,
            p1: String, p2: String
        ) = "game/$gridSize/${mode.name}/${difficulty.name}/$p1/$p2"
    }
}

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val app           = LocalContext.current.applicationContext as DotsBoxesApp
    val scope         = rememberCoroutineScope()

    // Daily login — runs once at app start
    var pendingDailyReward by remember { mutableStateOf<DailyLoginInfo?>(null) }
    LaunchedEffect(Unit) {
        val current = app.gameRepository.observeStats().first()
        val (updated, reward) = current.checkDailyLogin()
        if (reward != null) {
            app.gameRepository.saveStats(updated)
            pendingDailyReward = reward
        }
    }

    NavHost(navController = navController, startDestination = Screen.Home.route) {

        composable(Screen.Home.route) {
            val stats by app.gameRepository.observeStats()
                .collectAsState(initial = PlayerStats())

            HomeScreen(
                playerStats        = stats,
                pendingDailyReward = pendingDailyReward,
                onRewardDismissed  = { pendingDailyReward = null },
                onSkinSelected = { skin ->
                    scope.launch {
                        val current = app.gameRepository.observeStats().first()
                        app.gameRepository.saveStats(current.withActiveSkin(skin))
                    }
                },
                onStartGame = { config ->
                    navController.navigate(
                        Screen.Game.buildRoute(
                            config.gridSize, config.mode, config.difficulty,
                            config.p1Name.ifBlank { "Player 1" },
                            config.p2Name.ifBlank {
                                if (config.mode == GameMode.PVP) "Player 2" else "AI"
                            }
                        )
                    )
                }
            )
        }

        composable(
            route = Screen.Game.route,
            arguments = listOf(
                navArgument("gridSize")   { type = NavType.IntType },
                navArgument("mode")       { type = NavType.StringType },
                navArgument("difficulty") { type = NavType.StringType },
                navArgument("p1")         { type = NavType.StringType },
                navArgument("p2")         { type = NavType.StringType }
            )
        ) { entry ->
            val factory = GameViewModel.Factory(app.gameRepository, app.soundManager)
            val vm: GameViewModel = viewModel(factory = factory)

            val config = GameConfig(
                gridSize   = entry.arguments!!.getInt("gridSize"),
                mode       = GameMode.valueOf(entry.arguments!!.getString("mode")!!),
                difficulty = Difficulty.valueOf(entry.arguments!!.getString("difficulty")!!),
                p1Name     = entry.arguments!!.getString("p1")!!,
                p2Name     = entry.arguments!!.getString("p2")!!
            )

            GameScreen(
                viewModel      = vm,
                config         = config,
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
