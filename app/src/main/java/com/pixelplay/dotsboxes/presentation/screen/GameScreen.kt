package com.pixelplay.dotsboxes.presentation.screen

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.pixelplay.dotsboxes.DotsBoxesApp
import com.pixelplay.dotsboxes.domain.model.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.pixelplay.dotsboxes.presentation.components.GameBoard
import com.pixelplay.dotsboxes.presentation.components.ScoreBoard
import com.pixelplay.dotsboxes.presentation.theme.*
import com.pixelplay.dotsboxes.presentation.util.ShareCardGenerator
import com.pixelplay.dotsboxes.presentation.viewmodel.GameConfig
import com.pixelplay.dotsboxes.presentation.viewmodel.GameViewModel
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameScreen(
    viewModel: GameViewModel,
    config: GameConfig,
    onNavigateBack: () -> Unit,
    onOpenSpin: () -> Unit = {},
    onNextLevel: (() -> Unit)? = null
) {
    val ui by viewModel.uiState.collectAsState()
    val isDark = isSystemInDarkTheme()
    val haptic = LocalHapticFeedback.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope2 = rememberCoroutineScope()
    var showThemeMenu by remember { mutableStateOf(false) }

    // Skin-aware player colors (same logic as GameBoard)
    val (p1Color, p2Color) = when (ui.playerStats.activeSkinEnum) {
        BoardSkin.DEFAULT  -> Player1Blue       to Player2Orange
        BoardSkin.FIRE     -> Color(0xFFFF1744) to Color(0xFFFFEA00)
        BoardSkin.GOLDEN   -> Color(0xFFFF8F00) to Color(0xFFE040FB)
        BoardSkin.CONTRAST -> Color(0xFF40C4FF) to Color(0xFFFF6D00)
    }

    // ── Top-bar coin counter (animation target on win) ────────────────────────
    val targetCoins = ui.playerStats.dotCoins
    val baseCoins   = (targetCoins - ui.coinsToCollect).coerceAtLeast(0)
    val coinAnim    = remember { Animatable(targetCoins.toFloat()) }
    LaunchedEffect(ui.showWinCoinBurst, ui.coinsToCollect) {
        if (ui.showWinCoinBurst && ui.coinsToCollect > 0) {
            coinAnim.snapTo(baseCoins.toFloat())
            delay(550L)                       // let coins travel toward counter first
            coinAnim.animateTo(targetCoins.toFloat(), tween(850, easing = FastOutSlowInEasing))
        } else {
            coinAnim.snapTo(targetCoins.toFloat())
        }
    }
    val displayedCoins = coinAnim.value.roundToInt()
    // Pulse the pill while counting up
    val counting  = ui.showWinCoinBurst && displayedCoins < targetCoins
    val coinPulse by animateFloatAsState(
        targetValue   = if (counting) 1.18f else 1f,
        animationSpec  = tween(180),
        label          = "coinPulse"
    )

    LaunchedEffect(config) { viewModel.startNewGame(config) }
    BackHandler { onNavigateBack() }

    val bgGradient = if (isDark)
        Brush.verticalGradient(listOf(Color(0xFF0D0D2B), Color(0xFF141430)))
    else
        Brush.verticalGradient(listOf(Color(0xFFF2F0FF), Color(0xFFE8E4FF)))

    // Haptic-wrapped line tap (respects vibration toggle)
    val onLineTapWithHaptic: (LineId) -> Unit = { line ->
        if (ui.isVibrationEnabled) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        viewModel.onLineTapped(line)
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    // 💡 Hint — all PvA modes, human's turn
                    if (ui.gameState.gameMode == GameMode.PVA &&
                        !ui.gameState.isGameOver) {
                        val coins = ui.playerStats.hintCoins
                        TextButton(
                            onClick = viewModel::useHint,
                            colors  = ButtonDefaults.textButtonColors(
                                contentColor = p1Color
                            )
                        ) {
                            Text("💡 $coins", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                    // 🔄 Restart
                    IconButton(onClick = viewModel::restartGame) {
                        Icon(Icons.Default.Refresh, "Restart")
                    }
                    // 🎨 Theme (board skin) picker
                    Box {
                        IconButton(onClick = { showThemeMenu = true }) {
                            Text("🎨", fontSize = 18.sp)
                        }
                        val unlockedSkins = BoardSkin.entries.filter {
                            it == BoardSkin.CONTRAST || ui.playerStats.unlockedSkins.contains(it.name)
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
                    // 🪙 Coin counter — animation target
                    Surface(
                        shape  = RoundedCornerShape(50),
                        color  = Color(0xFFFFD700),
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .scale(coinPulse)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Text("🪙", fontSize = 14.sp)
                            Text(
                                "$displayedCoins",
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    color      = Color(0xFF3E2000)
                                )
                            )
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
                ScoreBoard(state = ui.gameState, isAiThinking = ui.isAiThinking, p1Color = p1Color, p2Color = p2Color)

                // ── Move timer (Level 8 — 15s countdown) ─────────────────────
                if (ui.moveTimerSeconds != null && !ui.gameState.isGameOver) {
                    MoveTimerBar(seconds = ui.moveTimerSeconds!!, limit = 15)
                }

                // ── Tutorial banner (Level 1) ─────────────────────────────────
                if (config.levelNumber == 1 &&
                    !ui.gameState.isGameOver &&
                    ui.gameState.currentPlayer == PlayerType.ONE) {
                    TutorialBanner()
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    GameBoard(
                        state      = ui.gameState,
                        lastLine   = ui.lastLine,
                        onLineTap  = onLineTapWithHaptic,
                        modifier   = Modifier.fillMaxWidth(),
                        hintMove          = ui.hintMove,
                        activeSkin        = ui.playerStats.activeSkinEnum,
                        lastMoveHighlight = ui.lastMoveHighlight,
                        specialBoxes      = ui.specialBoxes
                    )
                }

                AnimatedVisibility(
                    visible = !ui.gameState.isGameOver,
                    enter   = fadeIn() + slideInVertically(),
                    exit    = fadeOut()
                ) {
                    PulsingTurnIndicator(
                        current      = ui.gameState.currentPlayer,
                        isAiThinking = ui.isAiThinking,
                        state        = ui.gameState,
                        p1Color      = p1Color,
                        p2Color      = p2Color
                    )
                }
            }

            // Phase 1: coins stream from board up to the top-right coin counter
            if (ui.showWinCoinBurst && ui.coinsToCollect > 0) {
                CoinStreamOverlay(coinCount = ui.coinsToCollect)
            }

            if (ui.gameState.isGameOver && !ui.showXpScreen && !ui.showWinCoinBurst) {
                WinDialog(
                    gameState      = ui.gameState,
                    playerStats    = ui.playerStats,
                    playerJustLost = ui.playerJustLost,
                    levelNumber    = ui.levelNumber,
                    p1Color        = p1Color,
                    p2Color        = p2Color,
                    onRestart      = viewModel::restartGame,
                    onNextLevel    = onNextLevel,
                    onMainMenu     = onNavigateBack,
                    longGame       = viewModel.wasLongGame()
                )
            }

            if (ui.showEarnHintsDialog) {
                val ctx = LocalContext.current
                EarnHintsDialog(
                    onWatchAd    = viewModel::earnHintsFromAd,
                    onShareFriend = { viewModel.earnHintsFromShare(ctx) },
                    onDismiss    = viewModel::dismissEarnHintsDialog
                )
            }

            // 👑/🎁 special box reward popup
            ui.specialReward?.let { reward ->
                SpecialRewardDialog(
                    event     = reward,
                    onDismiss = viewModel::dismissSpecialReward
                )
            }

            // 🎯 Daily Mission complete → free spin popup
            if (ui.dailyMissionSpinEarned) {
                DailyMissionSpinDialog(
                    onSpin    = { viewModel.dismissDailyMissionSpin(); onOpenSpin() },
                    onLater   = viewModel::dismissDailyMissionSpin
                )
            }

            // XP result screen (shown after game over, before WinDialog)
            if (ui.showXpScreen) {
                XpResultScreen(
                    gameState    = ui.gameState,
                    xpEarned     = ui.xpEarned,
                    coinsEarned  = ui.coinsToCollect,
                    playerStats  = ui.playerStats,
                    onContinue   = viewModel::dismissXpScreen,
                    onDoubleCoins = { viewModel.addBonusCoins(ui.coinsToCollect) }
                )
            }
        }
    }
}

// ── Move timer bar ────────────────────────────────────────────────────────────

@Composable
private fun MoveTimerBar(seconds: Int, limit: Int) {
    val fraction = (seconds.toFloat() / limit).coerceIn(0f, 1f)
    val color = when {
        fraction > 0.6f -> Color(0xFF4CAF50)
        fraction > 0.3f -> Color(0xFFFF9800)
        else            -> Color(0xFFF44336)
    }
    // Pulse red when almost out of time
    val pulse by rememberInfiniteTransition(label = "timer").animateFloat(
        initialValue  = 1f,
        targetValue   = if (seconds <= 5) 0.5f else 1f,
        animationSpec = infiniteRepeatable(tween(400), RepeatMode.Reverse),
        label         = "timerPulse"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            "⏱ $seconds",
            style = MaterialTheme.typography.labelLarge.copy(
                color      = color.copy(alpha = pulse),
                fontWeight = FontWeight.ExtraBold
            ),
            modifier = Modifier.width(52.dp)
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(8.dp)
                .clip(RoundedCornerShape(50))
                .background(color.copy(alpha = 0.2f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction)
                    .fillMaxHeight()
                    .background(
                        Brush.horizontalGradient(listOf(color, color.copy(0.7f))),
                        RoundedCornerShape(50)
                    )
            )
        }
    }
}

// ── Coin stream — coins flow from the board up to the top-right counter ───────

private const val BOARD_PAD_FRAC = 0.08f

@Composable
internal fun CoinStreamOverlay(coinCount: Int) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()

        // Target = top-right, where the app-bar coin pill sits (just above overlay top)
        val targetX = w - with(LocalDensity.current) { 34.dp.toPx() }
        val targetY = with(LocalDensity.current) { (-14).dp.toPx() }

        // Board region (roughly centered square) — coins originate from here
        val boardSize = minOf(w, h * 0.55f)
        val boardLeft = (w - boardSize) / 2f
        val boardTop  = h * 0.22f

        val particles = minOf(coinCount, 18).coerceAtLeast(6)
        repeat(particles) { i ->
            key(i) {
                val rand = remember(i) { kotlin.random.Random(i * 91711 + 7) }
                val sx = boardLeft + rand.nextFloat() * boardSize
                val sy = boardTop  + rand.nextFloat() * boardSize
                CoinStreamParticle(
                    startX  = sx,
                    startY  = sy,
                    targetX = targetX,
                    targetY = targetY,
                    seed    = i,
                    startMs = 60L + i * 70L      // staggered → tube/stream feel
                )
            }
        }
    }
}

