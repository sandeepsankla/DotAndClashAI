package com.pixelplay.dotsboxes.presentation.screen

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.pixelplay.dotsboxes.DotsBoxesApp
import com.pixelplay.dotsboxes.domain.model.PlayerType
import com.pixelplay.dotsboxes.domain.model.RoomStatus
import com.pixelplay.dotsboxes.presentation.ads.BannerAdView
import com.pixelplay.dotsboxes.presentation.components.GameBoard
import com.pixelplay.dotsboxes.presentation.components.ScoreBoard
import com.pixelplay.dotsboxes.presentation.theme.Player1Blue
import com.pixelplay.dotsboxes.presentation.theme.Player2Orange
import com.pixelplay.dotsboxes.presentation.theme.Purple40
import com.pixelplay.dotsboxes.presentation.viewmodel.OnlineConnectionStatus
import com.pixelplay.dotsboxes.presentation.viewmodel.OnlineGameViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnlineGameScreen(
    viewModel: OnlineGameViewModel,
    onNavigateBack: () -> Unit
) {
    val ui by viewModel.uiState.collectAsState()
    val isDark = isSystemInDarkTheme()
    val context = LocalContext.current
    val app = context.applicationContext as DotsBoxesApp

    var showLeaveDialog by remember { mutableStateOf(false) }
    var matchAdShown by remember { mutableStateOf(false) }
    var showThemeMenu by remember { mutableStateOf(false) }

    BackHandler { showLeaveDialog = true }

    // Show the full-screen (video) ad AFTER the match ends — a safe break point.
    // (Showing it at match START paused the activity and dropped the Firebase
    //  connection, which fired onDisconnect → room wrongly marked ABANDONED.)
    LaunchedEffect(ui.gameState.isGameOver, ui.connectionStatus) {
        val ended = ui.gameState.isGameOver ||
                    ui.connectionStatus == OnlineConnectionStatus.OPPONENT_LEFT
        if (ended && !matchAdShown) {
            matchAdShown = true
            kotlinx.coroutines.delay(1500L)   // let the result overlay appear first
            (context as? android.app.Activity)?.let { activity ->
                app.interstitialAd.showOnlineEnd(activity)
            }
        }
    }

    val bgGradient = if (isDark)
        Brush.verticalGradient(listOf(Color(0xFF0D0D2B), Color(0xFF141430)))
    else
        Brush.verticalGradient(listOf(Color(0xFFF2F0FF), Color(0xFFE8E4FF)))

    // Opponent disconnected → auto-show result
    LaunchedEffect(ui.connectionStatus) {
        if (ui.connectionStatus == OnlineConnectionStatus.OPPONENT_LEFT) {
            // Dialog will show automatically via state
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "🌐 Online · ${ui.roomCode}",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            if (ui.isMyTurn) "Your turn" else "${ui.opponentName}'s turn",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (ui.isMyTurn) Color(0xFF4CAF50)
                                    else MaterialTheme.colorScheme.onSurface.copy(0.5f)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { showLeaveDialog = true }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Leave")
                    }
                },
                actions = {
                    // 🎨 Board theme picker
                    Box {
                        IconButton(onClick = { showThemeMenu = true }) {
                            Text("🎨", fontSize = 18.sp)
                        }
                        val unlockedSkins = com.pixelplay.dotsboxes.domain.model.BoardSkin.entries.filter {
                            it == com.pixelplay.dotsboxes.domain.model.BoardSkin.CONTRAST ||
                                ui.playerStats.unlockedSkins.contains(it.name)
                        }
                        DropdownMenu(
                            expanded = showThemeMenu,
                            onDismissRequest = { showThemeMenu = false }
                        ) {
                            unlockedSkins.forEach { skin ->
                                val active = ui.playerStats.activeSkinEnum == skin
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            "${skin.emoji} ${skin.displayName}" + if (active) "  ✓" else "",
                                            fontWeight = if (active) FontWeight.Bold else FontWeight.Normal
                                        )
                                    },
                                    onClick = {
                                        viewModel.setSkin(skin)
                                        showThemeMenu = false
                                    }
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        containerColor = Color.Transparent
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(bgGradient)
                .padding(padding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Waiting for guest to join
                if (ui.roomStatus == RoomStatus.WAITING) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Color(0xFF1A237E).copy(0.5f),
                                RoundedCornerShape(12.dp)
                            )
                            .padding(12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "⏳  Waiting for opponent to join…",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White
                        )
                    }
                }

                // Connection status banner
                if (ui.connectionStatus == OnlineConnectionStatus.RECONNECTING) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }

                ScoreBoard(state = ui.gameState, isAiThinking = false)

                // Online turn indicator — shows role clearly
                OnlineTurnBanner(
                    isMyTurn     = ui.isMyTurn,
                    myRole       = ui.myRole,
                    opponentName = ui.opponentName,
                    roomStatus   = ui.roomStatus
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    GameBoard(
                        state      = ui.gameState,
                        lastLine          = ui.lastLine,
                        onLineTap  = { line ->
                            if (ui.isMyTurn) viewModel.onLineTapped(line)
                        },
                        modifier   = Modifier.fillMaxWidth(),
                        hintMove   = null,
                        activeSkin = ui.playerStats.activeSkinEnum,
                        lastMoveHighlight = ui.lastMoveHighlight,
                        specialBoxes = ui.specialBoxes
                    )
                }
            }

            // Coin stream overlay
            if (ui.showWinCoinBurst && ui.coinsEarned > 0) {
                CoinStreamOverlay(coinCount = ui.coinsEarned)
            }

            // 👑/🎁 special box reward popup
            ui.specialReward?.let { reward ->
                SpecialRewardDialog(
                    event     = reward,
                    onDismiss = viewModel::dismissSpecialReward
                )
            }

            // XP screen
            if (ui.showXpScreen) {
                XpResultScreen(
                    gameState    = ui.gameState,
                    xpEarned     = ui.xpEarned,
                    coinsEarned  = ui.coinsEarned,
                    playerStats  = ui.playerStats,
                    onContinue   = {
                        viewModel.dismissXpScreen()
                        onNavigateBack()
                    },
                    onDoubleCoins = { viewModel.addBonusCoins(ui.coinsEarned) }
                )
            }

            // Opponent left — show result dialog (no XP animation needed)
            if (!ui.showXpScreen && !ui.showWinCoinBurst &&
                (ui.connectionStatus == OnlineConnectionStatus.OPPONENT_LEFT)) {
                OnlineResultDialog(
                    myRole       = ui.myRole,
                    winner       = ui.gameState.winner,
                    p1Name       = ui.gameState.p1Name,
                    p2Name       = ui.gameState.p2Name,
                    p1Score      = ui.gameState.p1Score,
                    p2Score      = ui.gameState.p2Score,
                    opponentLeft = true,
                    onMainMenu   = onNavigateBack
                )
            }

            // Leave confirmation dialog
            if (showLeaveDialog) {
                AlertDialog(
                    onDismissRequest = { showLeaveDialog = false },
                    title   = { Text("Leave Game?") },
                    text    = { Text("Your opponent will win if you leave mid-game.") },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                viewModel.leaveRoom()
                                onNavigateBack()
                            },
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            )
                        ) { Text("Leave") }
                    },
                    dismissButton = {
                        TextButton(onClick = { showLeaveDialog = false }) {
                            Text("Stay")
                        }
                    }
                )
            }

            // Error snackbar
            ui.errorMessage?.let { msg ->
                LaunchedEffect(msg) {
                    kotlinx.coroutines.delay(3000L)
                    viewModel.clearError()
                }
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp)
                        .background(
                            MaterialTheme.colorScheme.errorContainer,
                            RoundedCornerShape(12.dp)
                        )
                        .padding(12.dp)
                ) {
                    Text(msg, color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

// ── Online turn banner ────────────────────────────────────────────────────────

@Composable
private fun OnlineTurnBanner(
    isMyTurn: Boolean,
    myRole: PlayerType,
    opponentName: String,
    roomStatus: RoomStatus
) {
    if (roomStatus != RoomStatus.PLAYING) return

    val color  = if (isMyTurn) Color(0xFF4CAF50) else Player2Orange
    val text   = if (isMyTurn) "Your Turn — tap a line!" else "$opponentName is thinking…"
    val scale by rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue  = 1f,
        targetValue   = if (isMyTurn) 1.03f else 1f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label         = "bannerScale"
    )

    Card(
        modifier = Modifier.scale(scale),
        shape    = RoundedCornerShape(50),
        colors   = CardDefaults.cardColors(
            containerColor = color.copy(alpha = 0.15f)
        ),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(color)
            )
            Text(
                text,
                style = MaterialTheme.typography.labelLarge.copy(color = color)
            )
            if (!isMyTurn) {
                CircularProgressIndicator(
                    modifier    = Modifier.size(14.dp),
                    strokeWidth = 2.dp,
                    color       = color
                )
            }
        }
    }
}

