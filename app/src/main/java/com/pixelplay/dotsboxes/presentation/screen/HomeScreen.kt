package com.pixelplay.dotsboxes.presentation.screen

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.pixelplay.dotsboxes.domain.model.ALL_AVATARS
import com.pixelplay.dotsboxes.domain.model.BoardSkin
import com.pixelplay.dotsboxes.domain.model.PlayerAvatar
import com.pixelplay.dotsboxes.domain.model.DailyLoginInfo
import com.pixelplay.dotsboxes.domain.model.Difficulty
import com.pixelplay.dotsboxes.domain.model.DifficultyStats
import com.pixelplay.dotsboxes.domain.model.GameMode
import com.pixelplay.dotsboxes.domain.model.PlayerLevel
import com.pixelplay.dotsboxes.domain.model.PlayerStats
import com.pixelplay.dotsboxes.presentation.ads.BannerAdView
import com.pixelplay.dotsboxes.presentation.theme.*
import com.pixelplay.dotsboxes.presentation.viewmodel.GameConfig
import kotlin.math.cos
import kotlin.math.sin

/** One message shown in the bell notification popup. */
private data class HomeNotification(
    val id: String,
    val emoji: String,
    val title: String,
    val subtitle: String,
    val actionLabel: String? = null,
    val onAction: (() -> Unit)? = null
)

