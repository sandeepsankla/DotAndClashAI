package com.pixelplay.dotsboxes.presentation.screen

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pixelplay.dotsboxes.domain.model.LineId
import com.pixelplay.dotsboxes.domain.model.PlayerType
import com.pixelplay.dotsboxes.presentation.theme.Player1Blue
import com.pixelplay.dotsboxes.presentation.theme.Player2Orange
import com.pixelplay.dotsboxes.presentation.viewmodel.FlashChallengeViewModel
import com.pixelplay.dotsboxes.presentation.viewmodel.FlashUiState
import kotlinx.coroutines.flow.first
import kotlin.math.min

private const val PAD_FRAC  = 0.08f
private const val DOT_FRAC  = 0.07f
private const val LINE_FRAC = 0.12f
private const val HIT_FRAC  = 0.42f

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FlashChallengeScreen(
    viewModel: FlashChallengeViewModel,
    onBack: () -> Unit,
    onOpenSpin: () -> Unit,
    onOpenLeaderboard: () -> Unit = {}
) {
    val ui by viewModel.uiState.collectAsState()

    // tryStart() runs once per composition entry when statsLoaded first becomes true.
    LaunchedEffect(Unit) {
        snapshotFlow { ui.statsLoaded }.first { it }
        viewModel.tryStart()
    }

    // Timer color: red when <= 5 seconds
    val timerColor = when {
        ui.secondsLeft <= 5  -> Color(0xFFFF1744)
        ui.secondsLeft <= 10 -> Color(0xFFFFB300)
        else                  -> Color(0xFF4CAF50)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("⚡ Flash Challenge", style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold)
                        Text("Complete boxes as fast as you can!",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(0.5f))
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
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
                .background(Brush.verticalGradient(listOf(Color(0xFF0D0D2B), Color(0xFF141430))))
                .padding(padding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Timer + score row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Score: You vs AI
                    ScoreBubble("You", ui.playerScore, Player1Blue)
                    // Big timer
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "${ui.secondsLeft}",
                            style = MaterialTheme.typography.displayLarge.copy(
                                color = timerColor, fontWeight = FontWeight.ExtraBold,
                                fontSize = 56.sp
                            )
                        )
                        Text("seconds", style = MaterialTheme.typography.labelSmall,
                            color = timerColor.copy(0.7f))
                    }
                    ScoreBubble("AI", ui.aiScore, Player2Orange)
                }

                // Board
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    FlashBoard(
                        ui      = ui,
                        onTap   = { viewModel.onLineTapped(it) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // How-to hint (helps first-time players)
                Text(
                    "Grey lines are already drawn — tap the faint purple edges to complete boxes",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = Color.White.copy(0.6f)
                    ),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                )

                // Legend
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    LegendDot(Color(0xFFB0BEC5), "Already drawn")
                    LegendDot(Player1Blue, "You")
                    LegendDot(Player2Orange, "AI")
                }
            }

            // Already played today — show locked screen
            if (ui.playerStats.hasPlayedFlashToday && !ui.isRunning && !ui.isOver) {
                AlreadyPlayedOverlay(
                    pendingSpins = ui.playerStats.pendingSpins,
                    onSpin       = onOpenSpin,
                    onBack       = onBack
                )
            }

            // Game over overlay
            if (ui.isOver) {
                FlashResultOverlay(
                    ui               = ui,
                    onSpin           = onOpenSpin,
                    onBack           = onBack,
                    onLeaderboard    = onOpenLeaderboard
                )
            }
        }
    }
}

// ── Flash board — draws pre-drawn lines gray, fresh lines in player color ──────