@Composable
private fun CoinStreamParticle(
    startX: Float, startY: Float,
    targetX: Float, targetY: Float,
    seed: Int,
    startMs: Long
) {
    val density = LocalDensity.current
    val rand    = remember { kotlin.random.Random(seed) }

    // Control point for a curved (arc) path — bulges upward & sideways
    val ctrlX = (startX + targetX) / 2f + (rand.nextFloat() - 0.5f) * startX * 0.5f
    val ctrlY = minOf(startY, targetY) - startX * (0.25f + rand.nextFloat() * 0.25f)

    val progress = remember { Animatable(0f) }
    val alpha    = remember { Animatable(0f) }
    val scale    = remember { Animatable(0.3f) }
    val spin     = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        delay(startMs)
        launch { alpha.animateTo(1f, tween(120)) }
        launch { scale.animateTo(1f, tween(180, easing = EaseOutBack)) }
        launch { spin.animateTo(720f, tween(720)) }
        progress.animateTo(1f, tween(720, easing = FastOutSlowInEasing))
        // shrink into the counter as it lands
        launch { scale.animateTo(0.2f, tween(140)) }
        alpha.animateTo(0f, tween(140))
    }

    // Quadratic bezier: start → ctrl → target
    val t  = progress.value
    val mt = 1f - t
    val cx = mt * mt * startX + 2 * mt * t * ctrlX + t * t * targetX
    val cy = mt * mt * startY + 2 * mt * t * ctrlY + t * t * targetY

    Box(Modifier.fillMaxSize()) {
        Text(
            "🪙",
            fontSize = 20.sp,
            modifier = Modifier
                .offset(
                    x = with(density) { (cx - 10.dp.toPx()).toDp() },
                    y = with(density) { (cy - 10.dp.toPx()).toDp() }
                )
                .graphicsLayer {
                    this.alpha = alpha.value
                    scaleX = scale.value
                    scaleY = scale.value
                    rotationZ = spin.value
                }
        )
    }
}

