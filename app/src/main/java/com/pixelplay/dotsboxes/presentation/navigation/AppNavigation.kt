package com.pixelplay.dotsboxes.presentation.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Person
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.pixelplay.dotsboxes.DotsBoxesApp
import com.pixelplay.dotsboxes.domain.model.*
import com.pixelplay.dotsboxes.presentation.screen.FlashChallengeScreen
import com.pixelplay.dotsboxes.presentation.screen.GameScreen
import com.pixelplay.dotsboxes.presentation.screen.HomeScreen
import com.pixelplay.dotsboxes.presentation.screen.LeaderboardScreen
import com.pixelplay.dotsboxes.presentation.screen.LuckySpinScreen
import com.pixelplay.dotsboxes.presentation.screen.LevelSelectScreen
import com.pixelplay.dotsboxes.presentation.screen.LoginScreen
import com.pixelplay.dotsboxes.presentation.screen.OnlineGameScreen
import com.pixelplay.dotsboxes.presentation.screen.OnlineLobbyScreen
import com.pixelplay.dotsboxes.presentation.screen.ProfileScreen
import com.pixelplay.dotsboxes.presentation.screen.RateUsScreen
import com.pixelplay.dotsboxes.presentation.screen.RewardStoreScreen
import com.pixelplay.dotsboxes.presentation.viewmodel.FlashChallengeViewModel
import com.pixelplay.dotsboxes.presentation.viewmodel.OnlineGameViewModel
import com.pixelplay.dotsboxes.presentation.viewmodel.GameConfig
import com.pixelplay.dotsboxes.presentation.viewmodel.GameViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

sealed class Screen(val route: String) {
    object Home           : Screen("home")
    object Profile        : Screen("profile")
    object RateUs         : Screen("rate_us")
    object LevelSelect    : Screen("level_select")
    object RewardStore    : Screen("reward_store")
    object Login          : Screen("login")
    object OnlineLobby    : Screen("online_lobby")
    object FlashChallenge : Screen("flash_challenge")
    object LuckySpin      : Screen("lucky_spin")
    object Leaderboard    : Screen("leaderboard")
    object OnlineGame   : Screen("online_game/{code}/{isHost}/{myName}/{gridSize}") {
        fun buildRoute(code: String, isHost: Boolean, myName: String, gridSize: Int) =
            "online_game/$code/$isHost/${myName.ifBlank { "Player" }}/$gridSize"
    }
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

    // Daily login — runs once at app start. The reward XP/coins are granted only
    // when the user taps "collect" (onRewardDismissed), not when the dialog appears.
    var pendingDailyReward by remember { mutableStateOf<DailyLoginInfo?>(null) }
    var pendingDailyStats  by remember { mutableStateOf<PlayerStats?>(null) }
    LaunchedEffect(Unit) {
        val current = app.gameRepository.observeStats().first()
        // Expire hints whose 2-day window has passed — save this immediately (not a reward)
        val afterExpiry = current.expireHintsIfDue()
        if (afterExpiry.hintCoins != current.hintCoins) {
            app.gameRepository.saveStats(afterExpiry)
        }
        val (updated, reward) = afterExpiry.checkDailyLogin()
        if (reward != null) {
            pendingDailyStats  = updated   // held until the user collects
            pendingDailyReward = reward
        }
    }

    // Invite deep link → jump into online lobby (code prefilled there)
    LaunchedEffect(app.pendingInviteCode) {
        if (app.pendingInviteCode != null) {
            navController.navigate(Screen.OnlineLobby.route)
        }
    }

    // Bottom nav shows only on top-level destinations
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    // Guards against double-tap back crash: only pop if current screen is fully resumed
    val safePop: () -> Unit = {
        if (navController.currentBackStackEntry?.lifecycle?.currentState
                ?.isAtLeast(Lifecycle.State.RESUMED) == true
        ) {
            navController.popBackStack()
        }
    }

