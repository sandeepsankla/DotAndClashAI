package com.pixelplay.dotsboxes.presentation.screen

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pixelplay.dotsboxes.domain.model.SpinPrize
import com.pixelplay.dotsboxes.presentation.theme.Player1Blue
import com.pixelplay.dotsboxes.presentation.theme.Player2Orange
import com.pixelplay.dotsboxes.presentation.theme.Purple40
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

private val SEGMENT_COLORS = listOf(
    Color(0xFFE53935), Color(0xFFFF6F00), Color(0xFF43A047),
    Color(0xFF1E88E5), Color(0xFF8E24AA), Color(0xFF00ACC1),
    Color(0xFFF4511E), Color(0xFF6D4C41)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LuckySpinScreen(
    pendingSpins: Int,
    onSpinUsed: (SpinPrize) -> Unit,
    onBack: () -> Unit
) {
    val prizes   = SpinPrize.entries
    val segments = prizes.size
    val scope    = rememberCoroutineScope()

    var rotation       by remember { mutableFloatStateOf(0f) }
    var isSpinning     by remember { mutableStateOf(false) }
    var wonPrize       by remember { mutableStateOf<SpinPrize?>(null) }
    // spinsLeft tracks local session count; synced from DB via pendingSpins
    var spinsLeft      by remember { mutableIntStateOf(pendingSpins) }
    // Sync whenever pendingSpins changes (e.g. after DB loads real value)
    LaunchedEffect(pendingSpins) { if (!isSpinning) spinsLeft = pendingSpins }

    val animRotation = remember { Animatable(0f) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Lucky Spin 🎡", fontWeight = FontWeight.Bold) },
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
                .background(
                    Brush.verticalGradient(listOf(Color(0xFF0D0D2B), Color(0xFF1A0030)))
                )
                .padding(padding),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(24.dp),
                modifier = Modifier.padding(24.dp)
            ) {
                // Spins remaining
                Surface(
                    shape = RoundedCornerShape(50),
                    color = Color(0xFFFFD700).copy(0.15f)
                ) {
                    Text(
                        "🎟  $spinsLeft spin${if (spinsLeft != 1) "s" else ""} remaining",
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.labelLarge.copy(
                            color = Color(0xFFFFD700), fontWeight = FontWeight.Bold
                        )
                    )
                }

                // Wheel + pointer
                Box(contentAlignment = Alignment.TopCenter) {
                    // Pointer triangle
                    Box(
                        modifier = Modifier
                            .offset(y = (-4).dp)
                            .size(28.dp, 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(modifier = Modifier.size(28.dp, 32.dp)) {
                            val path = androidx.compose.ui.graphics.Path().apply {
                                moveTo(size.width / 2f, size.height)
                                lineTo(0f, 0f)
                                lineTo(size.width, 0f)
                                close()
                            }
                            drawPath(path, Color(0xFFFFD700))
                        }
                    }

                    // Spin wheel
                    SpinWheel(
                        prizes    = prizes,
                        rotation  = animRotation.value,
                        modifier  = Modifier
                            .padding(top = 20.dp)
                            .size(300.dp)
                    )
                }

                // Won prize card
                wonPrize?.let { prize ->
                    Card(
                        shape  = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A40))
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("🎉 You won!", style = MaterialTheme.typography.titleMedium.copy(
                                color = Color(0xFFFFD700), fontWeight = FontWeight.ExtraBold))
                            Text(
                                prize.label,
                                style = MaterialTheme.typography.headlineSmall.copy(
                                    color = Color.White, fontWeight = FontWeight.ExtraBold
                                )
                            )
                        }
                    }
                }

                // Spin button
                Button(
                    onClick = {
                        if (isSpinning || spinsLeft <= 0) return@Button
                        val prizeIndex = Random.nextInt(segments)
                        val sweepAngle = 360f / segments
                        // Rotate so segment prizeIndex lands under the top pointer
                        val targetDeg  = 360f * 8 - (prizeIndex * sweepAngle + sweepAngle / 2f)
                        isSpinning = true
                        wonPrize   = null
                        scope.launch {
                            animRotation.animateTo(
                                targetValue = animRotation.value + targetDeg,
                                animationSpec = tween(
                                    durationMillis = 4000,
                                    easing         = FastOutSlowInEasing
                                )
                            )
                            val prize = prizes[prizeIndex]
                            wonPrize   = prize
                            isSpinning = false
                            spinsLeft -= 1
                            onSpinUsed(prize)
                        }
                    },
                    enabled  = spinsLeft > 0 && !isSpinning,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(68.dp),
                    shape    = RoundedCornerShape(50),
                    colors   = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                if (spinsLeft > 0 && !isSpinning)
                                    Brush.horizontalGradient(listOf(Color(0xFFFFD700), Color(0xFFFFA000)))
                                else
                                    Brush.horizontalGradient(listOf(Color.Gray, Color.DarkGray)),
                                RoundedCornerShape(50)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            if (isSpinning) "Spinning…" else if (spinsLeft > 0) "🎡  SPIN!" else "No spins left",
                            style = MaterialTheme.typography.titleLarge.copy(
                                color = Color.White, fontWeight = FontWeight.ExtraBold
                            )
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SpinWheel(
    prizes: List<SpinPrize>,
    rotation: Float,
    modifier: Modifier = Modifier
) {
    val segments  = prizes.size
    val sweepAngle = 360f / segments

    Canvas(modifier = modifier.rotate(rotation)) {
        val radius  = size.minDimension / 2f
        val centerX = size.width / 2f
        val centerY = size.height / 2f

        prizes.forEachIndexed { i, prize ->
            val startAngle = i * sweepAngle - 90f
            val color      = SEGMENT_COLORS[i % SEGMENT_COLORS.size]

            // Segment fill
            drawArc(
                color      = color,
                startAngle = startAngle,
                sweepAngle = sweepAngle,
                useCenter  = true
            )

            // Segment border
            drawArc(
                color      = Color.Black.copy(0.3f),
                startAngle = startAngle,
                sweepAngle = sweepAngle,
                useCenter  = true,
                style      = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx())
            )

            // Label text — drawn via nativeCanvas
            val midAngle  = (startAngle + sweepAngle / 2f) * PI.toFloat() / 180f
            val textR     = radius * 0.65f
            val tx        = centerX + textR * cos(midAngle)
            val ty        = centerY + textR * sin(midAngle)

            drawContext.canvas.nativeCanvas.apply {
                val paint = android.graphics.Paint().apply {
                    textAlign = android.graphics.Paint.Align.CENTER
                    textSize  = 28f
                    isFakeBoldText = true
                    this.color = android.graphics.Color.WHITE
                    setShadowLayer(3f, 0f, 1f, android.graphics.Color.BLACK)
                }
                save()
                rotate(startAngle + sweepAngle / 2f + 90f, tx, ty)
                drawText(prize.label.replace(" ", "\n"), tx, ty, paint)
                restore()
            }
        }

        // Center circle
        drawCircle(color = Color(0xFF1A1A40), radius = radius * 0.12f)
        drawCircle(color = Color(0xFFFFD700), radius = radius * 0.12f,
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3.dp.toPx()))
    }
}
