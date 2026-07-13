package com.pixelplay.dotsboxes.presentation.screen

import android.content.Intent
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pixelplay.dotsboxes.DotsBoxesApp
import com.pixelplay.dotsboxes.domain.model.OnlinePlayer
import com.pixelplay.dotsboxes.domain.model.RoomStatus
import com.pixelplay.dotsboxes.presentation.theme.Player1Blue
import com.pixelplay.dotsboxes.presentation.theme.Player2Orange
import com.pixelplay.dotsboxes.presentation.theme.Purple40
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val WAIT_TIMEOUT_SECONDS = 120   // auto-abandon room after 2 min of no join

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnlineLobbyScreen(
    player: OnlinePlayer,
    playerStats: com.pixelplay.dotsboxes.domain.model.PlayerStats =
        com.pixelplay.dotsboxes.domain.model.PlayerStats(),
    onGameReady: (roomCode: String, isHost: Boolean, myName: String, gridSize: Int) -> Unit,
    onSignOut: () -> Unit,
    onBack: () -> Unit
) {
    val context   = LocalContext.current
    val app       = context.applicationContext as DotsBoxesApp
    val scope     = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current

    var joinCode     by remember { mutableStateOf("") }
    var autofillHint by remember { mutableStateOf<String?>(null) }
    var createdCode  by remember { mutableStateOf<String?>(null) }
    var isLoading    by remember { mutableStateOf(false) }
    var errorMsg     by remember { mutableStateOf<String?>(null) }
    var isWaiting    by remember { mutableStateOf(false) }
    var waitSecondsLeft by remember { mutableStateOf(WAIT_TIMEOUT_SECONDS) }
    val defaultGrid  = 4  // fixed grid size for online

    // First N online games/day are free (Remote Config); after that, ASK to watch a rewarded ad.
    val freeGames = com.pixelplay.dotsboxes.config.RemoteConfig.freeOnlineGames
    val freeGamesLeft = (freeGames - playerStats.todayOnlineGames).coerceAtLeast(0)
    var pendingGatedAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    fun gatedProceed(proceed: () -> Unit) {
        if (freeGamesLeft > 0) proceed()
        else pendingGatedAction = proceed   // shows the "watch ad?" dialog
    }

    // Confirmation before playing the rewarded ad (user-initiated → AdMob-policy safe)
    pendingGatedAction?.let { action ->
        AlertDialog(
            onDismissRequest = { pendingGatedAction = null },
            icon  = { Text("🎬", fontSize = 34.sp) },
            title = { Text("Daily free games used") },
            text  = { Text("You've played your $freeGames free online games today. Watch a short ad to play another match?") },
            confirmButton = {
                Button(onClick = {
                    pendingGatedAction = null
                    val activity = context as? android.app.Activity
                    if (activity == null) { action(); return@Button }
                    com.pixelplay.dotsboxes.analytics.Analytics.onlineGatedByAd()
                    app.rewardedAd.show(
                        activity   = activity,
                        coins      = 0,
                        onRewarded = {
                            com.pixelplay.dotsboxes.analytics.Analytics.adWatched("rewarded", "online_gate")
                            action()
                        },
                        onFailed   = { errorMsg = "Ad not ready — try again in a moment." }
                    )
                }) { Text("🎬  Watch Ad") }
            },
            dismissButton = {
                TextButton(onClick = { pendingGatedAction = null }) { Text("Maybe later") }
            }
        )
    }

    val isDark = isSystemInDarkTheme()
    val bgGradient = if (isDark)
        Brush.verticalGradient(listOf(Color(0xFF0D0D2B), Color(0xFF141430)))
    else
        Brush.verticalGradient(listOf(Color(0xFFF2F0FF), Color(0xFFE8E4FF)))

    // Tie analytics to this (anonymous/Google) auth user for stable identity
    LaunchedEffect(Unit) {
        com.pixelplay.dotsboxes.analytics.Analytics.setUser(app.firebaseManager.currentUser?.uid)
    }

    // Deep-link invite → prefill (reacts whenever a new code arrives, even if already composed)
    LaunchedEffect(app.pendingInviteCode) {
        app.pendingInviteCode?.let { invite ->
            joinCode = invite
            autofillHint = "🔗 Code from invite link"
            com.pixelplay.dotsboxes.analytics.Analytics.inviteOpened()
            app.pendingInviteCode = null
        }
    }

    // Fall back to clipboard on first open (friend copied the shared invite/code)
    LaunchedEffect(Unit) {
        if (joinCode.isNotEmpty()) return@LaunchedEffect
        val raw = clipboard.getText()?.text?.trim() ?: return@LaunchedEffect
        val codePattern = "[A-HJ-NP-Z2-9]{6}"   // matches FirebaseManager's code alphabet
        val code = when {
            // Clipboard is exactly a 6-char code
            raw.uppercase().matches(Regex(codePattern)) -> raw.uppercase()
            // Extract from invite text like "...Room code: A7X9K2..."
            else -> Regex("code[:\\s]+($codePattern)", RegexOption.IGNORE_CASE)
                .find(raw)?.groupValues?.getOrNull(1)?.uppercase()
        }
        if (code != null) {
            joinCode = code
            autofillHint = "📋 Code pasted from clipboard"
        }
    }

    // While waiting for guest: listen for room status change → PLAYING
    LaunchedEffect(createdCode, isWaiting) {
        val code = createdCode ?: return@LaunchedEffect
        if (!isWaiting) return@LaunchedEffect
        app.firebaseManager.listenToRoom(code).collect { room ->
            if (room?.status == RoomStatus.PLAYING) {
                onGameReady(code, true, player.displayName, room.gridSize)
            }
        }
    }

    // Waiting timeout: if nobody joins within WAIT_TIMEOUT_SECONDS → auto-abandon the room
    LaunchedEffect(createdCode, isWaiting) {
        if (!isWaiting || createdCode == null) return@LaunchedEffect
        waitSecondsLeft = WAIT_TIMEOUT_SECONDS
        while (waitSecondsLeft > 0 && isWaiting) {
            kotlinx.coroutines.delay(1000L)
            waitSecondsLeft -= 1
        }
        if (isWaiting && waitSecondsLeft <= 0) {
            createdCode?.let { app.firebaseManager.abandonRoom(it) }
            createdCode = null
            isWaiting   = false
            errorMsg    = "⏳ No one joined in 2 minutes — room closed. Create a new one."
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("🌐 Online Play") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    TextButton(onClick = onSignOut) {
                        Text("Sign Out", color = MaterialTheme.colorScheme.error)
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
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // ── Player info card ──────────────────────────────────────────
                PlayerInfoCard(player)

                if (isWaiting && createdCode != null) {
                    // ── Waiting for opponent screen ───────────────────────────
                    WaitingForOpponent(
                        code      = createdCode!!,
                        secondsLeft = waitSecondsLeft,
                        onCancel  = {
                            scope.launch {
                                app.firebaseManager.abandonRoom(createdCode!!)
                                createdCode = null
                                isWaiting   = false
                            }
                        },
                        onShare   = {
                            val code = createdCode!!
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT,
                                    "🎮 Dot Clash AI mein mujhse khelo!\n" +
                                    "Room code: $code\n\n" +
                                    "👉 Tap to join: https://dotclashai.web.app/join?code=$code\n\n" +
                                    "App na ho to install karo:\n" +
                                    "https://play.google.com/store/apps/details?id=${context.packageName}")
                            }
                            com.pixelplay.dotsboxes.analytics.Analytics.inviteShared()
                            context.startActivity(Intent.createChooser(shareIntent, "Invite friend"))
                        },
                        onCopy    = { clipboard.setText(AnnotatedString(createdCode!!)) }
                    )
                } else {
                    // Free-games / watch-ad hint
                    Text(
                        if (freeGamesLeft > 0) "🎮 Free online games left today: $freeGamesLeft"
                        else "🎬 Daily free games used — watch a short ad to play more",
                        style = MaterialTheme.typography.labelMedium.copy(
                            color = if (freeGamesLeft > 0) Color(0xFF4CAF50) else Color(0xFFFFB300)
                        )
                    )

                    // ── Create room ───────────────────────────────────────────
                    Button(
                        onClick = {
                            gatedProceed {
                                scope.launch {
                                    isLoading = true
                                    errorMsg  = null
                                    app.firebaseManager.createRoom(player.displayName, defaultGrid)
                                        .onSuccess { code ->
                                            com.pixelplay.dotsboxes.analytics.Analytics.onlineRoomCreated()
                                            createdCode = code
                                            isWaiting   = true
                                        }
                                        .onFailure { e -> errorMsg = e.message }
                                    isLoading = false
                                }
                            }
                        },
                        enabled  = !isLoading,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp),
                        shape  = RoundedCornerShape(50),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                        contentPadding = PaddingValues(0.dp)
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
                            Text("🏠  Create Room",
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontSize = 16.sp, color = Color.White, fontWeight = FontWeight.Bold
                                ))
                        }
                    }

                    // ── Divider ───────────────────────────────────────────────
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        HorizontalDivider(Modifier.weight(1f),
                            color = MaterialTheme.colorScheme.outline.copy(0.25f))
                        Text("or join", style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(0.45f))
                        HorizontalDivider(Modifier.weight(1f),
                            color = MaterialTheme.colorScheme.outline.copy(0.25f))
                    }

                    // ── Join room ─────────────────────────────────────────────
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value         = joinCode,
                            onValueChange = { joinCode = it.uppercase().take(6); autofillHint = null },
                            label         = { Text("Room Code") },
                            placeholder   = { Text("ABC123") },
                            modifier      = Modifier.weight(1f),
                            shape         = RoundedCornerShape(14.dp),
                            singleLine    = true,
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.Characters,
                                imeAction = ImeAction.Done
                            ),
                            keyboardActions = KeyboardActions(onDone = {
                                if (joinCode.length == 6) gatedProceed {
                                    scope.launch {
                                        isLoading = true
                                        errorMsg  = null
                                        app.firebaseManager.joinRoom(joinCode, player.displayName)
                                            .onSuccess { room ->
                                                com.pixelplay.dotsboxes.analytics.Analytics.onlineJoined()
                                                onGameReady(joinCode, false, player.displayName, room.gridSize)
                                            }
                                            .onFailure { e -> errorMsg = e.message }
                                        isLoading = false
                                    }
                                }
                            })
                        )
                        Button(
                            onClick = {
                                gatedProceed {
                                    scope.launch {
                                        isLoading = true
                                        errorMsg  = null
                                        app.firebaseManager.joinRoom(joinCode, player.displayName)
                                            .onSuccess { room ->
                                                com.pixelplay.dotsboxes.analytics.Analytics.onlineJoined()
                                                onGameReady(joinCode, false, player.displayName, room.gridSize)
                                            }
                                            .onFailure { e -> errorMsg = e.message }
                                        isLoading = false
                                    }
                                }
                            },
                            enabled = joinCode.length == 6 && !isLoading,
                            shape   = RoundedCornerShape(14.dp),
                            colors  = ButtonDefaults.buttonColors(
                                containerColor = Player2Orange
                            ),
                            modifier = Modifier.height(56.dp)
                        ) {
                            Text("Join", style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.Bold, color = Color.White
                            ))
                        }
                    }

                    autofillHint?.let { hint ->
                        Text(
                            hint,
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = Color(0xFF4CAF50), fontWeight = FontWeight.Medium
                            )
                        )
                    }
                }

                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(28.dp))
                }

                errorMsg?.let { msg ->
                    Card(
                        shape  = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Text(
                            msg,
                            modifier  = Modifier.padding(12.dp),
                            style     = MaterialTheme.typography.bodySmall,
                            color     = MaterialTheme.colorScheme.onErrorContainer,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PlayerInfoCard(player: OnlinePlayer) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(18.dp),
        colors   = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(0.4f)
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(50.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(listOf(Player1Blue, Purple40))
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    player.displayName.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                    style = MaterialTheme.typography.titleLarge.copy(
                        color = Color.White, fontWeight = FontWeight.ExtraBold
                    )
                )
            }
            Column {
                Text(
                    player.displayName,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold)
                )
                Text(
                    if (player.isGuest) "👤 Guest" else "✅ ${player.email ?: "Google"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(0.55f)
                )
            }
        }
    }
}