@Composable
fun HomeScreen(
    playerStats: PlayerStats,
    pendingDailyReward: DailyLoginInfo?,
    onRewardDismissed: () -> Unit,
    onCampaign: () -> Unit,
    onStore: () -> Unit,
    onOnline: () -> Unit,
    onProfile: () -> Unit,
    onFlashChallenge: () -> Unit,
    onLeaderboard: () -> Unit = {},
    onOpenSpin: () -> Unit = {},
    onNotificationRead: (String) -> Unit = {},
    onSkinSelected: (BoardSkin) -> Unit,
    onStartGame: (GameConfig) -> Unit
) {
    // rememberSaveable survives back-navigation and process death
    var gridSize          by rememberSaveable { mutableStateOf(4) }
    var modeOrdinal       by rememberSaveable { mutableStateOf(GameMode.PVA.ordinal) }
    var difficultyOrdinal by rememberSaveable { mutableStateOf(Difficulty.MEDIUM.ordinal) }
    var p1Name            by rememberSaveable { mutableStateOf(playerStats.playerName.takeIf { it != "Player" } ?: "") }
    var p2Name            by rememberSaveable { mutableStateOf("") }

    val mode       = GameMode.entries[modeOrdinal]
    val difficulty = Difficulty.entries[difficultyOrdinal]

    // ── Bell notifications: messages derived from state, minus ones read today.
    // Reading (tapping) a message removes it for the rest of the day; it returns
    // next day if still relevant (read-state resets daily in PlayerStats).
    var showNotifications by remember { mutableStateOf(false) }
    val readToday = playerStats.notifReadToday
    val notifications = buildList {
        if (playerStats.pendingSpins > 0) add(
            HomeNotification(
                id          = "spins",
                emoji       = "🎡",
                title       = "${playerStats.pendingSpins} free spin${if (playerStats.pendingSpins > 1) "s" else ""} waiting",
                subtitle    = "Spin the lucky wheel to win rewards",
                actionLabel = "Spin",
                onAction    = onOpenSpin
            )
        )
        if (!playerStats.dailyTaskComplete) add(
            HomeNotification(
                id          = "mission",
                emoji       = "🎯",
                title       = "Daily Mission: ${playerStats.todayWins}/2 wins",
                subtitle    = "Win ${2 - playerStats.todayWins} more vs AI for a free spin",
                actionLabel = "Play",
                onAction    = onCampaign
            )
        )
        if (playerStats.hintCoins > 0 && playerStats.hintsExpiryDaysLeft in 0..1) add(
            HomeNotification(
                id       = "hints",
                emoji    = "⏳",
                title    = "${playerStats.hintCoins} hint${if (playerStats.hintCoins > 1) "s" else ""} expiring soon",
                subtitle = if (playerStats.hintsExpiryDaysLeft <= 0)
                    "They expire today — use them in Hard mode!"
                else
                    "They expire tomorrow — use them in Hard mode!"
            )
        )
    }.filterNot { it.id in readToday }

    val isDark = isSystemInDarkTheme()

    val bgStart = if (isDark) Color(0xFF232049) else Color(0xFFF0EEFF)
    val bgEnd   = if (isDark) Color(0xFF362191) else Color(0xFFDDD5FF)

    // Animate background dots
    val dotAnim by rememberInfiniteTransition(label = "dots").animateFloat(
        initialValue  = 0f,
        targetValue   = 360f,
        animationSpec = infiniteRepeatable(tween(18000, easing = LinearEasing)),
        label         = "dotRot"
    )

    val scope = rememberCoroutineScope()

    Box(Modifier.fillMaxSize()) {
        // Animated dot background
        androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
            drawAnimatedDotsBg(dotAnim, isDark)
        }

        // Gradient overlay
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(bgStart, bgEnd)
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(top = 52.dp, bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // ── Title ────────────────────────────────────────────────────────
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "DOT CLASH",
                    style = MaterialTheme.typography.displayLarge.copy(
                        brush = Brush.linearGradient(listOf(Purple80, Player1Blue)),
                        fontSize = 48.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 3.sp
                    )
                )
                Text(
                    "AI",
                    style = MaterialTheme.typography.displayMedium.copy(
                        brush  = Brush.linearGradient(listOf(Player2Orange, Color(0xFFFF9800))),
                        fontSize = 28.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 8.sp
                    )
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "Strategy · Speed · Smarts",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f),
                    letterSpacing = 1.sp
                )
            }

            // ── Level + XP bar ────────────────────────────────────────────────
            LevelXpCard(stats = playerStats)

            // ── Campaign + Store + Online row ─────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    CampaignCard(stats = playerStats, onClick = onCampaign)
                }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    StoreButton(dotCoins = playerStats.dotCoins, onClick = onStore)
                    OnlineButton(onClick = onOnline)
                }
            }

            // ── Flash Challenge + Daily Task ──────────────────────────────────
            FlashChallengeCard(
                playerStats    = playerStats,
                onClick        = onFlashChallenge,
                onDailyMission = onCampaign
            )

            // ── Stats Card ────────────────────────────────────────────────────
            if (playerStats.totalGames > 0) {
                StatsCard(stats = playerStats, onSkinSelected = onSkinSelected)
            }

            // ── Divider between campaign and custom game ──────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.outline.copy(0.25f))
                Text(
                    "Custom Game",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(0.4f)
                )
                HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.outline.copy(0.25f))
            }

            // ── Setup Card ───────────────────────────────────────────────────
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape    = RoundedCornerShape(24.dp),
                colors   = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f)
                ),
                elevation = CardDefaults.cardElevation(10.dp)
            ) {
                Column(
                    Modifier.padding(horizontal = 12.dp, vertical = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Grid size (4–6 only)
                    SectionLabel("Grid Size")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(4, 5, 6).forEach { size ->
                            FilterChip(
                                selected = gridSize == size,
                                onClick  = { gridSize = size },
                                label    = { Text("${size}×${size}", fontSize = 13.sp) },
                                colors   = chipColors()
                            )
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(0.25f))

                    // Game mode
                    SectionLabel("Mode")
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(horizontal = 2.dp)
                    ) {
                        item { ModeButton("🤖 vs AI", GameMode.PVA, mode) { modeOrdinal = it.ordinal } }
                        item { ModeButton("👥 PvP",  GameMode.PVP, mode) { modeOrdinal = it.ordinal } }
                        item { OnlineNavButton(onClick = onOnline) }
                    }

                    if (mode == GameMode.PVA) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(0.25f))
                        SectionLabel("AI Difficulty")
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(
                                Difficulty.EASY   to "🐣 Easy",
                                Difficulty.MEDIUM to "⚔️ Medium",
                                Difficulty.HARD   to "🧠 Hard"
                            ).forEach { (d, label) ->
                                FilterChip(
                                    selected = difficulty == d,
                                    onClick  = { difficultyOrdinal = d.ordinal },
                                    label    = { Text(label, fontSize = 12.sp) },
                                    colors   = chipColors()
                                )
                            }
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(0.25f))

                    // Player names — auto-fill placeholders
                    SectionLabel("Players")
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        NameField(
                            value       = p1Name,
                            onChange    = { p1Name = it },
                            label       = "You",
                            placeholder = "Your name",
                            accent      = Player1Blue,
                            modifier    = Modifier.weight(1f)
                        )
                        if (mode == GameMode.PVP) {
                            NameField(
                                value       = p2Name,
                                onChange    = { p2Name = it },
                                label       = "Player 2",
                                placeholder = "P2 name",
                                accent      = Player2Orange,
                                modifier    = Modifier.weight(1f)
                            )
                        } else {
                            // AI indicator (not editable)
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(56.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Player2Orange.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("🤖", fontSize = 22.sp)
                                    Text("AI", style = MaterialTheme.typography.labelMedium,
                                        color = Player2Orange)
                                }
                            }
                        }
                    }
                }
            }

            // ── Play button ───────────────────────────────────────────────────
            Button(
                onClick = {
                    onStartGame(
                        GameConfig(
                            gridSize   = gridSize,
                            mode       = mode,
                            difficulty = difficulty,
                            p1Name     = p1Name.ifBlank { "You" },
                            p2Name     = if (mode == GameMode.PVP) p2Name.ifBlank { "Player 2" }
                                         else "AI"
                        )
                    )
                },
                modifier       = Modifier.fillMaxWidth().height(54.dp),
                shape          = RoundedCornerShape(50),
                colors         = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                contentPadding = PaddingValues(0.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.horizontalGradient(listOf(Purple40, Player1Blue)),
                            RoundedCornerShape(50)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "PLAY  ▶",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontSize      = 17.sp,
                            letterSpacing = 2.sp,
                            color         = Color.White,
                            fontWeight    = FontWeight.ExtraBold
                        )
                    )
                }
            }

            Text(
                "Dot Clash AI  v${com.pixelplay.dotsboxes.BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.3f),
                textAlign = TextAlign.Center
            )
        }

        // ── Top-right: coins + notification ───────────────────────────────────
        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 10.dp, end = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Coin pill
            Surface(
                shape = RoundedCornerShape(50),
                color = Color(0xFFFFD700)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text("🪙", fontSize = 15.sp)
                    Text(
                        "${playerStats.dotCoins}",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.ExtraBold,
                            color      = Color(0xFF3E2000)
                        )
                    )
                }
            }
            // 🎡 Spin chip — only when spins are waiting; tap → Lucky Spin wheel
            if (playerStats.pendingSpins > 0) {
                val spinPulse by rememberInfiniteTransition(label = "spin").animateFloat(
                    initialValue  = 0.9f,
                    targetValue   = 1.08f,
                    animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
                    label = "spinPulse"
                )
                Surface(
                    shape = RoundedCornerShape(50),
                    color = Color(0xFF7E57C2),
                    modifier = Modifier
                        .scale(spinPulse)
                        .clip(RoundedCornerShape(50))
                        .clickable { onOpenSpin() }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        SpinnerIcon(modifier = Modifier.size(16.dp))
                        Text(
                            "${playerStats.pendingSpins}",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.ExtraBold,
                                color      = Color.White
                            )
                        )
                    }
                }
            }
            // Notification bell (with unread badge). Outer Box is NOT clipped so the
            // badge can overflow the circular button.
            Box(contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.onBackground.copy(0.08f))
                        .clickable { showNotifications = true },
                    contentAlignment = Alignment.Center
                ) {
                    Text("🔔", fontSize = 18.sp)
                }
                if (notifications.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .offset(x = 3.dp, y = (-3).dp)
                            .size(18.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFE53935)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "${notifications.size}",
                            color      = Color.White,
                            fontSize   = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // ── Bell notification popup ────────────────────────────────────────────
        if (showNotifications) {
            AlertDialog(
                onDismissRequest = { showNotifications = false },
                confirmButton = {
                    TextButton(onClick = { showNotifications = false }) { Text("Close") }
                },
                title = { Text("🔔  Notifications", fontWeight = FontWeight.Bold) },
                text = {
                    if (notifications.isEmpty()) {
                        Text("You're all caught up! 🎉")
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            notifications.forEach { n ->
                                Row(
                                    // Tapping reads (deletes) the message; if it has an
                                    // action, also runs it and closes the popup.
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            onNotificationRead(n.id)
                                            if (n.onAction != null) {
                                                showNotifications = false
                                                n.onAction.invoke()
                                            }
                                        },
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Text(n.emoji, fontSize = 24.sp)
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            n.title,
                                            style = MaterialTheme.typography.bodyLarge.copy(
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        )
                                        Text(
                                            n.subtitle,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurface.copy(0.6f)
                                        )
                                    }
                                    Text(
                                        n.actionLabel ?: "Dismiss",
                                        color      = if (n.actionLabel != null)
                                            MaterialTheme.colorScheme.primary
                                        else
                                            MaterialTheme.colorScheme.onSurface.copy(0.5f),
                                        fontWeight = FontWeight.Bold,
                                        style      = MaterialTheme.typography.labelLarge
                                    )
                                }
                            }
                        }
                    }
                }
            )
        }

        // ── Banner ad (bottom) ────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
        ) {
            BannerAdView()
        }

        // ── Daily reward dialog ───────────────────────────────────────────────
        if (pendingDailyReward != null) {
            DailyRewardDialog(
                info      = pendingDailyReward,
                onDismiss = onRewardDismissed
            )
        }
    }

}

