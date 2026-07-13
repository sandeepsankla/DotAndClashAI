package com.pixelplay.dotsboxes.presentation.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.pixelplay.dotsboxes.BuildConfig
import com.pixelplay.dotsboxes.domain.model.CAMPAIGN_LEVELS
import com.pixelplay.dotsboxes.domain.model.Difficulty
import com.pixelplay.dotsboxes.domain.model.PlayerLevel
import com.pixelplay.dotsboxes.domain.model.PlayerStats
import com.pixelplay.dotsboxes.presentation.theme.Player1Blue
import com.pixelplay.dotsboxes.presentation.theme.Player2Orange
import com.pixelplay.dotsboxes.presentation.theme.Purple40

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    playerStats: PlayerStats,
    onBack: () -> Unit,
    onRateUs: () -> Unit,
    onEditName: (String) -> Unit,
    onToggleSound: (Boolean) -> Unit = {},
    onToggleVibration: (Boolean) -> Unit = {}
) {
    val isDark = isSystemInDarkTheme()
    val bgGradient = if (isDark)
        Brush.verticalGradient(listOf(Color(0xFF0D0D2B), Color(0xFF141430)))
    else
        Brush.verticalGradient(listOf(Color(0xFFF2F0FF), Color(0xFFE8E4FF)))

    val level      = playerStats.level
    val levelColor = when (level) {
        PlayerLevel.BEGINNER -> Color(0xFF81C784)
        PlayerLevel.ROOKIE   -> Player1Blue
        PlayerLevel.PRO      -> Color(0xFFFF7043)
        PlayerLevel.MASTER   -> Color(0xFFAB47BC)
        PlayerLevel.LEGEND   -> Color(0xFFFFD700)
    }

    var showEditDialog by remember { mutableStateOf(false) }
    var editNameInput  by remember { mutableStateOf(playerStats.playerName) }

    Scaffold(
        bottomBar = { com.pixelplay.dotsboxes.presentation.ads.BannerAdView() },
        topBar = {
            TopAppBar(
                title = { Text("Profile", fontWeight = FontWeight.Bold) },
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
            Modifier
                .fillMaxSize()
                .background(bgGradient)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {

                // ── Avatar + name ─────────────────────────────────────────────
                Card(
                    modifier  = Modifier.fillMaxWidth(),
                    shape     = RoundedCornerShape(24.dp),
                    colors    = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(0.85f)
                    ),
                    elevation = CardDefaults.cardElevation(8.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 24.dp, top = 16.dp, end = 24.dp, bottom = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Avatar circle
                        Box(
                            modifier = Modifier
                                .size(88.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.radialGradient(
                                        listOf(levelColor.copy(0.35f), levelColor.copy(0.12f))
                                    )
                                )
                                .border(2.dp, levelColor.copy(0.35f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(level.emoji, fontSize = 44.sp)
                        }

                        // Level badge
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = levelColor.copy(0.15f)
                        ) {
                            Text(
                                level.title,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp),
                                style    = MaterialTheme.typography.labelLarge.copy(
                                    color      = levelColor,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                playerStats.playerName,
                                style = MaterialTheme.typography.headlineSmall.copy(
                                    fontWeight = FontWeight.ExtraBold
                                )
                            )
                            IconButton(
                                onClick   = {
                                    editNameInput = playerStats.playerName
                                    showEditDialog = true
                                },
                                modifier  = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    Icons.Filled.Edit,
                                    contentDescription = "Edit name",
                                    tint     = MaterialTheme.colorScheme.primary.copy(0.6f),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        // XP bar
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    "${playerStats.xp} XP",
                                    style = MaterialTheme.typography.labelLarge.copy(
                                        color = levelColor, fontWeight = FontWeight.Bold
                                    )
                                )
                                if (!level.isMax) {
                                    Text(
                                        "${playerStats.xpToNext} to ${
                                            PlayerLevel.entries.getOrNull(level.ordinal + 1)?.title ?: "Max"
                                        }",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurface.copy(0.45f)
                                    )
                                } else {
                                    Text("MAX LEVEL 👑",
                                        style = MaterialTheme.typography.labelSmall.copy(color = levelColor))
                                }
                            }
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(10.dp)
                                    .clip(RoundedCornerShape(50))
                                    .background(levelColor.copy(0.12f))
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(playerStats.xpProgressFraction)
                                        .fillMaxHeight()
                                        .background(
                                            Brush.horizontalGradient(listOf(levelColor, levelColor.copy(0.65f))),
                                            RoundedCornerShape(50)
                                        )
                                )
                            }
                        }
                    }
                }

                // ── Currency row ──────────────────────────────────────────────
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CurrencyCard(
                        emoji = "🪙", label = "DotCoins",
                        value = "${playerStats.dotCoins}",
                        color = Color(0xFFFFC107),
                        modifier = Modifier.weight(1f)
                    )
                    CurrencyCard(
                        emoji = "💡", label = "Hints",
                        value = "${playerStats.hintCoins}",
                        color = Color(0xFF64B5F6),
                        modifier = Modifier.weight(1f)
                    )
                    CurrencyCard(
                        emoji = "🔥", label = "Streak",
                        value = "${playerStats.currentStreak}",
                        color = Color(0xFFFF7043),
                        modifier = Modifier.weight(1f)
                    )
                }

                // ── Overall stats ─────────────────────────────────────────────
                Card(
                    modifier  = Modifier.fillMaxWidth(),
                    shape     = RoundedCornerShape(20.dp),
                    colors    = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(0.85f)
                    ),
                    elevation = CardDefaults.cardElevation(6.dp)
                ) {
                    Column(
                        Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text(
                            "Game Stats",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.ExtraBold,
                                color      = MaterialTheme.colorScheme.primary
                            )
                        )
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            StatPill("🏆", "${playerStats.wins}",   "Wins",   Player1Blue)
                            StatPill("💀", "${playerStats.losses}", "Losses", Player2Orange)
                            StatPill("🤝", "${playerStats.ties}",   "Ties",   MaterialTheme.colorScheme.outline)
                            StatPill("⭐", "${playerStats.bestStreak}", "Best",  Color(0xFFFFD54F))
                        }

                        // Win rate bar
                        val wr = playerStats.winRatePct
                        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Win Rate", style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(0.5f))
                                Text("$wr%", style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold, color = Player1Blue))
                            }
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth().height(8.dp)
                                    .clip(RoundedCornerShape(50))
                                    .background(MaterialTheme.colorScheme.outline.copy(0.15f))
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(wr / 100f)
                                        .fillMaxHeight()
                                        .background(
                                            Brush.horizontalGradient(listOf(Player1Blue, Purple40)),
                                            RoundedCornerShape(50)
                                        )
                                )
                            }
                        }
                    }
                }

                // ── Per-difficulty stats ───────────────────────────────────────
                Card(
                    modifier  = Modifier.fillMaxWidth(),
                    shape     = RoundedCornerShape(20.dp),
                    colors    = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(0.85f)
                    ),
                    elevation = CardDefaults.cardElevation(6.dp)
                ) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("By Difficulty",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.primary))
                        listOf(
                            Difficulty.EASY   to Pair("🐣 Easy",   Color(0xFF66BB6A)),
                            Difficulty.MEDIUM to Pair("⚔️ Medium", Color(0xFFFFB300)),
                            Difficulty.HARD   to Pair("🧠 Hard",   Color(0xFFEF5350))
                        ).forEach { (diff, labelColor) ->
                            val ds = playerStats.diffStats(diff)
                            if (ds.totalGames > 0) {
                                DiffRow(labelColor.first, labelColor.second, ds.wins, ds.losses, ds.ties)
                            }
                        }
                        if (playerStats.totalGames == 0) {
                            Text("Play some games to see stats here!",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(0.45f),
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Center)
                        }
                    }
                }

                // ── Campaign progress ─────────────────────────────────────────
                Card(
                    modifier  = Modifier.fillMaxWidth(),
                    shape     = RoundedCornerShape(20.dp),
                    colors    = CardDefaults.cardColors(containerColor = Color.Transparent),
                    elevation = CardDefaults.cardElevation(0.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.horizontalGradient(listOf(Purple40.copy(0.8f), Player1Blue.copy(0.8f))),
                                RoundedCornerShape(20.dp)
                            )
                            .padding(18.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("🎮 Campaign",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        color = Color.White, fontWeight = FontWeight.ExtraBold))
                                Text("${playerStats.levelsCompleted.size} / ${CAMPAIGN_LEVELS.size}",
                                    style = MaterialTheme.typography.labelLarge.copy(
                                        color = Color(0xFFFFD700), fontWeight = FontWeight.ExtraBold))
                            }
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth().height(8.dp)
                                    .clip(RoundedCornerShape(50))
                                    .background(Color.White.copy(0.2f))
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(
                                            (playerStats.levelsCompleted.size.toFloat() / CAMPAIGN_LEVELS.size)
                                                .coerceIn(0f, 1f)
                                        )
                                        .fillMaxHeight()
                                        .background(
                                            Brush.horizontalGradient(listOf(Color(0xFFFFD700), Color(0xFFFFAB00))),
                                            RoundedCornerShape(50)
                                        )
                                )
                            }
                            if (playerStats.levelsCompleted.size < CAMPAIGN_LEVELS.size) {
                                Text("Next: Level ${playerStats.highestLevelUnlocked}",
                                    style = MaterialTheme.typography.bodySmall.copy(color = Color.White.copy(0.75f)))
                            } else {
                                Text("🏆 All levels complete!",
                                    style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFFFFD700)))
                            }
                        }
                    }
                }

                // ── Settings ──────────────────────────────────────────────────
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape    = RoundedCornerShape(20.dp),
                    colors   = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(0.85f)
                    )
                ) {
                    Column(modifier = Modifier.padding(vertical = 6.dp)) {
                        Text(
                            "⚙️  Settings",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
                        )
                        SettingToggleRow(
                            emoji   = "🔊",
                            label   = "Sound Effects",
                            checked = playerStats.soundEnabled,
                            onCheckedChange = onToggleSound
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 20.dp),
                            color = MaterialTheme.colorScheme.outline.copy(0.12f)
                        )
                        SettingToggleRow(
                            emoji   = "📳",
                            label   = "Vibration",
                            checked = playerStats.vibrationEnabled,
                            onCheckedChange = onToggleVibration
                        )
                    }
                }

                // ── Rate Us button ────────────────────────────────────────────
                Button(
                    onClick        = onRateUs,
                    modifier       = Modifier.fillMaxWidth().height(52.dp),
                    shape          = RoundedCornerShape(50),
                    colors         = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
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
                            "⭐  Rate Dot Clash AI",
                            style = MaterialTheme.typography.labelLarge.copy(
                                color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp
                            )
                        )
                    }
                }

                // ── App version ───────────────────────────────────────────────
                Text(
                    "Dot Clash AI  v${BuildConfig.VERSION_NAME}",
                    style     = MaterialTheme.typography.labelSmall,
                    color     = MaterialTheme.colorScheme.onBackground.copy(0.30f),
                    textAlign = TextAlign.Center
                )

                Spacer(Modifier.height(8.dp))
            }
        }
    }

    // ── Edit name dialog ──────────────────────────────────────────────────────
    if (showEditDialog) {
        AlertDialog(
            onDismissRequest = { showEditDialog = false },
            title  = { Text("Edit Name", fontWeight = FontWeight.Bold) },
            text   = {
                OutlinedTextField(
                    value         = editNameInput,
                    onValueChange = { if (it.length <= 20) editNameInput = it },
                    label         = { Text("Display name") },
                    singleLine    = true,
                    modifier      = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val name = editNameInput.trim().ifBlank { "Player" }
                        onEditName(name)
                        showEditDialog = false
                    }
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showEditDialog = false }) { Text("Cancel") }
            }
        )
    }
}

