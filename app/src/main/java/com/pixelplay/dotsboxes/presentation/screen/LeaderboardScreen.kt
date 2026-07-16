package com.pixelplay.dotsboxes.presentation.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pixelplay.dotsboxes.data.remote.FirebaseManager
import com.pixelplay.dotsboxes.data.remote.LeaderboardEntry
import com.pixelplay.dotsboxes.presentation.theme.Player1Blue
import kotlinx.coroutines.flow.catch

private val Gold   = Color(0xFFFFD700)
private val Silver = Color(0xFFB0BEC5)
private val Bronze = Color(0xFFCD7F32)

private val BgTop    = Color(0xFF0D0D2B)
private val BgBottom = Color(0xFF141430)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LeaderboardScreen(
    firebase: FirebaseManager,
    myUid: String,
    onBack: () -> Unit
) {
    var entries by remember { mutableStateOf<List<LeaderboardEntry>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        firebase.listenLeaderboard()
            .catch { loading = false }
            .collect { list ->
                entries = list
                loading = false
            }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(BgTop, BgBottom)))
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            bottomBar = { com.pixelplay.dotsboxes.presentation.ads.BannerAdView() },
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                "🏆 Daily Leaderboard",
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White
                            )
                            Text(
                                "Today's Flash Challenge",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(0.5f)
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = Color.White
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        titleContentColor = Color.White,
                        navigationIconContentColor = Color.White
                    )
                )
            }
        ) { padding ->

            when {
                loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize().padding(padding),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = Player1Blue)
                    }
                }

                entries.isEmpty() -> {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(padding),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text("⚡", fontSize = 56.sp)
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "No scores yet today",
                            style = MaterialTheme.typography.titleMedium.copy(color = Color.White)
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Be the first to play Flash Challenge!",
                            style = MaterialTheme.typography.bodySmall.copy(color = Color.White.copy(0.5f)),
                            textAlign = TextAlign.Center
                        )
                    }
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(padding),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (entries.isNotEmpty()) {
                            item { PodiumRow(entries.take(3), myUid) }
                            item { Spacer(Modifier.height(8.dp)) }
                        }

                        itemsIndexed(entries.drop(if (entries.size >= 3) 3 else 0)) { idx, entry ->
                            val rank = idx + (if (entries.size >= 3) 4 else 1)
                            LeaderboardRow(rank = rank, entry = entry, isMe = entry.uid == myUid)
                        }

                        val myRank = entries.indexOfFirst { it.uid == myUid }
                        if (myRank >= 3) {
                            item {
                                Spacer(Modifier.height(8.dp))
                                HorizontalDivider(color = Color.White.copy(0.1f))
                                Spacer(Modifier.height(8.dp))
                                LeaderboardRow(rank = myRank + 1, entry = entries[myRank], isMe = true)
                            }
                        }

                        item { Spacer(Modifier.height(16.dp)) }
                    }
                }
            }
        }
    }
}

// ── Podium (top 3) ─────────────────────────────────────────────────────────────

@Composable
private fun PodiumRow(top3: List<LeaderboardEntry>, myUid: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.Bottom
    ) {
        if (top3.size >= 2) PodiumItem(top3[1], rank = 2, height = 80.dp, color = Silver, isMe = top3[1].uid == myUid)
        PodiumItem(top3[0], rank = 1, height = 110.dp, color = Gold, isMe = top3[0].uid == myUid)
        if (top3.size >= 3) PodiumItem(top3[2], rank = 3, height = 60.dp, color = Bronze, isMe = top3[2].uid == myUid)
    }
}