// ── Spinner icon (colorful radiating lines — lucky-wheel look) ─────────────────

@Composable
private fun SpinnerIcon(modifier: Modifier = Modifier) {
    val spokeColors = listOf(
        Color(0xFFFF5252), Color(0xFFFFB300), Color(0xFFFFEB3B),
        Color(0xFF66BB6A), Color(0xFF29B6F6), Color(0xFFAB47BC)
    )
    androidx.compose.foundation.Canvas(modifier) {
        val c  = Offset(size.width / 2f, size.height / 2f)
        val r  = size.minDimension / 2f
        val n  = spokeColors.size
        val sw = size.minDimension * 0.13f
        for (i in 0 until n) {
            val ang = (2.0 * Math.PI / n * i).toFloat()
            val end = Offset(c.x + r * cos(ang), c.y + r * sin(ang))
            drawLine(spokeColors[i], c, end, strokeWidth = sw, cap = StrokeCap.Round)
        }
        drawCircle(Color.White, r * 0.22f, c)
    }
}

// ── Store button ─────────────────────────────────────────────────────────────

@Composable
private fun StoreButton(dotCoins: Int, onClick: () -> Unit) {
    Card(
        onClick   = onClick,
        shape     = RoundedCornerShape(20.dp),
        colors    = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Box(
            modifier = Modifier
                .background(
                    Brush.verticalGradient(listOf(Color(0xFFF9A825), Color(0xFFF57F17))),
                    RoundedCornerShape(20.dp)
                )
                .padding(horizontal = 16.dp, vertical = 14.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text("🛍️", fontSize = 26.sp)
                Text(
                    "Store",
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White
                    )
                )
                Text(
                    "🪙 $dotCoins",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = Color.White.copy(0.85f)
                    )
                )
            }
        }
    }
}