    Scaffold(
        bottomBar = {
            if (currentRoute in BOTTOM_NAV_ROUTES) {
                AppBottomBar(navController = navController, currentRoute = currentRoute)
            }
        }
    ) { innerPadding ->
    NavHost(
        navController = navController,
        startDestination = Screen.Home.route,
        modifier = Modifier.padding(innerPadding)
    ) {

        composable(Screen.Home.route) {
            val stats by app.gameRepository.observeStats()
                .collectAsState(initial = PlayerStats())

            HomeScreen(
                playerStats        = stats,
                pendingDailyReward = pendingDailyReward,
                onRewardDismissed  = {
                    // Grant the reward now (on collect), then clear
                    pendingDailyStats?.let { s -> scope.launch { app.gameRepository.saveStats(s) } }
                    pendingDailyReward?.let {
                        com.pixelplay.dotsboxes.analytics.Analytics.dailyRewardClaimed(it.xpBonus)
                    }
                    pendingDailyStats  = null
                    pendingDailyReward = null
                },
                onCampaign         = { navController.navigate(Screen.LevelSelect.route) },
                onStore            = { navController.navigate(Screen.RewardStore.route) },
                onProfile          = { navController.navigate(Screen.Profile.route) },
                onOnline           = { navController.navigate(Screen.Login.route) },
                onFlashChallenge   = { navController.navigate(Screen.FlashChallenge.route) },
                onLeaderboard      = { navController.navigate(Screen.Leaderboard.route) },
                onNotifications    = { navController.navigate(Screen.FlashChallenge.route) },
                onOpenSpin         = { navController.navigate(Screen.LuckySpin.route) },
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

        composable(Screen.Profile.route) {
            val stats by app.gameRepository.observeStats()
                .collectAsState(initial = PlayerStats())
            ProfileScreen(
                playerStats = stats,
                onBack      = safePop,
                onRateUs    = { navController.navigate(Screen.RateUs.route) },
                onEditName  = { newName ->
                    scope.launch {
                        val current = app.gameRepository.observeStats().first()
                        app.gameRepository.saveStats(current.copy(playerName = newName))
                    }
                },
                onToggleSound = { enabled ->
                    scope.launch {
                        val current = app.gameRepository.observeStats().first()
                        app.gameRepository.saveStats(current.copy(soundEnabled = enabled))
                    }
                },
                onToggleVibration = { enabled ->
                    scope.launch {
                        val current = app.gameRepository.observeStats().first()
                        app.gameRepository.saveStats(current.copy(vibrationEnabled = enabled))
                    }
                }
            )
        }

        composable(Screen.RateUs.route) {
            RateUsScreen(onBack = safePop)
        }

        composable(Screen.RewardStore.route) {
            val stats by app.gameRepository.observeStats()
                .collectAsState(initial = PlayerStats())
            RewardStoreScreen(
                playerStats     = stats,
                onPurchaseSkin  = { skin, cost ->
                    scope.launch {
                        val current = app.gameRepository.observeStats().first()
                        app.gameRepository.saveStats(
                            current.purchaseSkin(skin, cost).withActiveSkin(skin)
                        )
                    }
                },
                onActivateSkin  = { skin ->
                    scope.launch {
                        val current = app.gameRepository.observeStats().first()
                        app.gameRepository.saveStats(current.withActiveSkin(skin))
                    }
                },
                onPurchaseHints = { hints, cost ->
                    scope.launch {
                        val current = app.gameRepository.observeStats().first()
                        app.gameRepository.saveStats(current.purchaseHintPack(hints, cost))
                    }
                },
                onAvatarChanged = { avatarId, cost ->
                    scope.launch {
                        val current = app.gameRepository.observeStats().first()
                        val updated = if (cost > 0 && !current.isAvatarUnlocked(avatarId)) {
                            current.purchaseAvatar(avatarId, cost)
                        } else {
                            current.setAvatar(avatarId)
                        }
                        app.gameRepository.saveStats(updated)
                    }
                },
                onBack = safePop
            )
        }

        // ── Login ─────────────────────────────────────────────────────────────
        composable(Screen.Login.route) {
            LoginScreen(
                onLoggedIn = {
                    navController.navigate(Screen.OnlineLobby.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                }
            )
        }

        // ── Online Lobby ──────────────────────────────────────────────────────
        composable(Screen.OnlineLobby.route) {
            val player = app.firebaseManager.currentOnlinePlayer()
            if (player == null) {
                LaunchedEffect(Unit) {
                    navController.navigate(Screen.Login.route) {
                        popUpTo(Screen.OnlineLobby.route) { inclusive = true }
                    }
                }
                return@composable
            }
            val onlineStats by app.gameRepository.observeStats().collectAsState(initial = PlayerStats())
            OnlineLobbyScreen(
                player      = player,
                playerStats = onlineStats,
                onGameReady = { code, isHost, myName, gridSize ->
                    navController.navigate(
                        Screen.OnlineGame.buildRoute(code, isHost, myName, gridSize)
                    )
                },
                onSignOut   = {
                    app.firebaseManager.signOut()
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.OnlineLobby.route) { inclusive = true }
                    }
                },
                onBack      = safePop
            )
        }

        // ── Online Game ───────────────────────────────────────────────────────
        composable(
            route = Screen.OnlineGame.route,
            arguments = listOf(
                navArgument("code")     { type = NavType.StringType },
                navArgument("isHost")   { type = NavType.BoolType },
                navArgument("myName")   { type = NavType.StringType },
                navArgument("gridSize") { type = NavType.IntType }
            )
        ) { entry ->
            val code     = entry.arguments!!.getString("code")!!
            val isHost   = entry.arguments!!.getBoolean("isHost")
            val myName   = entry.arguments!!.getString("myName")!!
            val gridSize = entry.arguments!!.getInt("gridSize")
            val factory  = OnlineGameViewModel.Factory(
                roomCode = code, isHost = isHost, myName = myName,
                gridSize = gridSize, firebase = app.firebaseManager,
                repository = app.gameRepository
            )
            val vm: OnlineGameViewModel = viewModel(factory = factory)
            OnlineGameScreen(
                viewModel      = vm,
                onNavigateBack = safePop
            )
        }

        // ── Flash Challenge ───────────────────────────────────────────────────
        composable(Screen.FlashChallenge.route) {
            val factory = FlashChallengeViewModel.Factory(app.gameRepository, app.firebaseManager)
            val vm: FlashChallengeViewModel = viewModel(factory = factory)
            FlashChallengeScreen(
                viewModel        = vm,
                onBack           = safePop,
                onOpenSpin       = { navController.navigate(Screen.LuckySpin.route) },
                onOpenLeaderboard = { navController.navigate(Screen.Leaderboard.route) }
            )
        }

        // ── Leaderboard ───────────────────────────────────────────────────────
        composable(Screen.Leaderboard.route) {
            val myUid = app.firebaseManager.currentUser?.uid ?: ""
            LeaderboardScreen(
                firebase = app.firebaseManager,
                myUid    = myUid,
                onBack   = safePop
            )
        }

        // ── Lucky Spin ────────────────────────────────────────────────────────
        composable(Screen.LuckySpin.route) {
            val stats by app.gameRepository.observeStats()
                .collectAsState(initial = com.pixelplay.dotsboxes.domain.model.PlayerStats())
            LuckySpinScreen(
                pendingSpins = stats.pendingSpins,
                onSpinUsed   = { prize ->
                    com.pixelplay.dotsboxes.analytics.Analytics.spinUsed(prize.name)
                    scope.launch {
                        val current = app.gameRepository.observeStats().first()
                        var updated = current.useSpin()
                        if (prize.coins > 0) updated = updated.earnDotCoins(prize.coins)
                        if (prize.xp > 0)    updated = updated.copy(xp = updated.xp + prize.xp)
                        app.gameRepository.saveStats(updated)
                    }
                },
                onBack = safePop
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
                onBack = safePop
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

            // Always allow next level (campaign 1-10 → then infinite 11, 12, ...)
            val onNextLevel: (() -> Unit)? = levelArg?.let { lvl ->
                {
                    val next = levelConfigFor(lvl + 1)
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
            }

            GameScreen(
                viewModel      = vm,
                config         = config,
                onNavigateBack = safePop,
                onOpenSpin     = { navController.navigate(Screen.LuckySpin.route) },
                onNextLevel    = onNextLevel
            )
        }
    }
    } // end Scaffold
}

// ── Bottom navigation ───────────────────────────────────────────────────────────

private data class BottomNavItem(
    val route: String,
    val label: String,
    val icon: ImageVector,
    val color: androidx.compose.ui.graphics.Color
)

private val BOTTOM_NAV_ITEMS = listOf(
    BottomNavItem(Screen.Home.route,        "Home",        Icons.Default.Home,       androidx.compose.ui.graphics.Color(0xFF5C6BC0)),
    BottomNavItem(Screen.Leaderboard.route, "Leaderboard", Icons.Default.EmojiEvents, androidx.compose.ui.graphics.Color(0xFFFFB300)),
    BottomNavItem(Screen.RewardStore.route, "Store",       Icons.Default.Storefront,  androidx.compose.ui.graphics.Color(0xFFEF6C00)),
    BottomNavItem(Screen.Profile.route,     "Profile",     Icons.Default.Person,      androidx.compose.ui.graphics.Color(0xFF26A69A))
)

private val BOTTOM_NAV_ROUTES = BOTTOM_NAV_ITEMS.map { it.route }.toSet()

@Composable
private fun AppBottomBar(navController: NavHostController, currentRoute: String?) {
    NavigationBar(containerColor = androidx.compose.ui.graphics.Color(0xFF12122E)) {
        BOTTOM_NAV_ITEMS.forEach { item ->
            NavigationBarItem(
                selected = currentRoute == item.route,
                onClick  = {
                    if (currentRoute != item.route) {
                        navController.navigate(item.route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState    = true
                        }
                    }
                },
                icon  = { Icon(item.icon, contentDescription = item.label) },
                label = { Text(item.label) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor   = androidx.compose.ui.graphics.Color.White,
                    selectedTextColor   = item.color,
                    indicatorColor      = item.color,
                    unselectedIconColor = item.color.copy(alpha = 0.55f),
                    unselectedTextColor = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.4f)
                )
            )
        }
    }
}