@Composable
private fun FlashBoard(
    ui: FlashUiState,
    onTap: (LineId) -> Unit,
    modifier: Modifier = Modifier
) {
    val state          = ui.gameState
    val preFilledLines = ui.preFilledLines
    val playerLines    = ui.playerFreshLines
    val aiLines        = ui.aiFreshLines
    val gridSize       = state.gridSize

    Canvas(
        modifier = modifier
            .aspectRatio(1f)
            .pointerInput(ui.isRunning) {
                if (!ui.isRunning) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown()
                    val boardPx = minOf(size.width, size.height).toFloat()
                    val pad     = boardPx * PAD_FRAC
                    val cell    = (boardPx - 2 * pad) / gridSize
                    val hitDist = cell * HIT_FRAC
                    val tap     = down.position

                    var best: LineId? = null
                    var bestDist = Float.MAX_VALUE

                    // Check horizontal lines
                    for (r in 0..gridSize) for (c in 0 until gridSize) {
                        if (state.hLines[r][c] != null) continue
                        val mx = pad + c * cell + cell / 2f
                        val my = pad + r * cell
                        val d  = dist(tap.x, tap.y, mx, my)
                        if (d < hitDist && d < bestDist) { bestDist = d; best = LineId(r, c, true) }
                    }
                    // Check vertical lines
                    for (r in 0 until gridSize) for (c in 0..gridSize) {
                        if (state.vLines[r][c] != null) continue
                        val mx = pad + c * cell
                        val my = pad + r * cell + cell / 2f
                        val d  = dist(tap.x, tap.y, mx, my)
                        if (d < hitDist && d < bestDist) { bestDist = d; best = LineId(r, c, false) }
                    }
                    best?.let { onTap(it) }
                }
            }
    ) {
        val boardPx = minOf(size.width, size.height)
        val pad     = boardPx * PAD_FRAC
        val cell    = (boardPx - 2 * pad) / gridSize
        val dotR    = boardPx * DOT_FRAC * 0.5f
        val stroke  = boardPx * LINE_FRAC * 0.5f

        // Boxes
        for (r in 0 until gridSize) for (c in 0 until gridSize) {
            val owner = state.boxes[r][c] ?: continue
            val x = pad + c * cell
            val y = pad + r * cell
            drawRect(
                color   = if (owner == PlayerType.ONE) Player1Blue.copy(0.45f) else Player2Orange.copy(0.45f),
                topLeft = Offset(x + 4, y + 4),
                size    = Size(cell - 8, cell - 8)
            )
        }

        // Lines
        drawFlashLines(
            state = state, gridSize = gridSize, pad = pad, cell = cell,
            stroke = stroke, preFilledLines = preFilledLines,
            playerLines = playerLines, aiLines = aiLines
        )

        // Dots
        for (r in 0..gridSize) for (c in 0..gridSize) {
            drawCircle(
                color  = Color.White,
                radius = dotR,
                center = Offset(pad + c * cell, pad + r * cell)
            )
        }
    }
}

private fun DrawScope.drawFlashLines(
    state: com.pixelplay.dotsboxes.domain.model.GameState,
    gridSize: Int, pad: Float, cell: Float, stroke: Float,
    preFilledLines: Set<LineId>,
    playerLines: Set<LineId>,
    aiLines: Set<LineId>
) {
    fun colorFor(id: LineId): Pair<Color, Float> = when {
        id in playerLines     -> Player1Blue             to stroke
        id in aiLines         -> Player2Orange           to stroke
        id in preFilledLines  -> Color(0xFFB0BEC5)       to stroke * 0.8f   // clearly "already drawn"
        state.isLineDrawn(id)  -> Color(0xFFB0BEC5)      to stroke * 0.8f
        else                  -> Color(0xFF7E57C2).copy(0.35f) to stroke * 0.4f  // hint: tappable edge
    }

    // Horizontal
    for (r in 0..gridSize) for (c in 0 until gridSize) {
        val id    = LineId(r, c, true)
        val start = Offset(pad + c * cell, pad + r * cell)
        val end   = Offset(pad + (c + 1) * cell, pad + r * cell)
        val (color, w) = colorFor(id)
        drawLine(color, start, end, w, StrokeCap.Round)
    }
    // Vertical
    for (r in 0 until gridSize) for (c in 0..gridSize) {
        val id    = LineId(r, c, false)
        val start = Offset(pad + c * cell, pad + r * cell)
        val end   = Offset(pad + c * cell, pad + (r + 1) * cell)
        val (color, w) = colorFor(id)
        drawLine(color, start, end, w, StrokeCap.Round)
    }
}

private fun dist(x1: Float, y1: Float, x2: Float, y2: Float): Float {
    val dx = x1 - x2; val dy = y1 - y2
    return kotlin.math.sqrt(dx * dx + dy * dy)
}

// ── Result overlay ─────────────────────────────────────────────────────────────