@Composable
private fun OnlineButton(onClick: () -> Unit) {
    Card(
        onClick   = onClick,
        shape     = RoundedCornerShape(20.dp),
        colors    = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Box(
            modifier = Modifier
                .background(
                    Brush.verticalGradient(listOf(Color(0xFF0097A7), Color(0xFF006064))),
                    RoundedCornerShape(20.dp)
                )
                .padding(horizontal = 16.dp, vertical = 14.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text("🌐", fontSize = 22.sp)
                Text(
                    "Online",
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White
                    )
                )
                Text(
                    "1v1",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = Color.White.copy(0.85f)
                    )
                )
            }
        }
    }
}

// ── Campaign card ─────────────────────────────────────────────────────────────

@Composable
private fun CampaignCard(stats: PlayerStats, onClick: () -> Unit) {
    val completed    = stats.levelsCompleted.size
    val total        = com.pixelplay.dotsboxes.domain.model.CAMPAIGN_LEVELS.size
    val nextLvl      = com.pixelplay.dotsboxes.domain.model.CAMPAIGN_LEVELS
        .getOrNull(stats.highestLevelUnlocked - 1)
    val isAllDone    = completed >= total
    val progress     = completed.toFloat() / total

    Card(
        onClick   = onClick,
        modifier  = Modifier.fillMaxSize(),
        shape     = RoundedCornerShape(20.dp),
        colors    = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(listOf(Purple40.copy(0.85f), Player1Blue.copy(0.85f))),
                    RoundedCornerShape(20.dp)
                )
                .padding(18.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Top: title + stars
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            if (isAllDone) "🏆 All Levels Complete!" else "🎮 Campaign Mode",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White
                            )
                        )
                        if (!isAllDone && nextLvl != null) {
                            Spacer(Modifier.height(2.dp))
                            Text(
                                "Next: LV ${nextLvl.number} — ${nextLvl.title} ${nextLvl.emoji}",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = Color.White.copy(0.8f)
                                )
                            )
                        } else if (isAllDone) {
                            Spacer(Modifier.height(2.dp))
                            Text(
                                "You're a Legend!",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = Color.White.copy(0.8f)
                                )
                            )
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    // Stars badge
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("⭐", fontSize = 24.sp)
                        Text(
                            "$completed/$total",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFFFFD700)
                            )
                        )
                    }
                }

                // Middle: progress bar
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(50))
                        .background(Color.White.copy(alpha = 0.25f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progress.coerceIn(0f, 1f))
                            .fillMaxHeight()
                            .background(
                                Brush.horizontalGradient(listOf(Color(0xFFFFD700), Color(0xFFFFAB00))),
                                androidx.compose.foundation.shape.RoundedCornerShape(50)
                            )
                    )
                }

                // Bottom: CTA button full width
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = Color.White.copy(alpha = 0.22f)
                ) {
                    Text(
                        if (completed == 0) "Start  ▶" else "Continue  ▶",
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 10.dp),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelLarge.copy(
                            color = Color.White,
                            fontWeight = FontWeight.ExtraBold
                        )
                    )
                }
            }
        }
    }
}

// ── Lifetime stats card ────────────────────────────────────────────────────────

