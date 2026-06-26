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
import com.pixelplay.dotsboxes.domain.model.*
import com.pixelplay.dotsboxes.presentation.screen.GameScreen
import com.pixelplay.dotsboxes.presentation.screen.HomeScreen
import com.pixelplay.dotsboxes.presentation.screen.LevelSelectScreen
import com.pixelplay.dotsboxes.presentation.viewmodel.GameConfig
import com.pixelplay.dotsboxes.presentation.viewmodel.GameViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

sealed class Screen(val route: String) {
    object Home        : Screen("home")
    object LevelSelect : Screen("level_select")
    object Game : Screen("game/{gridSize}/{mode}/{difficulty}/{p1}/{p2}/{level}") {
        fun buildRoute(
            gridSize: Int, mode: GameMode, difficulty: Difficulty,
            p1: String, p2: String, level: Int = -1
        ) = "game/$gridSize/${mode.name}/${difficulty.name}/$p1/$p2/$level"
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
                onCampaign         = { navController.navigate(Screen.LevelSelect.route) },
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

        composable(Screen.LevelSelect.route) {
            val stats by app.gameRepository.observeStats()
                .collectAsState(initial = PlayerStats())
            LevelSelectScreen(
                playerStats     = stats,
                onLevelSelected = { lvl ->
                    navController.navigate(
                        Screen.Game.buildRoute(
                            gridSize   = lvl.gridSize,
                            mode       = GameMode.PVA,
                            difficulty = lvl.difficulty,
                            p1         = "You",
                            p2         = "AI",
                            level      = lvl.number
                        )
                    )
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Screen.Game.route,
            arguments = listOf(
                navArgument("gridSize")   { type = NavType.IntType },
                navArgument("mode")       { type = NavType.StringType },
                navArgument("difficulty") { type = NavType.StringType },
                navArgument("p1")         { type = NavType.StringType },
                navArgument("p2")         { type = NavType.StringType },
                navArgument("level")      { type = NavType.IntType; defaultValue = -1 }
            )
        ) { entry ->
            val factory = GameViewModel.Factory(app.gameRepository, app.soundManager)
            val vm: GameViewModel = viewModel(factory = factory)

            val levelArg = entry.arguments!!.getInt("level").takeIf { it > 0 }
            val config = GameConfig(
                gridSize    = entry.arguments!!.getInt("gridSize"),
                mode        = GameMode.valueOf(entry.arguments!!.getString("mode")!!),
                difficulty  = Difficulty.valueOf(entry.arguments!!.getString("difficulty")!!),
                p1Name      = entry.arguments!!.getString("p1")!!,
                p2Name      = entry.arguments!!.getString("p2")!!,
                levelNumber = levelArg
            )

            // Compute next-level callback for campaign mode
            val onNextLevel: (() -> Unit)? = levelArg?.let { lvl ->
                if (lvl < CAMPAIGN_LEVELS.size) {
                    {
                        val next = CAMPAIGN_LEVELS[lvl] // lvl is 1-based, index = lvl
                        navController.navigate(
                            Screen.Game.buildRoute(
                                gridSize   = next.gridSize,
                                mode       = GameMode.PVA,
                                difficulty = next.difficulty,
                                p1         = config.p1Name,
                                p2         = "AI",
                                level      = next.number
                            )
                        ) { popUpTo(Screen.Game.route) { inclusive = true } }
                    }
                } else null
            }

            GameScreen(
                viewModel      = vm,
                config         = config,
                onNavigateBack = { navController.popBackStack() },
                onNextLevel    = onNextLevel
            )
        }
    }
}
