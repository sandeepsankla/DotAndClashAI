package com.pixelplay.dotsboxes.presentation.screen

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pixelplay.dotsboxes.domain.model.BoardSkin
import com.pixelplay.dotsboxes.domain.model.PlayerStats
import com.pixelplay.dotsboxes.presentation.theme.Player1Blue
import com.pixelplay.dotsboxes.presentation.theme.Player2Orange
import kotlinx.coroutines.launch

// ── Store item definitions ─────────────────────────────────────────────────────

private data class SkinOffer(
    val skin: BoardSkin,
    val cost: Int,
    val gradient: List<Color>
)

private data class HintOffer(
    val label: String,
    val hints: Int,
    val cost: Int,
    val emoji: String
)

private val SKIN_OFFERS = listOf(
    SkinOffer(BoardSkin.FIRE,   50,  listOf(Color(0xFFFF6F00), Color(0xFFFF1744))),
    SkinOffer(BoardSkin.GOLDEN, 100, listOf(Color(0xFFFFD700), Color(0xFFFF8F00)))
)

private val HINT_OFFERS = listOf(
    HintOffer("Starter Pack",  5,  30,  "💡"),
    HintOffer("Value Pack",   15,  80,  "🔦"),
    HintOffer("Mega Pack",    30, 150,  "⚡")
)

private val coinYellow   = Color(0xFFFFD700)
private val coinDarkGold = Color(0xFFC07800)

// ── Screen ────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RewardStoreScreen(
    playerStats: PlayerStats,
    onPurchaseSkin: (BoardSkin, Int) -> Unit,
    onActivateSkin: (BoardSkin) -> Unit,
    onPurchaseHints: (Int, Int) -> Unit,
    onAvatarChanged: (Int, Int) -> Unit = { _, _ -> },
    onBack: () -> Unit
) {
    val isDark    = isSystemInDarkTheme()
    val coinColor = if (isDark) coinYellow else coinDarkGold
    val snackbarHost = remember { SnackbarHostState() }
    val scope        = rememberCoroutineScope()
    var showAvatarSheet by remember { mutableStateOf(false) }

    val bgGradient = if (isDark)
        Brush.verticalGradient(listOf(Color(0xFF0D0D2B), Color(0xFF141430)))
    else
        Brush.verticalGradient(listOf(Color(0xFFF2F0FF), Color(0xFFE8E4FF)))

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHost) },
        bottomBar = { com.pixelplay.dotsboxes.presentation.ads.BannerAdView() },
        topBar = {
            TopAppBar(
                title = { Text("🛍️ Reward Store") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    // DotCoins balance badge
                    Card(
                        modifier = Modifier.padding(end = 12.dp),
                        shape    = RoundedCornerShape(50),
                        colors   = CardDefaults.cardColors(
                            containerColor = coinColor.copy(alpha = 0.15f)
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text("🪙", fontSize = 16.sp)
                            Text(
                                "${playerStats.dotCoins}",
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    color = coinColor
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
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 24.dp, top = 8.dp)
            ) {
                // ── How to earn banner ────────────────────────────────────────
                item {
                    EarnInfoBanner()
                }

                // ── Avatars section ───────────────────────────────────────────
                item {
                    SectionHeader("😎 Avatars", "Buy with coins — show off your style")
                    Spacer(Modifier.height(8.dp))
                    AvatarStoreCard(
                        playerStats = playerStats,
                        onClick     = { showAvatarSheet = true }
                    )
                    Spacer(Modifier.height(4.dp))
                }

                // ── Skins section ─────────────────────────────────────────────
                item {
                    SectionHeader("🎨 Board Themes", "Permanent unlock — use any time")
                }

                items(SKIN_OFFERS) { offer ->
                    val owned  = playerStats.unlockedSkins.contains(offer.skin.name)
                    val active = playerStats.activeSkinEnum == offer.skin
                    SkinCard(
                        offer      = offer,
                        owned      = owned,
                        active     = active,
                        canAfford  = playerStats.canAfford(offer.cost),
                        coinColor  = coinColor,
                        onBuy      = {
                            onPurchaseSkin(offer.skin, offer.cost)
                            scope.launch {
                                snackbarHost.showSnackbar(
                                    "✅ ${offer.skin.displayName} skin unlocked & activated!"
                                )
                            }
                        },
                        onActivate = {
                            onActivateSkin(offer.skin)
                            scope.launch {
                                snackbarHost.showSnackbar(
                                    "🎨 ${offer.skin.displayName} skin activated!"
                                )
                            }
                        }
                    )
                }

                // ── Hint packs section ────────────────────────────────────────
                item {
                    Spacer(Modifier.height(4.dp))
                    SectionHeader("💡 Hint Packs", "Use on Hard mode to see best move")
                    Spacer(Modifier.height(8.dp))
                    WatchAdHintsCard(onEarned = { onPurchaseHints(3, 0) })
                    Spacer(Modifier.height(4.dp))
                }

                items(HINT_OFFERS) { offer ->
                    HintPackCard(
                        offer     = offer,
                        canAfford = playerStats.canAfford(offer.cost),
                        coinColor = coinColor,
                        onBuy     = {
                            onPurchaseHints(offer.hints, offer.cost)
                            scope.launch {
                                snackbarHost.showSnackbar(
                                    "✅ +${offer.hints} hints added! Total: ${playerStats.hintCoins + offer.hints}"
                                )
                            }
                        }
                    )
                }

                // ── Current hints balance ─────────────────────────────────────
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape    = RoundedCornerShape(16.dp),
                        colors   = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(0.5f)
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    "💡 Current hint balance",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface.copy(0.7f)
                                )
                                val daysLeft = playerStats.hintsExpiryDaysLeft
                                if (playerStats.hintCoins > 0 && daysLeft in 0..2) {
                                    Text(
                                        if (daysLeft <= 0) "⏳ Expires today — use them!"
                                        else "⏳ Expires in $daysLeft day${if (daysLeft > 1) "s" else ""}",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = Color(0xFFFF7043)
                                        )
                                    )
                                }
                            }
                            Text(
                                "${playerStats.hintCoins} hints",
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    color = coinColor
                                )
                            )
                        }
                    }
                }
            }
        }
    }

    // ── Avatar picker sheet ───────────────────────────────────────────────────
    if (showAvatarSheet) {
        AvatarPickerSheet(
            playerStats = playerStats,
            onSelect    = { avatar ->
                onAvatarChanged(
                    avatar.id,
                    if (playerStats.isAvatarUnlocked(avatar.id)) 0 else avatar.cost
                )
                showAvatarSheet = false
            },
            onDismiss   = { showAvatarSheet = false }
        )
    }
}

