package com.pixelplay.dotsboxes.presentation.screen

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.pixelplay.dotsboxes.DotsBoxesApp
import com.pixelplay.dotsboxes.presentation.theme.Player1Blue
import com.pixelplay.dotsboxes.presentation.theme.Player2Orange
import com.pixelplay.dotsboxes.presentation.theme.Purple40
import kotlinx.coroutines.launch

private const val WEB_CLIENT_ID =
    "274960535067-7n4o1nmtev6t5mqld1flodvatcv96frp.apps.googleusercontent.com"

@Composable
fun LoginScreen(onLoggedIn: () -> Unit) {
    val context = LocalContext.current
    val app     = context.applicationContext as DotsBoxesApp

    val currentUser = remember { app.firebaseManager.currentUser }
    val currentName = remember { app.firebaseManager.currentDisplayName() }

    var guestName    by remember { mutableStateOf("") }
    var isLoading    by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showGuest    by remember { mutableStateOf(false) }
    val scope        = rememberCoroutineScope()

    val isDark = isSystemInDarkTheme()
    val bgGradient = if (isDark)
        Brush.verticalGradient(listOf(Color(0xFF0D0D2B), Color(0xFF141430)))
    else
        Brush.verticalGradient(listOf(Color(0xFFF2F0FF), Color(0xFFE8E4FF)))

    val gso = remember {
        GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(WEB_CLIENT_ID)
            .requestEmail()
            .build()
    }
    val googleClient = remember { GoogleSignIn.getClient(context, gso) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        scope.launch {
            isLoading = true
            errorMessage = null
            try {
                val task    = GoogleSignIn.getSignedInAccountFromIntent(result.data)
                val account = task.getResult(ApiException::class.java)
                val idToken = account.idToken ?: error("No ID token received")
                app.firebaseManager.signInWithGoogle(idToken)
                    .onSuccess { onLoggedIn() }
                    .onFailure { e -> errorMessage = "Firebase error: ${e.message}" }
            } catch (e: ApiException) {
                errorMessage = "Google error code: ${e.statusCode}\n" + when (e.statusCode) {
                    10    -> "SHA-1 mismatch (check Firebase console)"
                    12501 -> "Sign-in cancelled"
                    12500 -> "Sign-in failed"
                    else  -> e.message ?: "Unknown"
                }
            } catch (e: Exception) {
                errorMessage = "Error: ${e.message}"
            } finally {
                isLoading = false
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgGradient),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("🎮", fontSize = 64.sp)
            Text(
                "DOT CLASH AI",
                style = MaterialTheme.typography.headlineLarge.copy(
                    brush         = Brush.linearGradient(listOf(Player1Blue, Purple40)),
                    fontWeight    = FontWeight.ExtraBold,
                    letterSpacing = 2.sp
                )
            )
            Text(
                "Online Multiplayer",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(0.6f)
            )

            Spacer(Modifier.height(4.dp))

            // ── Continue banner (only if already signed in) ───────────────────
            if (currentUser != null) {
                Button(
                    onClick = onLoggedIn,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
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
                        Text(
                            "▶  Continue as ${currentName ?: "Player"}",
                            style = MaterialTheme.typography.labelLarge.copy(
                                color = Color.White, fontWeight = FontWeight.ExtraBold
                            )
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    HorizontalDivider(Modifier.weight(1f),
                        color = MaterialTheme.colorScheme.outline.copy(0.25f))
                    Text("or switch account",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(0.4f))
                    HorizontalDivider(Modifier.weight(1f),
                        color = MaterialTheme.colorScheme.outline.copy(0.25f))
                }
            }

            // ── Google Sign-In ────────────────────────────────────────────────
            Button(
                onClick  = { launcher.launch(googleClient.signInIntent) },
                enabled  = !isLoading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape  = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White,
                    contentColor   = Color(0xFF3C4043)
                ),
                elevation = ButtonDefaults.buttonElevation(4.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    Text("G", style = MaterialTheme.typography.titleLarge.copy(
                        color = Color(0xFF4285F4), fontWeight = FontWeight.ExtraBold
                    ))
                    Text(
                        "Sign in with Google",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.Medium
                        )
                    )
                }
            }

            // ── Divider ───────────────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                HorizontalDivider(Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.outline.copy(0.25f))
                Text("or",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(0.4f))
                HorizontalDivider(Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.outline.copy(0.25f))
            }

            // ── Guest mode ────────────────────────────────────────────────────
            if (!showGuest) {
                OutlinedButton(
                    onClick  = { showGuest = true },
                    enabled  = !isLoading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(50)
                ) {
                    Text("👤  Play as Guest", style = MaterialTheme.typography.labelLarge)
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value         = guestName,
                        onValueChange = { guestName = it.take(20) },
                        label         = { Text("Your name") },
                        placeholder   = { Text("e.g. Sandeep") },
                        modifier      = Modifier.fillMaxWidth(),
                        shape         = RoundedCornerShape(14.dp),
                        singleLine    = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = {
                            if (guestName.isNotBlank()) {
                                isLoading = true
                                scope.launch {
                                    app.firebaseManager.signInAsGuest()
                                        .onSuccess {
                                            app.firebaseManager.updateDisplayName(guestName.trim())
                                            onLoggedIn()
                                        }
                                        .onFailure { e -> errorMessage = e.message }
                                    isLoading = false
                                }
                            }
                        })
                    )
                    Button(
                        onClick = {
                            if (guestName.isNotBlank()) {
                                isLoading = true
                                scope.launch {
                                    app.firebaseManager.signInAsGuest()
                                        .onSuccess {
                                            app.firebaseManager.updateDisplayName(guestName.trim())
                                            onLoggedIn()
                                        }
                                        .onFailure { e -> errorMessage = e.message }
                                    isLoading = false
                                }
                            }
                        },
                        enabled  = guestName.isNotBlank() && !isLoading,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        shape  = RoundedCornerShape(50),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.horizontalGradient(listOf(Player2Orange, Color(0xFFFF9800))),
                                    RoundedCornerShape(50)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "Continue as Guest",
                                style = MaterialTheme.typography.labelLarge.copy(
                                    color = Color.White, fontWeight = FontWeight.Bold
                                )
                            )
                        }
                    }
                }
            }

            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(28.dp))
            }

            errorMessage?.let { msg ->
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
                TextButton(onClick = { errorMessage = null }) { Text("Dismiss") }
            }
        }
    }
}