// ── Tutorial banner (Level 1) ─────────────────────────────────────────────────

@Composable
private fun TutorialBanner() {
    Card(
        shape  = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF1A237E).copy(alpha = 0.75f)
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("💡", fontSize = 18.sp)
            Text(
                "Golden line = best move! Watch & learn.",
                style = MaterialTheme.typography.bodySmall.copy(
                    color = Color.White,
                    fontWeight = FontWeight.Medium
                )
            )
        }
    }
}

// ── Pulsing turn indicator ────────────────────────────────────────────────────

@Composable
private fun PulsingTurnIndicator(
    current: PlayerType,
    isAiThinking: Boolean,
    state: GameState,
    p1Color: Color = Player1Blue,
    p2Color: Color = Player2Orange
) {
    val color = if (current == PlayerType.ONE) p1Color else p2Color
    val name  = state.playerName(current)

    // Pulse scale animation
    val scale by rememberInfiniteTransition(label = "turnPulse").animateFloat(
        initialValue  = 1f,
        targetValue   = 1.06f,
        animationSpec = infiniteRepeatable(
            tween(700, easing = EaseInOutSine), RepeatMode.Reverse
        ),
        label = "scale"
    )

    Card(
        modifier = Modifier.scale(if (!isAiThinking) scale else 1f),
        shape    = RoundedCornerShape(50),
        colors   = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.18f)),
        border   = CardDefaults.outlinedCardBorder()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 22.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Pulsing dot indicator
            val dotScale by rememberInfiniteTransition(label = "dot").animateFloat(
                initialValue  = 0.7f,
                targetValue   = 1.3f,
                animationSpec = infiniteRepeatable(tween(500), RepeatMode.Reverse),
                label         = "dotScale"
            )
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .scale(dotScale)
                    .background(color, androidx.compose.foundation.shape.CircleShape)
            )

            if (isAiThinking) {
                Text("AI is thinking…", style = MaterialTheme.typography.labelLarge)
                CircularProgressIndicator(
                    modifier    = Modifier.size(14.dp),
                    strokeWidth = 2.dp,
                    color       = color
                )
            } else {
                Text(
                    if (name.equals("You", ignoreCase = true)) "Your turn" else "$name's turn",
                    style = MaterialTheme.typography.labelLarge.copy(color = color)
                )
            }
        }
    }
}