@Composable
private fun StatsCard(stats: PlayerStats, onSkinSelected: (BoardSkin) -> Unit) {
    Card(
        modifier  = Modifier.fillMaxWidth(),
        shape     = RoundedCornerShape(20.dp),
        colors    = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.82f)
        ),
        elevation = CardDefaults.cardElevation(8.dp)
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {

            // Header row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Your Stats",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text("💡", fontSize = 14.sp)
                    Text(
                        "${stats.hintCoins} hints",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFFFFD700)
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            // Overall stats (no difficulty tabs)
            OverallStatsRow(stats)

            // ── Skin selector — CONTRAST always free, others via streak unlock ──
            val unlockedSkins = BoardSkin.entries.filter {
                it == BoardSkin.CONTRAST || stats.unlockedSkins.contains(it.name)
            }
            if (unlockedSkins.size > 1) {
                HorizontalDivider(
                    modifier = Modifier.padding(top = 12.dp),
                    color = MaterialTheme.colorScheme.outline.copy(0.2f)
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Board Theme",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(6.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(horizontal = 2.dp)
                ) {
                    items(unlockedSkins) { skin ->
                        val isActive = stats.activeSkin == skin.name
                        FilterChip(
                            selected = isActive,
                            onClick  = { onSkinSelected(skin) },
                            label    = { Text("${skin.emoji} ${skin.displayName}", fontSize = 12.sp, maxLines = 1) },
                            colors   = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = when (skin) {
                                    BoardSkin.FIRE     -> Color(0xFFFF1744).copy(alpha = 0.2f)
                                    BoardSkin.GOLDEN   -> Color(0xFFFF8F00).copy(alpha = 0.2f)
                                    BoardSkin.CONTRAST -> Color(0xFF212121)
                                    else               -> MaterialTheme.colorScheme.primaryContainer
                                },
                                selectedLabelColor = when (skin) {
                                    BoardSkin.FIRE     -> Color(0xFFFF1744)
                                    BoardSkin.GOLDEN   -> Color(0xFFFF8F00)
                                    BoardSkin.CONTRAST -> Color(0xFFFFFFFF)
                                    else               -> MaterialTheme.colorScheme.onPrimaryContainer
                                }
                            )
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OverallStatsRow(stats: PlayerStats) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        HomeStatPill("🏆", "${stats.wins}",           "Wins",   Player1Blue)
        HomeStatPill("💀", "${stats.losses}",         "Losses", Player2Orange)
        HomeStatPill("🤝", "${stats.ties}",           "Ties",   MaterialTheme.colorScheme.outline)
        HomeStatPill("🔥", "${stats.currentStreak}",  "Streak", Color(0xFFFFD54F))
    }
}

@Composable
private fun DifficultyStatsRow(d: DifficultyStats, accent: Color) {
    if (d.totalGames == 0) {
        Text(
            "No games yet on this difficulty",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
            modifier = Modifier.fillMaxWidth()
        )
        return
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        HomeStatPill("🏆", "${d.wins}",          "Wins",   accent)
        HomeStatPill("💀", "${d.losses}",        "Losses", Player2Orange)
        HomeStatPill("🤝", "${d.ties}",          "Ties",   MaterialTheme.colorScheme.outline)
        HomeStatPill("🔥", "${d.currentStreak}", "Streak", Color(0xFFFFD54F))
    }
    Spacer(Modifier.height(4.dp))
    // Win rate bar
    val pct = d.winRatePct / 100f
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                "Win rate",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
            Text(
                "${d.winRatePct}%",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = accent
            )
        }
        Spacer(Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(androidx.compose.foundation.shape.RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(pct)
                    .fillMaxHeight()
                    .background(
                        Brush.horizontalGradient(listOf(accent, accent.copy(alpha = 0.6f))),
                        androidx.compose.foundation.shape.RoundedCornerShape(50)
                    )
            )
        }
    }
}

@Composable
private fun HomeStatPill(emoji: String, value: String, label: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(emoji, fontSize = 18.sp)
        Text(
            value,
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.ExtraBold, color = color
            )
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
        )
    }
}

// ── Level + XP card ───────────────────────────────────────────────────────────

@Composable
private fun LevelXpCard(stats: PlayerStats) {
    val level = stats.level
    val levelColor = when (level) {
        PlayerLevel.BEGINNER -> Color(0xFF81C784)
        PlayerLevel.ROOKIE   -> Player1Blue
        PlayerLevel.PRO      -> Color(0xFFFF7043)
        PlayerLevel.MASTER   -> Color(0xFFAB47BC)
        PlayerLevel.LEGEND   -> Color(0xFFFFD700)
    }

    Card(
        modifier  = Modifier.fillMaxWidth(),
        shape     = RoundedCornerShape(16.dp),
        colors    = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.78f)
        ),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Level badge
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .background(levelColor.copy(alpha = 0.15f), RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(level.emoji, fontSize = 22.sp)
                    Text(
                        level.title,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = levelColor
                        )
                    )
                }
            }

            // XP bar + info
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "${stats.xp} XP",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.ExtraBold, color = levelColor
                        )
                    )
                    if (!level.isMax) {
                        Text(
                            "${stats.xpToNext} to ${PlayerLevel.entries.getOrNull(level.ordinal + 1)?.title ?: "Max"}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(0.45f)
                        )
                    } else {
                        Text("MAX", style = MaterialTheme.typography.labelSmall.copy(color = levelColor))
                    }
                }
                // XP progress bar
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(50))
                        .background(levelColor.copy(alpha = 0.15f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(stats.xpProgressFraction)
                            .fillMaxHeight()
                            .background(
                                Brush.horizontalGradient(listOf(levelColor, levelColor.copy(0.7f))),
                                RoundedCornerShape(50)
                            )
                    )
                }
            }

            // Daily streak badge
            if (stats.dailyStreak > 0) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("🔥", fontSize = 20.sp)
                    Text(
                        "${stats.dailyStreak}d",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFFFF6D00)
                        )
                    )
                }
            }

        }
    }
}

