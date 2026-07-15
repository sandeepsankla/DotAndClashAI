package com.pixelplay.dotsboxes.presentation.screen

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pixelplay.dotsboxes.domain.model.GameState
import com.pixelplay.dotsboxes.domain.model.PlayerLevel
import com.pixelplay.dotsboxes.domain.model.PlayerStats
import com.pixelplay.dotsboxes.domain.model.PlayerType
import com.pixelplay.dotsboxes.presentation.theme.Player1Blue
import com.pixelplay.dotsboxes.presentation.theme.Player2Orange
import com.pixelplay.dotsboxes.presentation.theme.Purple40
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun XpResultScreen(
    gameState: GameState,
    xpEarned: Int,
    coinsEarned: Int,
    playerStats: PlayerStats,
    onContinue: () -> Unit,
    onDoubleCoins: () -> Unit = {}
) {
    val playerWon = gameState.winner == PlayerType.ONE
    val ctx      = androidx.compose.ui.platform.LocalContext.current
    val bonusApp = ctx.applicationContext as com.pixelplay.dotsboxes.DotsBoxesApp
    var coinsDoubled by remember { mutableStateOf(false) }
    val isTie     = gameState.winner == null

    val oldLevel   = PlayerLevel.fromXp(playerStats.xp)
    val newXp      = playerStats.xp + xpEarned
    val newLevel   = PlayerLevel.fromXp(newXp)
    val leveledUp  = newLevel != oldLevel

    // XP bar: if leveled up, animate from 0 in new level; else from current fraction
    val xpBarStart  = if (leveledUp) 0f else playerStats.xpProgressFraction
    val xpBarTarget = run {
        val lvl = if (leveledUp) newLevel else oldLevel
        if (lvl.isMax) return@run 1f
        val range = (lvl.nextLevelXp - lvl.minXp).toFloat()
        ((newXp - lvl.minXp).toFloat() / range).coerceIn(0f, 1f)
    }

    val levelColor = when (if (leveledUp) newLevel else oldLevel) {
        PlayerLevel.BEGINNER -> Color(0xFF81C784)
        PlayerLevel.ROOKIE   -> Player1Blue
        PlayerLevel.PRO      -> Color(0xFFFF7043)
        PlayerLevel.MASTER   -> Color(0xFFAB47BC)
        PlayerLevel.LEGEND   -> Color(0xFFFFD700)
    }

    // Animatables
    val headerScale  = remember { Animatable(0f) }
    val xpCounter    = remember { Animatable(0f) }
    val coinCounter  = remember { Animatable(0f) }
    val xpBarAnim    = remember { Animatable(xpBarStart) }
    val continueAlpha = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        delay(120)
        // 1. Header pops in
        headerScale.animateTo(
            1f,
            spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)
        )
        delay(200)
        // 2. XP counter + bar simultaneously
        launch { xpCounter.animateTo(1f, tween(1300, easing = EaseOutCubic)) }
        launch { xpBarAnim.animateTo(xpBarTarget, tween(1500, easing = EaseOutCubic)) }
        // 3. Coins counter with slight delay
        if (coinsEarned > 0) {
            delay(250)
            coinCounter.animateTo(1f, tween(1000, easing = EaseOutCubic))
        }
        // 4. Continue button fades in
        delay(700)
        continueAlpha.animateTo(1f, tween(400))
    }

    val displayedXp    = (xpCounter.value * xpEarned).roundToInt()
    val displayedCoins = (coinCounter.value * coinsEarned).roundToInt()

    val resultColor = when {
        playerWon -> Color(0xFFFFD700)
        isTie     -> Color(0xFF64B5F6)
        else      -> Color(0xFFFF7043)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF09081E).copy(alpha = 0.96f))
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) {
                if (continueAlpha.value > 0.3f) onContinue()
            }
    ) {
        // ── Coin collect animation (fly from bottom → coin card) ─────────────
        if (coinsEarned > 0) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val w = constraints.maxWidth.toFloat()
                val h = constraints.maxHeight.toFloat()
                val count = minOf(coinsEarned, 16).coerceAtLeast(6)
                // Target: coin card is roughly at 72% from top, horizontally centered
                val targetX = w * 0.78f
                val targetY = h * 0.72f
                repeat(count) { i ->
                    key(i) {
                        XpCoinParticle(
                            index   = i,
                            total   = count,
                            width   = w,
                            height  = h,
                            startMs = 300L + i * 80L,
                            targetX = targetX,
                            targetY = targetY
                        )
                    }
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 28.dp, vertical = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // ── Result emoji ──────────────────────────────────────────────────
            Text(
                when { playerWon -> "🏆"; isTie -> "🤝"; else -> "😤" },
                fontSize = 80.sp,
                modifier = Modifier.scale(headerScale.value)
            )

            Spacer(Modifier.height(10.dp))

            // ── Result text ───────────────────────────────────────────────────
            Text(
                when { playerWon -> "You Win!"; isTie -> "It's a Tie!"; else -> "Good Try!" },
                style = MaterialTheme.typography.displaySmall.copy(
                    fontWeight = FontWeight.ExtraBold,
                    color      = resultColor
                ),
                modifier = Modifier.scale(headerScale.value)
            )

            Spacer(Modifier.height(4.dp))

            // ── Score ─────────────────────────────────────────────────────────
            Text(
                "${gameState.p1Name}  ${gameState.p1Score}  :  ${gameState.p2Score}  ${gameState.p2Name}",
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = Color.White.copy(alpha = 0.55f)
                ),
                modifier = Modifier.scale(headerScale.value)
            )

            Spacer(Modifier.height(28.dp))

            // ── XP Card ───────────────────────────────────────────────────────
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape    = RoundedCornerShape(22.dp),
                colors   = CardDefaults.cardColors(
                    containerColor = Color(0xFF1A237E).copy(alpha = 0.75f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // XP earned row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("⭐", fontSize = 22.sp)
                            Text(
                                "XP Earned",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    color = Color.White.copy(0.85f),
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }
                        Text(
                            "+$displayedXp",
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFFFFD700)
                            )
                        )
                    }

                    // XP bar
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                if (leveledUp) newLevel.title else oldLevel.title,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = levelColor, fontWeight = FontWeight.Bold
                                )
                            )
                            Text(
                                "${playerStats.xp + displayedXp} XP",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = Color.White.copy(0.5f)
                                )
                            )
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(14.dp)
                                .clip(RoundedCornerShape(50))
                                .background(Color.White.copy(alpha = 0.10f))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(xpBarAnim.value)
                                    .fillMaxHeight()
                                    .background(
                                        Brush.horizontalGradient(
                                            listOf(levelColor, levelColor.copy(0.7f))
                                        ),
                                        RoundedCornerShape(50)
                                    )
                            )
                        }
                    }

                    // Level-up badge
                    if (leveledUp) {
                        Card(
                            shape  = RoundedCornerShape(50),
                            colors = CardDefaults.cardColors(
                                containerColor = Color(0xFFFFD700).copy(0.2f)
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(newLevel.emoji, fontSize = 20.sp)
                                Text(
                                    "Level Up!  ${oldLevel.title} → ${newLevel.title}",
                                    style = MaterialTheme.typography.labelLarge.copy(
                                        color = Color(0xFFFFD700),
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                )
                            }
                        }
                    }
                }
            }

            // ── Coins Card ────────────────────────────────────────────────────
            if (coinsEarned > 0) {
                Spacer(Modifier.height(12.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape    = RoundedCornerShape(22.dp),
                    colors   = CardDefaults.cardColors(
                        containerColor = Color(0xFF4A2000).copy(alpha = 0.80f)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("🪙", fontSize = 22.sp)
                            Text(
                                "DotCoins",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    color = Color.White.copy(0.85f),
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }
                        Text(
                            "+$displayedCoins",
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFFFFD700)
                            )
                        )
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            // ── Watch Ad → 2× Coins (only when coins were earned, once) ────────
            if (coinsEarned > 0 && !coinsDoubled) {
                Button(
                    onClick = {
                        val activity = ctx as? android.app.Activity ?: return@Button
                        bonusApp.rewardedAd.show(
                            activity   = activity,
                            coins      = coinsEarned,
                            onRewarded = {
                                coinsDoubled = true
                                com.pixelplay.dotsboxes.analytics.Analytics.adWatched("rewarded", "double_coins")
                                onDoubleCoins()
                            },
                            onFailed   = {
                                // Don't fail silently — tell the user and reload for next tap.
                                android.widget.Toast.makeText(
                                    ctx, "Ad not ready yet — please try again in a moment.",
                                    android.widget.Toast.LENGTH_SHORT
                                ).show()
                                bonusApp.rewardedAd.preload()
                            }
                        )
                    },
                    modifier       = Modifier.fillMaxWidth().height(54.dp)
                        .graphicsLayer { alpha = continueAlpha.value },
                    shape          = RoundedCornerShape(50),
                    colors         = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                    contentPadding = PaddingValues(0.dp),
                    enabled        = continueAlpha.value > 0.3f
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize().background(
                            Brush.horizontalGradient(listOf(Color(0xFF43A047), Color(0xFF1B5E20))),
                            RoundedCornerShape(50)
                        ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "🎬  Watch Ad → 2× Coins (+$coinsEarned)",
                            style = MaterialTheme.typography.labelLarge.copy(
                                color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp
                            )
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            // ── Continue button ───────────────────────────────────────────────
            Button(
                onClick        = onContinue,
                modifier       = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .graphicsLayer { alpha = continueAlpha.value },
                shape          = RoundedCornerShape(50),
                colors         = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                contentPadding = PaddingValues(0.dp),
                enabled        = continueAlpha.value > 0.3f
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
                    Text(
                        "Continue  →",
                        style = MaterialTheme.typography.labelLarge.copy(
                            color      = Color.White,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize   = 16.sp
                        )
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            Text(
                "or tap anywhere",
                style = MaterialTheme.typography.labelSmall.copy(
                    color = Color.White.copy(alpha = 0.30f)
                ),
                modifier = Modifier.graphicsLayer { alpha = continueAlpha.value }
            )
        }
    }
}

// ── Coin collect particle: arcs from scattered start → coin counter card ─────

@Composable
private fun XpCoinParticle(
    index: Int,
    total: Int,
    width: Float,
    height: Float,
    startMs: Long,
    targetX: Float,
    targetY: Float
) {
    val density  = LocalDensity.current
    val rand     = remember { kotlin.random.Random(index * 7919 + 3) }

    // Start: spread across bottom 30% of screen
    val startX = width  * (0.08f + rand.nextFloat() * 0.84f)
    val startY = height * (0.72f + rand.nextFloat() * 0.20f)

    // Control point for arc: rises above then lands on target
    val ctrlX  = (startX + targetX) / 2f + (rand.nextFloat() - 0.5f) * width * 0.25f
    val ctrlY  = startY - height * (0.18f + rand.nextFloat() * 0.14f)

    val progress = remember { Animatable(0f) }
    val alpha    = remember { Animatable(0f) }
    val scale    = remember { Animatable(0.5f) }

    LaunchedEffect(Unit) {
        delay(startMs)
        launch { alpha.animateTo(1f, tween(100)) }
        launch { scale.animateTo(1f, tween(160, easing = EaseOutBack)) }
        progress.animateTo(1f, tween(650, easing = FastOutSlowInEasing))
        // Shrink and fade as it "enters" the coin card
        launch { scale.animateTo(0.3f, tween(180)) }
        alpha.animateTo(0f, tween(180))
    }

    val t = progress.value
    // Quadratic Bezier: P = (1-t)²·start + 2(1-t)t·ctrl + t²·target
    val u  = 1f - t
    val cx = u * u * startX + 2 * u * t * ctrlX + t * t * targetX
    val cy = u * u * startY + 2 * u * t * ctrlY + t * t * targetY
    val sz = (12f + (1f - t) * 8f).sp

    Box(Modifier.fillMaxSize()) {
        Text(
            "🪙",
            fontSize = sz,
            modifier = Modifier
                .offset(
                    x = with(density) { (cx - 8.dp.toPx()).toDp() },
                    y = with(density) { (cy - 8.dp.toPx()).toDp() }
                )
                .graphicsLayer {
                    this.alpha = alpha.value
                    scaleX = scale.value
                    scaleY = scale.value
                }
        )
    }
}