// ── Win dialog ────────────────────────────────────────────────────────────────

@Composable
private fun WinDialog(
    gameState: GameState,
    playerStats: PlayerStats,
    playerJustLost: Boolean,
    levelNumber: Int?,
    p1Color: Color = Player1Blue,
    p2Color: Color = Player2Orange,
    onRestart: () -> Unit,
    onNextLevel: (() -> Unit)?,
    onMainMenu: () -> Unit,
    longGame: Boolean = true
) {
    val winner         = gameState.winner
    val isTie          = winner == null
    val winnerName     = winner?.let { gameState.playerName(it) }
    val playerWon      = winner == PlayerType.ONE
    val isCampaign     = levelNumber != null
    val isInfiniteLevel = levelNumber != null && levelNumber > CAMPAIGN_LEVELS.size
    val isGatewayLevel  = levelNumber == CAMPAIGN_LEVELS.size   // Level 10 → unlocks infinite
    val context        = LocalContext.current
    val app            = context.applicationContext as DotsBoxesApp
    val activity       = context as? android.app.Activity

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
                Text(if (isTie) "🤝" else "🏆", fontSize = 56.sp)

                Text(
                    if (isTie) "It's a Tie!" else "$winnerName\nWins!",
                    style = MaterialTheme.typography.headlineLarge.copy(
                        fontWeight = FontWeight.ExtraBold,
                        textAlign  = TextAlign.Center
                    )
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    ScorePill(gameState.p1Name, gameState.p1Score, p1Color)
                    ScorePill(gameState.p2Name, gameState.p2Score, p2Color)
                }

                // Lifetime stats mini strip
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape    = RoundedCornerShape(16.dp),
                    colors   = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        StatMini("W", "${playerStats.wins}", Player1Blue)
                        StatMini("L", "${playerStats.losses}", Player2Orange)
                        StatMini("🔥", "${playerStats.currentStreak}", Color(0xFFFFD54F))
                        StatMini("🪙", "${playerStats.dotCoins}", Color(0xFFFFD700))
                    }
                }

                // Primary action button
                if (isCampaign && playerWon && onNextLevel != null) {
                    // Campaign / Infinite win — Next Level button
                    Button(
                        onClick        = onNextLevel,
                        modifier       = Modifier.fillMaxWidth(),
                        shape          = RoundedCornerShape(50),
                        colors         = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .background(
                                    when {
                                        isInfiniteLevel -> Brush.horizontalGradient(listOf(Color(0xFF00BCD4), Color(0xFF7C4DFF)))
                                        isGatewayLevel  -> Brush.horizontalGradient(listOf(Color(0xFFFFD700), Color(0xFFFF6F00)))
                                        else            -> Brush.horizontalGradient(listOf(Color(0xFFFFD700), Color(0xFFFF9800)))
                                    },
                                    RoundedCornerShape(50)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                when {
                                    isInfiniteLevel -> "♾️  Next Infinity Level"
                                    isGatewayLevel  -> "👑  Enter Infinite Mode!"
                                    else            -> "Next Level  →"
                                },
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontSize = 16.sp,
                                    color    = if (isInfiniteLevel) Color.White else Color(0xFF3E2000),
                                    fontWeight = FontWeight.ExtraBold
                                )
                            )
                        }
                    }
                } else {
                    // Custom game or campaign loss — Play Again / Revenge
                    Button(
                        onClick  = onRestart,
                        modifier = Modifier.fillMaxWidth(),
                        shape    = RoundedCornerShape(50),
                        colors   = ButtonDefaults.buttonColors(
                            containerColor = Color.Transparent,
                            contentColor   = Color.White
                        ),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .background(
                                    if (playerJustLost)
                                        Brush.horizontalGradient(listOf(Color(0xFFFF3D00), Color(0xFFFF9800)))
                                    else
                                        Brush.horizontalGradient(listOf(Purple40, Indigo40)),
                                    RoundedCornerShape(50)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                if (isCampaign && !playerWon) "🔄  Retry Level"
                                else if (playerJustLost) "🔥  Revenge!"
                                else "⚡  Play Again",
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontSize = 16.sp, color = Color.White, fontWeight = FontWeight.Bold
                                )
                            )
                        }
                    }
                }

                // Share button
                OutlinedButton(
                    onClick  = { ShareCardGenerator.share(context, gameState, playerStats) },
                    modifier = Modifier.fillMaxWidth(),
                    shape    = RoundedCornerShape(50),
                    colors   = ButtonDefaults.outlinedButtonColors(
                        contentColor = Color(0xFF25D366)
                    ),
                    border   = androidx.compose.foundation.BorderStroke(
                        1.5.dp, Color(0xFF25D366).copy(alpha = 0.6f)
                    )
                ) {
                    Icon(Icons.Default.Share, contentDescription = null,
                        modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Share Score Card")
                }

                OutlinedButton(
                    onClick  = {
                        if (activity != null) {
                            app.interstitialAd.onGameOver(activity, longGame) { onMainMenu() }
                        } else {
                            onMainMenu()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape    = RoundedCornerShape(50)
                ) {
                    Text("Main Menu")
                }
            }
        }
    }
}