// ── Daily reward dialog ────────────────────────────────────────────────────────

@Composable
private fun DailyRewardDialog(info: DailyLoginInfo, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape     = RoundedCornerShape(28.dp),
            colors    = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Streak flame
                Text(if (info.isConsecutive) "🔥" else "📅", fontSize = 56.sp)

                Text(
                    if (info.isMilestone) "Day ${info.dailyStreak} Streak! 🎉"
                    else if (info.isConsecutive) "${info.dailyStreak} Day Streak!"
                    else "Welcome Back!",
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold),
                    textAlign = TextAlign.Center
                )

                // Reward pills
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape    = RoundedCornerShape(16.dp),
                    colors   = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(0.4f)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        RewardRow("🌟", "+${info.xpBonus} XP", "Daily login bonus")
                        if (info.hintCoinsBonus > 0)
                            RewardRow("💡", "+${info.hintCoinsBonus} Hints", "Streak milestone reward!")
                        info.unlockedSkin?.let { skin ->
                            RewardRow(skin.emoji, "${skin.displayName} Theme", "New board skin unlocked!")
                        }
                        info.newBadge?.let { badge ->
                            RewardRow("🏅", badge, "Achievement badge earned!")
                        }
                    }
                }

                if (!info.isConsecutive) {
                    Text(
                        "Play every day to build your streak and earn rewards!",
                        style     = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                        color     = MaterialTheme.colorScheme.onSurface.copy(0.5f)
                    )
                }

                Button(
                    onClick        = onDismiss,
                    modifier       = Modifier.fillMaxWidth(),
                    shape          = RoundedCornerShape(50),
                    colors         = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp)
                            .background(
                                Brush.horizontalGradient(listOf(Purple40, Player1Blue)),
                                RoundedCornerShape(50)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            if (info.isMilestone) "Awesome! 🎉" else "Let's Play! ▶",
                            style = MaterialTheme.typography.labelLarge.copy(
                                color = Color.White, fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RewardRow(emoji: String, title: String, subtitle: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(emoji, fontSize = 24.sp)
        Column {
            Text(title, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
            Text(subtitle, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(0.55f))
        }
    }
}

// ── Animated floating dots background ────────────────────────────────────────

private fun DrawScope.drawAnimatedDotsBg(angleOffset: Float, isDark: Boolean) {
    val dotColor = if (isDark) Color(0xFF4B00BF).copy(alpha = 0.18f)
                  else Color(0xFF7C4DFF).copy(alpha = 0.10f)

    val cx = size.width  / 2f
    val cy = size.height / 2f

    // 3 rings of dots rotating slowly
    val rings = listOf(
        Triple(6,  minOf(cx, cy) * 0.35f, 10f),
        Triple(10, minOf(cx, cy) * 0.65f, 7f),
        Triple(14, minOf(cx, cy) * 0.90f, 5f)
    )

    rings.forEachIndexed { idx, (count, radius, dotR) ->
        val direction = if (idx % 2 == 0) 1f else -1f
        repeat(count) { i ->
            val angle = Math.toRadians(
                (360.0 / count * i + angleOffset * direction * (0.4f + idx * 0.2f)).toDouble()
            )
            drawCircle(
                color  = dotColor,
                radius = dotR,
                center = Offset(cx + radius * cos(angle).toFloat(), cy + radius * sin(angle).toFloat())
            )
        }
    }
}

// ── Small composable helpers ──────────────────────────────────────────────────

@Composable
private fun SectionLabel(text: String) = Text(
    text,
    style = MaterialTheme.typography.labelLarge,
    color = MaterialTheme.colorScheme.primary
)

@Composable
private fun OnlineNavButton(onClick: () -> Unit) {
    OutlinedButton(
        onClick        = onClick,
        modifier       = Modifier.widthIn(min = 100.dp).height(48.dp),
        shape          = RoundedCornerShape(12.dp),
        contentPadding = PaddingValues(horizontal = 12.dp),
        colors         = ButtonDefaults.outlinedButtonColors(
            containerColor = Color.Transparent,
            contentColor   = MaterialTheme.colorScheme.onSurface
        ),
        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.outline.copy(0.4f))
    ) {
        Text("🌐 Online", style = MaterialTheme.typography.labelMedium, maxLines = 1)
    }
}

@Composable
private fun ModeButton(
    label: String, thisMode: GameMode, selected: GameMode, onClick: (GameMode) -> Unit
) {
    val active = thisMode == selected
    OutlinedButton(
        onClick        = { onClick(thisMode) },
        modifier       = Modifier.widthIn(min = 100.dp).height(48.dp),
        shape          = RoundedCornerShape(12.dp),
        contentPadding = PaddingValues(horizontal = 12.dp),
        colors         = ButtonDefaults.outlinedButtonColors(
            containerColor = if (active) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
            contentColor   = if (active) MaterialTheme.colorScheme.onPrimaryContainer
                             else MaterialTheme.colorScheme.onSurface
        ),
        border = BorderStroke(
            1.5.dp,
            if (active) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.outline.copy(0.4f)
        )
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1)
    }
}

