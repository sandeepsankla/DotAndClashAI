package com.pixelplay.dotsboxes.presentation.screen

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.pixelplay.dotsboxes.domain.model.Difficulty
import com.pixelplay.dotsboxes.domain.model.GameMode
import com.pixelplay.dotsboxes.domain.model.GameState
import com.pixelplay.dotsboxes.domain.model.LineId
import com.pixelplay.dotsboxes.domain.model.PlayerStats
import com.pixelplay.dotsboxes.domain.model.PlayerType
import com.pixelplay.dotsboxes.presentation.components.GameBoard
import com.pixelplay.dotsboxes.presentation.components.ScoreBoard
import com.pixelplay.dotsboxes.presentation.theme.*
import com.pixelplay.dotsboxes.presentation.util.ShareCardGenerator
import com.pixelplay.dotsboxes.presentation.viewmodel.GameConfig
import com.pixelplay.dotsboxes.presentation.viewmodel.GameViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameScreen(
    viewModel: GameViewModel,
    config: GameConfig,
    onNavigateBack: () -> Unit
) {
    val ui by viewModel.uiState.collectAsState()
    val isDark = isSystemInDarkTheme()
    val haptic = LocalHapticFeedback.current

    LaunchedEffect(config) { viewModel.startNewGame(config) }
    BackHandler { onNavigateBack() }

    val bgGradient = if (isDark)
        Brush.verticalGradient(listOf(Color(0xFF0D0D2B), Color(0xFF141430)))
    else
        Brush.verticalGradient(listOf(Color(0xFFF2F0FF), Color(0xFFE8E4FF)))

    // Haptic-wrapped line tap
    val onLineTapWithHaptic: (LineId) -> Unit = { line ->
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        viewModel.onLineTapped(line)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "${config.gridSize}×${config.gridSize} · ${config.mode.displayName()}",
                        style = MaterialTheme.typography.titleMedium
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    // 💡 Hint button — Hard PvA only, human's turn
                    if (ui.gameState.gameMode == GameMode.PVA &&
                        ui.gameState.difficulty == Difficulty.HARD &&
                        !ui.gameState.isGameOver) {
                        val coins = ui.playerStats.hintCoins
                        TextButton(
                            onClick = viewModel::useHint,
                            colors  = ButtonDefaults.textButtonColors(
                                contentColor = Color(0xFFFFD700)
                            )
                        ) {
                            Text("💡 $coins", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                    IconButton(onClick = viewModel::toggleMute) {
                        Icon(
                            if (ui.isMuted) Icons.AutoMirrored.Filled.VolumeOff
                            else Icons.AutoMirrored.Filled.VolumeUp,
                            contentDescription = "Toggle sound"
                        )
                    }
                    IconButton(onClick = viewModel::restartGame) {
                        Icon(Icons.Default.Refresh, "Restart")
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
                ScoreBoard(state = ui.gameState, isAiThinking = ui.isAiThinking)

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
                        hintMove   = ui.hintMove,
                        activeSkin = ui.playerStats.activeSkinEnum
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
                        state        = ui.gameState
                    )
                }
            }

            if (ui.gameState.isGameOver) {
                WinDialog(
                    gameState       = ui.gameState,
                    playerStats     = ui.playerStats,
                    playerJustLost  = ui.playerJustLost,
                    onRestart       = viewModel::restartGame,
                    onMainMenu      = onNavigateBack
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
        }
    }
}

// ── Pulsing turn indicator ────────────────────────────────────────────────────

@Composable
private fun PulsingTurnIndicator(
    current: PlayerType,
    isAiThinking: Boolean,
    state: GameState
) {
    val color = if (current == PlayerType.ONE) Player1Blue else Player2Orange
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
                    "$name's turn",
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
    onRestart: () -> Unit,
    onMainMenu: () -> Unit
) {
    val winner     = gameState.winner
    val isTie      = winner == null
    val winnerName = winner?.let { gameState.playerName(it) }
    val context    = LocalContext.current

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
                    ScorePill(gameState.p1Name, gameState.p1Score, Player1Blue)
                    ScorePill(gameState.p2Name, gameState.p2Score, Player2Orange)
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
                        StatMini("T", "${playerStats.ties}", MaterialTheme.colorScheme.outline)
                        StatMini("🔥", "${playerStats.currentStreak}", Color(0xFFFFD54F))
                    }
                }

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
                            if (playerJustLost) "🔥  Revenge!" else "⚡  Play Again",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontSize = 16.sp, color = Color.White, fontWeight = FontWeight.Bold
                            )
                        )
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
                    onClick  = onMainMenu,
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

private fun GameMode.displayName() = when (this) {
    GameMode.PVP -> "PvP"
    GameMode.PVA -> "vs AI"
}