@Composable
private fun StatMini(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleMedium.copy(
            fontWeight = FontWeight.ExtraBold, color = color
        ))
        Text(label, style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
    }
}

@Composable
private fun ScorePill(name: String, score: Int, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            "$score",
            style = MaterialTheme.typography.displayMedium.copy(
                color = color, fontWeight = FontWeight.ExtraBold
            )
        )
        Text(name, style = MaterialTheme.typography.bodyMedium)
    }
}

// ── Earn Hints dialog ─────────────────────────────────────────────────────────

@Composable
private fun EarnHintsDialog(
    onWatchAd: () -> Unit,
    onShareFriend: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
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
                Text("💡", fontSize = 48.sp)
                Text(
                    "Out of Hints!",
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold)
                )
                Text(
                    "Earn hint coins to see the best move on Hard mode",
                    style     = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color     = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(0.2f))

                // Option 1 — Watch ad
                EarnHintOption(
                    emoji   = "📺",
                    title   = "Watch a short ad",
                    reward  = "+2 coins",
                    color   = Color(0xFF64B5F6),
                    onClick = onWatchAd
                )

                // Option 2 — Share to friends
                EarnHintOption(
                    emoji   = "🤝",
                    title   = "Invite a friend",
                    reward  = "+3 coins",
                    color   = Color(0xFF25D366),
                    onClick = onShareFriend
                )

                TextButton(onClick = onDismiss) {
                    Text("Maybe later", color = MaterialTheme.colorScheme.onSurface.copy(0.4f))
                }
            }
        }
    }
}