@Composable
private fun NameField(
    value: String, onChange: (String) -> Unit,
    label: String, placeholder: String,
    accent: Color, modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value         = value,
        onValueChange = { if (it.length <= 16) onChange(it) },
        modifier      = modifier,
        label         = { Text(label, fontSize = 12.sp) },
        placeholder   = { Text(placeholder, fontSize = 12.sp) },
        singleLine    = true,
        shape         = RoundedCornerShape(12.dp),
        colors        = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = accent,
            focusedLabelColor  = accent,
            cursorColor        = accent
        )
    )
}

@Composable
private fun chipColors() = FilterChipDefaults.filterChipColors(
    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
    selectedLabelColor     = MaterialTheme.colorScheme.onPrimaryContainer
)

// ── Flash Challenge Card ──────────────────────────────────────────────────────

@Composable
private fun FlashChallengeCard(
    playerStats: PlayerStats,
    onClick: () -> Unit,
    onDailyMission: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        FlashChallengeBox(playerStats = playerStats, onClick = onClick)
        DailyMissionBox(playerStats = playerStats, onClick = onDailyMission)
    }
}

// ── Box 1: Flash Challenge ─────────────────────────────────────────────────────

@Composable
private fun FlashChallengeBox(
    playerStats: PlayerStats,
    onClick: () -> Unit
) {
    val played = playerStats.hasPlayedFlashToday

    Card(
        onClick   = onClick,
        modifier  = Modifier.fillMaxWidth(),
        shape     = RoundedCornerShape(20.dp),
        colors    = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.horizontalGradient(listOf(Color(0xFF1A0040), Color(0xFF0D2040))),
                    RoundedCornerShape(20.dp)
                )
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(if (played) "🔒" else "⚡", fontSize = 36.sp)

                Column(modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "Flash Challenge",
                        style = MaterialTheme.typography.titleMedium.copy(
                            color = Color.White, fontWeight = FontWeight.ExtraBold
                        )
                    )
                    Text(
                        if (played) "✅ Played today · Resets tomorrow"
                        else "15s · Complete boxes vs AI",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = if (played) Color(0xFF4CAF50) else Color.White.copy(0.55f)
                        )
                    )
                    if (!played && playerStats.pendingSpins > 0) {
                        Text(
                            "🎡 ${playerStats.pendingSpins} spin${if (playerStats.pendingSpins > 1) "s" else ""} waiting!",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = Color(0xFFFFD700), fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }

                if (!played) {
                    Text("›", fontSize = 28.sp, color = Color.White.copy(0.4f))
                }
            }
        }
    }
}

// ── Box 2: Daily Mission (win 2 vs AI) ─────────────────────────────────────────

@Composable
private fun DailyMissionBox(playerStats: PlayerStats, onClick: () -> Unit = {}) {
    val wins     = playerStats.todayWins
    val complete = playerStats.dailyTaskComplete

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.horizontalGradient(listOf(Color(0xFF2A1A00), Color(0xFF1A2000))),
                RoundedCornerShape(20.dp)
            )
            .clickable(enabled = !complete, onClick = onClick)
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(if (complete) "✅" else "🎯", fontSize = 36.sp)

            Column(modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Daily Mission",
                        style = MaterialTheme.typography.titleMedium.copy(
                            color = Color.White, fontWeight = FontWeight.ExtraBold
                        )
                    )
                    Text(
                        "${minOf(wins, 2)}/2 ${if (complete) "✅" else ""}",
                        style = MaterialTheme.typography.labelLarge.copy(
                            color = if (complete) Color(0xFF4CAF50) else Color(0xFFFFD700),
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
                Text(
                    if (complete) "Completed! Come back tomorrow"
                    else "Win 2 games vs AI to complete",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = if (complete) Color(0xFF4CAF50) else Color.White.copy(0.55f)
                    )
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Color.White.copy(0.12f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth((wins / 2f).coerceIn(0f, 1f))
                            .fillMaxHeight()
                            .background(
                                Brush.horizontalGradient(
                                    listOf(Color(0xFFFFD700), Color(0xFFFFA000))
                                ),
                                RoundedCornerShape(50)
                            )
                    )
                }
            }
        }
    }
}

// ── Avatar Picker ──────────────────────────────────────────────────────────────