// ── Result dialog ─────────────────────────────────────────────────────────────

@Composable
private fun OnlineResultDialog(
    myRole: PlayerType,
    winner: PlayerType?,
    p1Name: String,
    p2Name: String,
    p1Score: Int,
    p2Score: Int,
    opponentLeft: Boolean,
    onMainMenu: () -> Unit
) {
    val iWon = when {
        opponentLeft                     -> true
        winner == null                   -> false
        winner == myRole                 -> true
        else                             -> false
    }
    val isTie   = !opponentLeft && winner == null
    val emoji   = when { opponentLeft -> "🏃"; isTie -> "🤝"; iWon -> "🏆"; else -> "😔" }
    val title   = when { opponentLeft -> "Opponent Left!"; isTie -> "It's a Tie!"; iWon -> "You Win!"; else -> "You Lose!" }
    val color   = when { isTie -> MaterialTheme.colorScheme.primary; iWon -> Color(0xFFFFD700); else -> Player2Orange }

    Dialog(onDismissRequest = {}) {
        Card(
            shape     = RoundedCornerShape(28.dp),
            colors    = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(emoji, fontSize = 56.sp)
                Text(
                    title,
                    style = MaterialTheme.typography.headlineLarge.copy(
                        fontWeight = FontWeight.ExtraBold,
                        color      = color,
                        textAlign  = TextAlign.Center
                    )
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("$p1Score", style = MaterialTheme.typography.displayMedium.copy(
                            color = Player1Blue, fontWeight = FontWeight.ExtraBold))
                        Text(p1Name, style = MaterialTheme.typography.bodyMedium)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("$p2Score", style = MaterialTheme.typography.displayMedium.copy(
                            color = Player2Orange, fontWeight = FontWeight.ExtraBold))
                        Text(p2Name, style = MaterialTheme.typography.bodyMedium)
                    }
                }

                Button(
                    onClick        = onMainMenu,
                    modifier       = Modifier.fillMaxWidth().height(50.dp),
                    shape          = RoundedCornerShape(50),
                    colors         = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.horizontalGradient(listOf(Player1Blue, Purple40)),
                                RoundedCornerShape(50)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("🏠  Main Menu", style = MaterialTheme.typography.labelLarge.copy(
                            color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp))
                    }
                }
            }
        }
    }
}
