package com.pixelplay.dotsboxes.presentation.screen

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.pixelplay.dotsboxes.domain.model.BoardSkin
import com.pixelplay.dotsboxes.domain.model.DailyLoginInfo
import com.pixelplay.dotsboxes.domain.model.Difficulty
import com.pixelplay.dotsboxes.domain.model.DifficultyStats
import com.pixelplay.dotsboxes.domain.model.GameMode
import com.pixelplay.dotsboxes.domain.model.PlayerLevel
import com.pixelplay.dotsboxes.domain.model.PlayerStats
import com.pixelplay.dotsboxes.presentation.theme.*
import com.pixelplay.dotsboxes.presentation.viewmodel.GameConfig
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun HomeScreen(
    playerStats: PlayerStats,
    pendingDailyReward: DailyLoginInfo?,
    onRewardDismissed: () -> Unit,
    onSkinSelected: (BoardSkin) -> Unit,
    onStartGame: (GameConfig) -> Unit
) {
    // rememberSaveable survives back-navigation and process death
    var gridSize          by rememberSaveable { mutableStateOf(4) }
    var modeOrdinal       by rememberSaveable { mutableStateOf(GameMode.PVP.ordinal) }
    var difficultyOrdinal by rememberSaveable { mutableStateOf(Difficulty.MEDIUM.ordinal) }
    var p1Name            by rememberSaveable { mutableStateOf("") }
    var p2Name            by rememberSaveable { mutableStateOf("") }

    val mode       = GameMode.entries[modeOrdinal]
    val difficulty = Difficulty.entries[difficultyOrdinal]

    val isDark = isSystemInDarkTheme()

    val bgStart = if (isDark) Color(0xFF0D0D2B) else Color(0xFFF0EEFF)
    val bgEnd   = if (isDark) Color(0xFF1A0060) else Color(0xFFDDD5FF)

    // Animate background dots
    val dotAnim by rememberInfiniteTransition(label = "dots").animateFloat(
        initialValue  = 0f,
        targetValue   = 360f,
        animationSpec = infiniteRepeatable(tween(18000, easing = LinearEasing)),
        label         = "dotRot"
    )

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
                        listOf(bgStart.copy(alpha = 0.92f), bgEnd.copy(alpha = 0.88f))
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

            // ── Stats Card ────────────────────────────────────────────────────
            if (playerStats.totalGames > 0) {
                StatsCard(stats = playerStats, onSkinSelected = onSkinSelected)
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
                    Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Grid size
                    SectionLabel("Grid Size")
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(4, 5, 6, 7, 8).forEach { size ->
                            FilterChip(
                                selected = gridSize == size,
                                onClick  = { gridSize = size },
                                label    = { Text("${size}×${size}", fontSize = 12.sp) },
                                colors   = chipColors()
                            )
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(0.25f))

                    // Game mode
                    SectionLabel("Mode")
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ModeButton("👥  PvP",   GameMode.PVP, mode) { modeOrdinal = it.ordinal }
                        ModeButton("🤖  vs AI", GameMode.PVA, mode) { modeOrdinal = it.ordinal }
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
                "Dot Clash AI  v1.0",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.3f),
                textAlign = TextAlign.Center
            )
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

// ── Lifetime stats card ────────────────────────────────────────────────────────

@Composable
private fun StatsCard(stats: PlayerStats, onSkinSelected: (BoardSkin) -> Unit) {
    var selectedTab by rememberSaveable { mutableStateOf(0) }  // 0=Overall 1=Easy 2=Med 3=Hard

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

            Spacer(Modifier.height(10.dp))

            // Tab row: Overall / Easy / Medium / Hard
            val tabs = listOf("All" to "🌐", "Easy" to "🐣", "Medium" to "⚔️", "Hard" to "🧠")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                tabs.forEachIndexed { i, (label, emoji) ->
                    val active = selectedTab == i
                    // Only show difficulty tabs if that difficulty has games played
                    val hasGames = when (i) {
                        0 -> true
                        1 -> stats.diffStats(Difficulty.EASY).totalGames > 0
                        2 -> stats.diffStats(Difficulty.MEDIUM).totalGames > 0
                        3 -> stats.diffStats(Difficulty.HARD).totalGames > 0
                        else -> false
                    }
                    if (hasGames) {
                        FilterChip(
                            selected = active,
                            onClick  = { selectedTab = i },
                            label    = { Text("$emoji $label", fontSize = 11.sp) },
                            colors   = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor     = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // Stats row for selected tab
            when (selectedTab) {
                0 -> OverallStatsRow(stats)
                1 -> DifficultyStatsRow(stats.diffStats(Difficulty.EASY), Color(0xFF81C784))
                2 -> DifficultyStatsRow(stats.diffStats(Difficulty.MEDIUM), Player1Blue)
                3 -> DifficultyStatsRow(stats.diffStats(Difficulty.HARD), Player2Orange)
            }

            // ── Skin selector (only if skins beyond DEFAULT unlocked) ──────────
            val unlockedSkins = BoardSkin.entries.filter { stats.unlockedSkins.contains(it.name) }
            if (unlockedSkins.size > 1) {
                HorizontalDivider(
                    modifier = Modifier.padding(top = 12.dp),
                    color = MaterialTheme.colorScheme.outline.copy(0.2f)
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Board Skin",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    unlockedSkins.forEach { skin ->
                        val isActive = stats.activeSkin == skin.name
                        FilterChip(
                            selected = isActive,
                            onClick  = { onSkinSelected(skin) },
                            label    = { Text("${skin.emoji} ${skin.displayName}", fontSize = 12.sp) },
                            colors   = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = when (skin) {
                                    BoardSkin.FIRE   -> Color(0xFFFF5722).copy(alpha = 0.2f)
                                    BoardSkin.GOLDEN -> Color(0xFFFFD700).copy(alpha = 0.2f)
                                    else             -> MaterialTheme.colorScheme.primaryContainer
                                },
                                selectedLabelColor = when (skin) {
                                    BoardSkin.FIRE   -> Color(0xFFFF5722)
                                    BoardSkin.GOLDEN -> Color(0xFFFFD700)
                                    else             -> MaterialTheme.colorScheme.onPrimaryContainer
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
private fun RowScope.ModeButton(
    label: String, thisMode: GameMode, selected: GameMode, onClick: (GameMode) -> Unit
) {
    val active = thisMode == selected
    OutlinedButton(
        onClick = { onClick(thisMode) },
        modifier = Modifier.weight(1f),
        shape    = RoundedCornerShape(12.dp),
        colors   = ButtonDefaults.outlinedButtonColors(
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
        Text(label, style = MaterialTheme.typography.labelLarge)
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