@Composable
private fun EarnHintOption(
    emoji: String, title: String, reward: String, color: Color, onClick: () -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(14.dp),
        border   = androidx.compose.foundation.BorderStroke(1.5.dp, color.copy(alpha = 0.5f)),
        colors   = ButtonDefaults.outlinedButtonColors(contentColor = color)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(emoji, fontSize = 24.sp)
                Text(title, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium))
            }
            Text(
                reward,
                style = MaterialTheme.typography.labelLarge.copy(
                    color = color, fontWeight = FontWeight.ExtraBold
                )
            )
        }
    }
}


@Composable
internal fun SpecialRewardDialog(
    event: com.pixelplay.dotsboxes.domain.model.SpecialRewardEvent,
    onDismiss: () -> Unit
) {
    val isCrown = event is com.pixelplay.dotsboxes.domain.model.SpecialRewardEvent.Crown
    val emoji   = when (event) {
        is com.pixelplay.dotsboxes.domain.model.SpecialRewardEvent.Crown   -> "👑"
        is com.pixelplay.dotsboxes.domain.model.SpecialRewardEvent.Mystery -> event.reward.emoji
    }
    val title = if (isCrown) "Crown Box!" else "Mystery Box!"
    val line  = when (event) {
        is com.pixelplay.dotsboxes.domain.model.SpecialRewardEvent.Crown   -> "🎡 A Lucky Spin has been added!"
        is com.pixelplay.dotsboxes.domain.model.SpecialRewardEvent.Mystery -> event.reward.label
    }
    val accent = if (isCrown) Color(0xFFFFD700) else Color(0xFFE040FB)

    // Pop-in scale animation
    val scale = remember { Animatable(0.6f) }
    LaunchedEffect(Unit) { scale.animateTo(1f, tween(260, easing = EaseOutBack)) }

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .scale(scale.value)
                .clip(RoundedCornerShape(24.dp))
                .background(Brush.verticalGradient(listOf(Color(0xFF1A0040), Color(0xFF0D1030))))
                .border(2.dp, accent.copy(0.6f), RoundedCornerShape(24.dp))
                .padding(28.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(emoji, fontSize = 64.sp)
                Text(
                    title,
                    style = MaterialTheme.typography.headlineSmall.copy(
                        color = accent, fontWeight = FontWeight.ExtraBold
                    )
                )
                Text(
                    line,
                    style = MaterialTheme.typography.titleMedium.copy(color = Color.White),
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(6.dp))
                Button(
                    onClick = onDismiss,
                    shape   = RoundedCornerShape(50),
                    colors  = ButtonDefaults.buttonColors(containerColor = accent)
                ) {
                    Text(
                        if (isCrown) "Awesome!" else "Collect",
                        color = Color(0xFF1A0040),
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun DailyMissionSpinDialog(
    onSpin: () -> Unit,
    onLater: () -> Unit
) {
    val accent = Color(0xFF4CAF50)
    val scale = remember { Animatable(0.6f) }
    LaunchedEffect(Unit) { scale.animateTo(1f, tween(260, easing = EaseOutBack)) }

    Dialog(onDismissRequest = onLater) {
        Box(
            modifier = Modifier
                .scale(scale.value)
                .clip(RoundedCornerShape(24.dp))
                .background(Brush.verticalGradient(listOf(Color(0xFF10331A), Color(0xFF0D1030))))
                .border(2.dp, accent.copy(0.6f), RoundedCornerShape(24.dp))
                .padding(28.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("🎯", fontSize = 60.sp)
                Text(
                    "Daily Mission Complete!",
                    style = MaterialTheme.typography.headlineSmall.copy(
                        color = accent, fontWeight = FontWeight.ExtraBold
                    ),
                    textAlign = TextAlign.Center
                )
                Text(
                    "You won 2 games today 🏆\n🎡 A free Lucky Spin is ready!",
                    style = MaterialTheme.typography.titleMedium.copy(color = Color.White),
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(6.dp))
                Button(
                    onClick = onSpin,
                    shape   = RoundedCornerShape(50),
                    colors  = ButtonDefaults.buttonColors(containerColor = accent)
                ) {
                    Text(
                        "🎡  Spin Now",
                        color = Color.White,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.padding(horizontal = 14.dp)
                    )
                }
                TextButton(onClick = onLater) {
                    Text("Later", color = Color.White.copy(0.6f))
                }
            }
        }
    }
}

private fun GameMode.displayName() = when (this) {
    GameMode.PVP -> "PvP"
    GameMode.PVA -> "vs AI"
}