// ── Avatar store card ─────────────────────────────────────────────────────────

@Composable
private fun AvatarStoreCard(playerStats: PlayerStats, onClick: () -> Unit) {
    val active = com.pixelplay.dotsboxes.domain.model.ALL_AVATARS
        .find { it.id == playerStats.activeAvatarId }
        ?: com.pixelplay.dotsboxes.domain.model.ALL_AVATARS[0]
    val owned  = playerStats.unlockedAvatarIds.size
    val total  = com.pixelplay.dotsboxes.domain.model.ALL_AVATARS.size

    Card(
        onClick  = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(16.dp),
        colors   = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.horizontalGradient(listOf(Color(0xFF2A1A4E), Color(0xFF1A2E4E))),
                    RoundedCornerShape(16.dp)
                )
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(50))
                    .background(
                        Brush.radialGradient(listOf(Player1Blue.copy(0.5f), Color(0xFF0D0D2B)))
                    )
                    .border(2.dp, Player1Blue.copy(0.6f), RoundedCornerShape(50)),
                contentAlignment = Alignment.Center
            ) {
                Text(active.emoji, fontSize = 28.sp)
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    "Change Avatar",
                    style = MaterialTheme.typography.titleMedium.copy(
                        color = Color.White, fontWeight = FontWeight.ExtraBold
                    )
                )
                Text(
                    "$owned / $total unlocked · tap to browse",
                    style = MaterialTheme.typography.bodySmall.copy(color = Color.White.copy(0.55f))
                )
            }
            Text("›", fontSize = 26.sp, color = Color.White.copy(0.4f))
        }
    }
}

// ── Earn info banner ──────────────────────────────────────────────────────────

@Composable
private fun WatchAdHintsCard(onEarned: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val app = context.applicationContext as com.pixelplay.dotsboxes.DotsBoxesApp
    val snackScope = rememberCoroutineScope()
    Card(
        onClick  = {
            val activity = context as? android.app.Activity
            if (activity != null) {
                app.rewardedAd.show(
                    activity  = activity,
                    coins     = 3,
                    onRewarded = {
                        com.pixelplay.dotsboxes.analytics.Analytics.adWatched("rewarded", "store_hints")
                        onEarned()
                    },
                    onFailed  = { }
                )
            }
        },
        modifier = Modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(16.dp),
        colors   = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.horizontalGradient(listOf(Color(0xFF1B5E20), Color(0xFF0D3B14))),
                    RoundedCornerShape(16.dp)
                )
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("🎬", fontSize = 30.sp)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Free Hints",
                    style = MaterialTheme.typography.titleMedium.copy(
                        color = Color.White, fontWeight = FontWeight.ExtraBold
                    )
                )
                Text(
                    "Watch a short video → +3 hints",
                    style = MaterialTheme.typography.bodySmall.copy(color = Color.White.copy(0.6f))
                )
            }
            Surface(shape = RoundedCornerShape(50), color = Color(0xFF4CAF50)) {
                Text(
                    "Watch",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.labelLarge.copy(
                        color = Color.White, fontWeight = FontWeight.Bold
                    )
                )
            }
        }
    }
}

@Composable
private fun EarnInfoBanner() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(16.dp),
        colors   = CardDefaults.cardColors(
            containerColor = Color(0xFF1A237E).copy(alpha = 0.6f)
        )
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("🪙", fontSize = 28.sp)
            Column {
                Text(
                    "Earn DotCoins by winning!",
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White
                    )
                )
                Text(
                    "2 coins per box you capture when you win a game",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = Color.White.copy(alpha = 0.7f)
                    )
                )
            }
        }
    }
}