private data class AvatarCategory(val title: String, val icon: String, val ids: List<Int>)

private val AVATAR_CATEGORIES = listOf(
    AvatarCategory("Free",        "🎁", listOf(0)),
    AvatarCategory("Superheroes", "🦸", listOf(1, 2, 3, 4, 5, 6, 7)),
    AvatarCategory("Royalty",     "👑", listOf(8, 9, 10, 11)),
    AvatarCategory("Legends",     "🐉", listOf(12, 13, 14, 15, 16, 17)),
    AvatarCategory("Elite",       "💎", listOf(18, 19, 20, 21, 22, 23))
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AvatarPickerSheet(
    playerStats: PlayerStats,
    onSelect: (PlayerAvatar) -> Unit,
    onDismiss: () -> Unit
) {
    val avatarMap = remember { ALL_AVATARS.associateBy { it.id } }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor   = Color(0xFF0E0E24),
        tonalElevation   = 0.dp
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(bottom = 36.dp)
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Choose Avatar",
                    style = MaterialTheme.typography.titleLarge.copy(
                        color = Color.White, fontWeight = FontWeight.ExtraBold
                    )
                )
                Surface(
                    shape = RoundedCornerShape(50),
                    color = Color(0xFFFFB300).copy(0.15f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFFB300).copy(0.4f))
                ) {
                    Text(
                        "🪙 ${playerStats.dotCoins}",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                        style = MaterialTheme.typography.labelLarge.copy(
                            color = Color(0xFFFFB300), fontWeight = FontWeight.Bold
                        )
                    )
                }
            }

            HorizontalDivider(color = Color.White.copy(0.06f), modifier = Modifier.padding(horizontal = 16.dp))
            Spacer(Modifier.height(8.dp))

            // Categories
            AVATAR_CATEGORIES.forEach { category ->
                val avatars = category.ids.mapNotNull { avatarMap[it] }

                // Category header
                Row(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(category.icon, fontSize = 16.sp)
                    Text(
                        category.title,
                        style = MaterialTheme.typography.labelLarge.copy(
                            color = Color.White.copy(0.6f),
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 1.sp
                        )
                    )
                    Box(
                        modifier = Modifier
                            .height(1.dp)
                            .weight(1f)
                            .background(Color.White.copy(0.08f))
                    )
                }

                // Avatar grid (4 per row)
                avatars.chunked(4).forEach { row ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 3.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        row.forEach { avatar ->
                            AvatarCell(
                                avatar     = avatar,
                                isActive   = avatar.id == playerStats.activeAvatarId,
                                isUnlocked = playerStats.isAvatarUnlocked(avatar.id),
                                canAfford  = playerStats.dotCoins >= avatar.cost,
                                modifier   = Modifier.weight(1f),
                                onTap      = { onSelect(avatar) }
                            )
                        }
                        repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }

                Spacer(Modifier.height(4.dp))
            }
        }
    }
}

@Composable
private fun AvatarCell(
    avatar: PlayerAvatar,
    isActive: Boolean,
    isUnlocked: Boolean,
    canAfford: Boolean,
    modifier: Modifier,
    onTap: () -> Unit
) {
    val bgColor = when {
        isActive   -> Player1Blue.copy(0.25f)
        isUnlocked -> Color.White.copy(0.07f)
        else       -> Color.White.copy(0.03f)
    }
    val borderColor = when {
        isActive   -> Player1Blue
        isUnlocked -> Color.White.copy(0.15f)
        canAfford  -> Color(0xFFFFB300).copy(0.3f)
        else       -> Color.White.copy(0.05f)
    }
    val dim = !isUnlocked && !canAfford

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(14.dp))
            .background(bgColor)
            .border(if (isActive) 2.dp else 1.dp, borderColor, RoundedCornerShape(14.dp))
            .clickable(enabled = isUnlocked || canAfford, onClick = onTap),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(6.dp)
        ) {
            Text(
                avatar.emoji,
                fontSize = 26.sp,
                modifier = Modifier.alpha(if (dim) 0.25f else 1f)
            )
            Spacer(Modifier.height(3.dp))
            when {
                isActive   -> Text("✓ Active", fontSize = 8.sp,
                    color = Player1Blue, fontWeight = FontWeight.ExtraBold)
                isUnlocked -> Text("Owned", fontSize = 8.sp,
                    color = Color.White.copy(0.35f))
                canAfford  -> Text("🪙 ${avatar.cost}", fontSize = 8.sp,
                    color = Color(0xFFFFB300), fontWeight = FontWeight.Bold)
                else       -> Text("🔒 ${avatar.cost}", fontSize = 8.sp,
                    color = Color.White.copy(0.2f))
            }
        }

        // Active glow ring
        if (isActive) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .border(2.dp, Player1Blue.copy(0.5f), RoundedCornerShape(14.dp))
            )
        }
    }
}