@Composable
private fun FlashResultOverlay(
    ui: FlashUiState,
    onSpin: () -> Unit,
    onBack: () -> Unit,
    onLeaderboard: () -> Unit = {}
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(0.75f)),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier  = Modifier
                .fillMaxWidth(0.88f)
                .wrapContentHeight(),
            shape     = RoundedCornerShape(28.dp),
            colors    = CardDefaults.cardColors(containerColor = Color(0xFF12122A)),
            elevation = CardDefaults.cardElevation(24.dp)
        ) {
            Column(
                modifier = Modifier.padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    if (ui.playerWon) "⚡ You Won!" else "😔 AI Won",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.ExtraBold,
                        color = if (ui.playerWon) Color(0xFFFFD700) else Player2Orange
                    )
                )

                // Scores
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    ScoreBubble("You", ui.playerScore, Player1Blue)
                    ScoreBubble("AI",  ui.aiScore,     Player2Orange)
                }

                if (ui.playerWon) {
                    // Coins earned
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = Color(0xFF1A2A1A)
                    ) {
                        Text(
                            "🪙 +${ui.playerScore * 3} DotCoins earned!",
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            style = MaterialTheme.typography.labelLarge.copy(
                                color = Color(0xFF4CAF50), fontWeight = FontWeight.Bold
                            )
                        )
                    }

                    if (ui.spinAwarded && ui.playerStats.pendingSpins > 0) {
                        Button(
                            onClick = onSpin,
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            shape = RoundedCornerShape(50),
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.horizontalGradient(listOf(Color(0xFFFFD700), Color(0xFFFFA000))),
                                        RoundedCornerShape(50)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("🎡  Spin Your Prize!",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        color = Color.White, fontWeight = FontWeight.ExtraBold
                                    ))
                            }
                        }
                    }
                }

                OutlinedButton(
                    onClick  = onLeaderboard,
                    modifier = Modifier.fillMaxWidth(),
                    shape    = RoundedCornerShape(50)
                ) {
                    Text("🏆  View Leaderboard")
                }

                OutlinedButton(
                    onClick  = onBack,
                    modifier = Modifier.fillMaxWidth(),
                    shape    = RoundedCornerShape(50)
                ) {
                    Text("Back to Home")
                }
            }
        }
    }
}

// ── Small helpers ──────────────────────────────────────────────────────────────

@Composable
private fun ScoreBubble(name: String, score: Int, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            "$score",
            style = MaterialTheme.typography.displaySmall.copy(
                color = color, fontWeight = FontWeight.ExtraBold
            )
        )
        Text(name, style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(0.6f))
    }
}

@Composable
private fun AlreadyPlayedOverlay(
    pendingSpins: Int,
    onSpin: () -> Unit,
    onBack: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(0.85f)),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier  = Modifier.fillMaxWidth(0.88f),
            shape     = RoundedCornerShape(28.dp),
            colors    = CardDefaults.cardColors(containerColor = Color(0xFF12122A)),
            elevation = CardDefaults.cardElevation(24.dp)
        ) {
            Column(
                modifier = Modifier.padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text("⏰", fontSize = 52.sp)
                Text(
                    "Already Played Today!",
                    style = MaterialTheme.typography.headlineSmall.copy(
                        color = Color.White, fontWeight = FontWeight.ExtraBold,
                        textAlign = TextAlign.Center
                    )
                )
                Text(
                    "Come back tomorrow for a new board",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = Color.White.copy(0.55f),
                        textAlign = TextAlign.Center
                    )
                )

                if (pendingSpins > 0) {
                    Button(
                        onClick = onSpin,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(50),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.horizontalGradient(listOf(Color(0xFFFFD700), Color(0xFFFFA000))),
                                    RoundedCornerShape(50)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "🎡  Spin ($pendingSpins remaining)",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    color = Color.White, fontWeight = FontWeight.ExtraBold
                                )
                            )
                        }
                    }
                }

                OutlinedButton(
                    onClick  = onBack,
                    modifier = Modifier.fillMaxWidth(),
                    shape    = RoundedCornerShape(50)
                ) { Text("Back to Home") }
            }
        }
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Canvas(Modifier.size(10.dp)) { drawCircle(color) }
        Text(label, style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(0.5f))
    }
}