// ── Small helpers ─────────────────────────────────────────────────────────────

@Composable
private fun SettingToggleRow(
    emoji: String,
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(emoji, fontSize = 20.sp)
        Text(
            label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge
        )
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun CurrencyCard(
    emoji: String, label: String, value: String, color: Color, modifier: Modifier
) {
    Card(
        modifier  = modifier,
        shape     = RoundedCornerShape(16.dp),
        colors    = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(0.85f)
        ),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(emoji, fontSize = 22.sp)
            Text(
                value,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.ExtraBold, color = color
                )
            )
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(0.5f)
            )
        }
    }
}

@Composable
private fun StatPill(emoji: String, value: String, label: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(emoji, fontSize = 18.sp)
        Text(value,
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.ExtraBold, color = color))
        Text(label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(0.5f))
    }
}

@Composable
private fun DiffRow(label: String, color: Color, wins: Int, losses: Int, ties: Int) {
    val total = wins + losses + ties
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge.copy(color = color, fontWeight = FontWeight.Bold),
            modifier = Modifier.width(90.dp))
        Text("W $wins", style = MaterialTheme.typography.labelMedium.copy(color = Color(0xFF66BB6A)))
        Text("L $losses", style = MaterialTheme.typography.labelMedium.copy(color = Player2Orange))
        Text("T $ties", style = MaterialTheme.typography.labelMedium.copy(
            color = MaterialTheme.colorScheme.onSurface.copy(0.5f)))
        Text(
            if (total > 0) "${wins * 100 / total}%" else "-",
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
        )
    }
}
