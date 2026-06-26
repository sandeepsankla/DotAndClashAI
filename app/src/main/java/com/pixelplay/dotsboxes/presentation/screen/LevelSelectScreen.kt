package com.pixelplay.dotsboxes.presentation.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pixelplay.dotsboxes.domain.model.*
import com.pixelplay.dotsboxes.presentation.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LevelSelectScreen(
    playerStats: PlayerStats,
    onLevelSelected: (LevelConfig) -> Unit,
    onBack: () -> Unit
) {
    val completedCount = playerStats.levelsCompleted.size
    val totalLevels    = CAMPAIGN_LEVELS.size
    val isDark         = isSystemInDarkTheme()

    val bgGradient = Brush.verticalGradient(
        if (isDark) listOf(Color(0xFF0D0D2B), Color(0xFF141430))
        else        listOf(Color(0xFFF2F0FF), Color(0xFFE8E4FF))
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "Campaign",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold)
                        )
                        Text(
                            "$completedCount / $totalLevels levels completed",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(0.55f)
                        )
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
                .background(bgGradient)
                .padding(padding)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Overall progress bar
                LinearProgressIndicator(
                    progress        = { completedCount.toFloat() / totalLevels },
                    modifier        = Modifier.fillMaxWidth().height(4.dp),
                    color           = Purple40,
                    trackColor      = MaterialTheme.colorScheme.outline.copy(0.2f)
                )

                // Stats strip
                if (completedCount > 0) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "⭐ $completedCount completed",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color(0xFFFFD700)
                        )
                        val nextLevel = playerStats.highestLevelUnlocked
                        if (nextLevel <= CAMPAIGN_LEVELS.size) {
                            Text(
                                "Next: Level $nextLevel",
                                style = MaterialTheme.typography.labelMedium,
                                color = Purple40
                            )
                        } else {
                            Text(
                                "All levels unlocked! 🏆",
                                style = MaterialTheme.typography.labelMedium,
                                color = Color(0xFFFFD700)
                            )
                        }
                    }
                } else {
                    Spacer(Modifier.height(10.dp))
                }

                // Level grid
                LazyVerticalGrid(
                    columns             = GridCells.Fixed(2),
                    contentPadding      = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier            = Modifier.fillMaxSize()
                ) {
                    items(CAMPAIGN_LEVELS) { level ->
                        val isCompleted = level.number in playerStats.levelsCompleted
                        val isUnlocked  = level.number <= playerStats.highestLevelUnlocked
                        LevelCard(
                            level       = level,
                            isCompleted = isCompleted,
                            isUnlocked  = isUnlocked,
                            onClick     = { if (isUnlocked) onLevelSelected(level) }
                        )
                    }
                    item { Spacer(Modifier.height(16.dp)) }
                    item { Spacer(Modifier.height(16.dp)) }
                }
            }
        }
    }
}

@Composable
private fun LevelCard(
    level: LevelConfig,
    isCompleted: Boolean,
    isUnlocked: Boolean,
    onClick: () -> Unit
) {
    val diffColor = when (level.difficulty) {
        Difficulty.EASY   -> Color(0xFF81C784)
        Difficulty.MEDIUM -> Player1Blue
        Difficulty.HARD   -> Player2Orange
    }

    val containerColor = when {
        isCompleted -> diffColor.copy(alpha = 0.13f)
        isUnlocked  -> MaterialTheme.colorScheme.surface.copy(alpha = 0.90f)
        else        -> MaterialTheme.colorScheme.surface.copy(alpha = 0.35f)
    }

    Card(
        onClick   = onClick,
        modifier  = Modifier
            .fillMaxWidth()
            .aspectRatio(0.82f)
            .alpha(if (isUnlocked) 1f else 0.55f),
        shape     = RoundedCornerShape(20.dp),
        colors    = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(if (isUnlocked) 6.dp else 1.dp),
        border    = if (isCompleted) BorderStroke(2.dp, diffColor.copy(0.5f))
                    else if (isUnlocked) BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(0.2f))
                    else null
    ) {
        Box(modifier = Modifier.fillMaxSize().padding(14.dp)) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Top row: level number + status icon
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = (if (isUnlocked) diffColor else MaterialTheme.colorScheme.outline)
                            .copy(alpha = 0.15f)
                    ) {
                        Text(
                            "LV ${level.number}",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.ExtraBold,
                                color = if (isUnlocked) diffColor
                                        else MaterialTheme.colorScheme.onSurface.copy(0.4f)
                            )
                        )
                    }
                    Text(
                        when {
                            isCompleted -> "⭐"
                            !isUnlocked -> "🔒"
                            else        -> ""
                        },
                        fontSize = 16.sp
                    )
                }

                // Center emoji
                Text(
                    if (!isUnlocked) "🔒" else level.emoji,
                    fontSize = 42.sp,
                    textAlign = TextAlign.Center
                )

                // Title
                Text(
                    level.title,
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = if (isUnlocked) MaterialTheme.colorScheme.onSurface
                                else MaterialTheme.colorScheme.onSurface.copy(0.35f)
                    ),
                    textAlign = TextAlign.Center,
                    maxLines  = 1
                )

                // Info badges (only when unlocked)
                if (isUnlocked) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        BadgeChip("${level.gridSize}×${level.gridSize}", MaterialTheme.colorScheme.outline.copy(0.5f))
                        // Special feature badge
                        when {
                            level.hasTutorial        -> BadgeChip("📚 Tutorial", Color(0xFF64B5F6))
                            level.timeLimitSeconds != null -> BadgeChip("⏱ ${level.timeLimitSeconds}s", Color(0xFFF44336))
                            else -> BadgeChip(
                                when (level.difficulty) {
                                    Difficulty.EASY   -> "Easy"
                                    Difficulty.MEDIUM -> "Medium"
                                    Difficulty.HARD   -> "Hard"
                                },
                                diffColor
                            )
                        }
                    }
                } else {
                    Spacer(Modifier.height(20.dp))
                }
            }
        }
    }
}

@Composable
private fun BadgeChip(text: String, color: Color) {
    Surface(
        shape = RoundedCornerShape(50),
        color = color.copy(alpha = 0.13f)
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall.copy(color = color, fontWeight = FontWeight.Medium)
        )
    }
}