@Composable
private fun WaitingForOpponent(
    code: String,
    secondsLeft: Int,
    onCancel: () -> Unit,
    onShare: () -> Unit,
    onCopy: () -> Unit
) {
    val dots by rememberInfiniteTransition(label = "dots").animateFloat(
        initialValue  = 0f,
        targetValue   = 3f,
        animationSpec = infiniteRepeatable(tween(900)),
        label         = "dotsAnim"
    )
    val dotStr = ".".repeat(dots.toInt() + 1)
    val mm = secondsLeft / 60
    val ss = secondsLeft % 60
    val timeStr = "%d:%02d".format(mm, ss)

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("⏳", fontSize = 48.sp)
        Text(
            "Waiting for opponent$dotStr",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(0.7f)
        )
        Text(
            "Room closes in $timeStr",
            style = MaterialTheme.typography.labelLarge.copy(
                fontWeight = FontWeight.Bold,
                color = if (secondsLeft <= 30) Color(0xFFE53935) else MaterialTheme.colorScheme.primary
            )
        )

        // Room code big display
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape    = RoundedCornerShape(20.dp),
            colors   = CardDefaults.cardColors(
                containerColor = Color(0xFF1A237E).copy(0.5f)
            )
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "Share this code with your friend:",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(0.7f)
                )
                Text(
                    code,
                    style = MaterialTheme.typography.displaySmall.copy(
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFFFFD700),
                        letterSpacing = 6.sp
                    )
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = onCopy,
                        shape   = RoundedCornerShape(50),
                        colors  = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                        border  = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(0.4f))
                    ) {
                        Text("📋 Copy")
                    }
                    Button(
                        onClick = onShare,
                        shape   = RoundedCornerShape(50),
                        colors  = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF25D366)
                        )
                    ) {
                        Text("📤 Share", style = MaterialTheme.typography.labelLarge.copy(
                            color = Color.White))
                    }
                }
            }
        }

        TextButton(onClick = onCancel, colors = ButtonDefaults.textButtonColors(
            contentColor = MaterialTheme.colorScheme.error
        )) {
            Text("Cancel")
        }
    }
}