// ── Section header ────────────────────────────────────────────────────────────

@Composable
private fun SectionHeader(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold)
        )
        Text(
            subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(0.5f)
        )
    }
}

// ── Skin card ─────────────────────────────────────────────────────────────────

@Composable
private fun SkinCard(
    offer: SkinOffer,
    owned: Boolean,
    active: Boolean,
    canAfford: Boolean,
    coinColor: Color,
    onBuy: () -> Unit,
    onActivate: () -> Unit
) {
    val borderColor by animateColorAsState(
        targetValue = when {
            active -> Color(0xFFFFD700)
            owned  -> Color(0xFF4CAF50)
            else   -> Color.Transparent
        },
        animationSpec = tween(300),
        label = "skinBorder"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (owned) Modifier.border(2.dp, borderColor, RoundedCornerShape(18.dp))
                else Modifier
            ),
        shape  = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Skin preview gradient circle
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(androidx.compose.foundation.shape.CircleShape)
                    .background(Brush.radialGradient(offer.gradient)),
                contentAlignment = Alignment.Center
            ) {
                Text(offer.skin.emoji, fontSize = 26.sp)
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    offer.skin.displayName,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.ExtraBold
                    )
                )
                Text(
                    when {
                        active -> "✨ Active"
                        owned  -> "✅ Owned — tap to activate"
                        else   -> "🪙 ${offer.cost} DotCoins"
                    },
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = when {
                            active -> Color(0xFFFFD700)
                            owned  -> Color(0xFF4CAF50)
                            !canAfford -> MaterialTheme.colorScheme.onSurface.copy(0.45f)
                            else   -> coinColor
                        }
                    )
                )
            }

            if (!owned) {
                Button(
                    onClick  = onBuy,
                    enabled  = canAfford,
                    shape    = RoundedCornerShape(50),
                    colors   = ButtonDefaults.buttonColors(
                        containerColor    = Color.Transparent,
                        disabledContainerColor = Color.Transparent
                    ),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .height(38.dp)
                            .widthIn(min = 90.dp)
                            .background(
                                if (canAfford)
                                    Brush.horizontalGradient(offer.gradient)
                                else
                                    Brush.horizontalGradient(
                                        listOf(Color.Gray.copy(0.3f), Color.Gray.copy(0.2f))
                                    ),
                                RoundedCornerShape(50)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            if (canAfford) "Buy" else "Need\n🪙${offer.cost}",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.ExtraBold,
                                color = if (canAfford) Color.White
                                        else MaterialTheme.colorScheme.onSurface.copy(0.4f),
                                textAlign = TextAlign.Center
                            )
                        )
                    }
                }
            } else if (active) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = Color(0xFFFFD700).copy(0.18f)
                ) {
                    Text(
                        "✨ Active",
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.labelMedium.copy(
                            color = Color(0xFFFFD700),
                            fontWeight = FontWeight.ExtraBold
                        )
                    )
                }
            } else {
                Button(
                    onClick        = onActivate,
                    shape          = RoundedCornerShape(50),
                    colors         = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF4CAF50).copy(0.15f)
                    ),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text(
                        "Activate",
                        style = MaterialTheme.typography.labelMedium.copy(
                            color = Color(0xFF4CAF50),
                            fontWeight = FontWeight.ExtraBold
                        )
                    )
                }
            }
        }
    }
}

// ── Hint pack card ────────────────────────────────────────────────────────────

@Composable
private fun HintPackCard(
    offer: HintOffer,
    canAfford: Boolean,
    coinColor: Color,
    onBuy: () -> Unit
) {
    val packColor = when (offer.hints) {
        5    -> Player1Blue
        15   -> Color(0xFF9C27B0)
        else -> Player2Orange
    }

    Card(
        modifier  = Modifier.fillMaxWidth(),
        shape     = RoundedCornerShape(18.dp),
        colors    = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Icon circle
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(androidx.compose.foundation.shape.CircleShape)
                    .background(packColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Text(offer.emoji, fontSize = 28.sp)
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    offer.label,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.ExtraBold
                    )
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "+${offer.hints} hints",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = packColor,
                            fontWeight = FontWeight.Medium
                        )
                    )
                    Text(
                        "•",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(0.3f)
                    )
                    Text(
                        "🪙 ${offer.cost}",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = if (canAfford) coinColor
                                    else MaterialTheme.colorScheme.onSurface.copy(0.4f)
                        )
                    )
                }
            }

            Button(
                onClick  = onBuy,
                enabled  = canAfford,
                shape    = RoundedCornerShape(50),
                colors   = ButtonDefaults.buttonColors(
                    containerColor         = packColor,
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Text(
                    "Buy",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.ExtraBold,
                        color = if (canAfford) Color.White
                                else MaterialTheme.colorScheme.onSurface.copy(0.35f)
                    )
                )
            }
        }
    }
}