@Composable
private fun PodiumItem(
    entry: LeaderboardEntry,
    rank: Int,
    height: Dp,
    color: Color,
    isMe: Boolean
) {
    val medal = when (rank) { 1 -> "🥇"; 2 -> "🥈"; else -> "🥉" }
    Column(
        modifier = Modifier.widthIn(max = 110.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(medal, fontSize = 30.sp)
        if (isMe) {
            Surface(shape = RoundedCornerShape(50), color = Player1Blue) {
                Text(
                    "You",
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = Color.White, fontWeight = FontWeight.Bold
                    )
                )
            }
        }
        Text(
            entry.name,
            style = MaterialTheme.typography.labelMedium.copy(
                color = Color.White, fontWeight = FontWeight.Bold
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
        Text(
            "${entry.score} 📦",
            style = MaterialTheme.typography.labelLarge.copy(
                color = color, fontWeight = FontWeight.ExtraBold
            )
        )
        Box(
            modifier = Modifier
                .width(80.dp)
                .height(height)
                .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                .background(color.copy(0.2f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "#$rank",
                style = MaterialTheme.typography.titleLarge.copy(
                    color = color, fontWeight = FontWeight.ExtraBold
                )
            )
        }
    }
}

// ── Single row ─────────────────────────────────────────────────────────────────

@Composable
private fun LeaderboardRow(rank: Int, entry: LeaderboardEntry, isMe: Boolean) {
    val medal     = when (rank) { 1 -> "🥇"; 2 -> "🥈"; 3 -> "🥉"; else -> null }
    val rankColor = when (rank) { 1 -> Gold; 2 -> Silver; 3 -> Bronze; else -> Player1Blue }
    val rowBg = if (isMe)
        Brush.horizontalGradient(listOf(Player1Blue.copy(0.30f), Player1Blue.copy(0.10f)))
    else
        Brush.horizontalGradient(listOf(Color.White.copy(0.07f), Color.White.copy(0.03f)))

    // Short player tag (last 4 of uid) so same-named players are still distinguishable.
    val tag = entry.uid.takeLast(4).uppercase().ifBlank { "----" }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(rowBg)
            .then(
                if (isMe) Modifier.border(1.dp, Player1Blue.copy(0.6f), RoundedCornerShape(16.dp))
                else Modifier
            )
            .padding(horizontal = 12.dp, vertical = 11.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Rank / medal
            Box(modifier = Modifier.width(30.dp), contentAlignment = Alignment.Center) {
                if (medal != null) {
                    Text(medal, fontSize = 22.sp)
                } else {
                    Text(
                        "$rank",
                        style = MaterialTheme.typography.titleMedium.copy(
                            color = Color.White.copy(0.55f),
                            fontWeight = FontWeight.ExtraBold
                        )
                    )
                }
            }

            // Avatar with rank-colored ring
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(listOf(rankColor.copy(0.5f), rankColor.copy(0.18f)))
                    )
                    .border(1.5.dp, rankColor.copy(0.55f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    entry.name.take(1).uppercase().ifBlank { "?" },
                    style = MaterialTheme.typography.titleMedium.copy(
                        color = Color.White,
                        fontWeight = FontWeight.ExtraBold
                    )
                )
            }

            // Name + "You" chip, with faded player tag underneath
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        entry.name,
                        modifier = Modifier.weight(1f, fill = false),
                        style = MaterialTheme.typography.bodyLarge.copy(
                            color = Color.White,
                            fontWeight = if (isMe) FontWeight.ExtraBold else FontWeight.SemiBold
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (isMe) {
                        Surface(shape = RoundedCornerShape(50), color = Player1Blue) {
                            Text(
                                "YOU",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = Color.White, fontWeight = FontWeight.Bold
                                )
                            )
                        }
                    }
                }
                Text(
                    "#$tag",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = Color.White.copy(0.35f),
                        fontWeight = FontWeight.Medium
                    )
                )
            }

            // Score + time
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    "${entry.score} 📦",
                    style = MaterialTheme.typography.titleMedium.copy(
                        color = if (isMe) Color(0xFF64B5F6) else Gold,
                        fontWeight = FontWeight.ExtraBold
                    )
                )
                Text(
                    "⏱ ${entry.timeTaken}s",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = Color.White.copy(0.4f)
                    )
                )
            }
        }
    }
}
