package com.pixelplay.dotsboxes.presentation.screen

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pixelplay.dotsboxes.presentation.theme.Player1Blue
import com.pixelplay.dotsboxes.presentation.theme.Player2Orange
import com.pixelplay.dotsboxes.presentation.theme.Purple40
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val PLAY_STORE_URL =
    "https://play.google.com/store/apps/details?id=com.pixelplay.dotsboxes"
private const val FEEDBACK_EMAIL = "sandeep.jan19@gmail.com"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RateUsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val isDark  = isSystemInDarkTheme()
    val scope   = rememberCoroutineScope()

    var rating       by remember { mutableIntStateOf(0) }
    var feedback     by remember { mutableStateOf("") }
    var submitted    by remember { mutableStateOf(false) }
    var starAnimated by remember { mutableIntStateOf(-1) }

    val bgGradient = if (isDark)
        Brush.verticalGradient(listOf(Color(0xFF0D0D2B), Color(0xFF141430)))
    else
        Brush.verticalGradient(listOf(Color(0xFFF2F0FF), Color(0xFFE8E4FF)))

    val headerEmoji = when {
        submitted   -> "🎉"
        rating == 0 -> "🎮"
        rating <= 2 -> "😔"
        rating == 3 -> "🙂"
        rating == 4 -> "😊"
        else        -> "🤩"
    }
    val headerMsg = when {
        submitted   -> "Thank you!"
        rating == 0 -> "How do you like\nDot Clash AI?"
        rating <= 2 -> "We're sorry to hear that.\nTell us what went wrong."
        rating == 3 -> "Thanks! What can\nwe do better?"
        rating == 4 -> "Great! We'd love your\nreview on Play Store."
        else        -> "Woohoo! You're awesome!\nRate us on Play Store?"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Rate Us", fontWeight = FontWeight.Bold) },
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
                .padding(padding),
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier  = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                shape     = RoundedCornerShape(28.dp),
                colors    = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(0.92f)
                ),
                elevation = CardDefaults.cardElevation(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    if (!submitted) {
                        // ── Header emoji ──────────────────────────────────────
                        val emojiScale = remember { Animatable(0f) }
                        LaunchedEffect(headerEmoji) {
                            emojiScale.snapTo(0.6f)
                            emojiScale.animateTo(
                                1f,
                                spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium)
                            )
                        }
                        Text(headerEmoji, fontSize = 64.sp,
                            modifier = Modifier.scale(emojiScale.value))

                        Text(
                            headerMsg,
                            style     = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.ExtraBold
                            ),
                            textAlign = TextAlign.Center
                        )

                        // ── Stars ─────────────────────────────────────────────
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment     = Alignment.CenterVertically
                        ) {
                            (1..5).forEach { star ->
                                val scale = remember { Animatable(1f) }
                                val filled = star <= rating
                                val starColor = when {
                                    filled && star <= 2 -> Color(0xFFFF5722)
                                    filled && star == 3 -> Color(0xFFFFB300)
                                    filled              -> Color(0xFFFFD700)
                                    else                -> MaterialTheme.colorScheme.outline.copy(0.4f)
                                }
                                Icon(
                                    imageVector = if (filled) Icons.Default.Star
                                                  else        Icons.Default.StarOutline,
                                    contentDescription = "$star star",
                                    tint     = starColor,
                                    modifier = Modifier
                                        .size(44.dp)
                                        .scale(scale.value)
                                        .clickable(
                                            indication        = null,
                                            interactionSource = remember { MutableInteractionSource() }
                                        ) {
                                            rating = star
                                            scope.launch {
                                                scale.animateTo(1.35f, tween(80))
                                                scale.animateTo(1f, spring(Spring.DampingRatioMediumBouncy))
                                            }
                                        }
                                )
                            }
                        }

                        // ── Feedback box (rating ≤ 4) ─────────────────────────
                        if (rating in 1..4) {
                            OutlinedTextField(
                                value         = feedback,
                                onValueChange = { feedback = it.take(300) },
                                modifier      = Modifier
                                    .fillMaxWidth()
                                    .height(110.dp),
                                placeholder   = {
                                    Text(
                                        if (rating <= 3) "Tell us what can be improved…"
                                        else             "Any suggestions for us? (optional)",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                },
                                shape         = RoundedCornerShape(14.dp),
                                keyboardOptions = KeyboardOptions(
                                    capitalization = KeyboardCapitalization.Sentences
                                )
                            )
                        }

                        // ── Primary action button ─────────────────────────────
                        if (rating > 0) {
                            Button(
                                onClick = {
                                    if (rating >= 4) {
                                        // Open Play Store
                                        runCatching {
                                            context.startActivity(
                                                Intent(Intent.ACTION_VIEW, Uri.parse(PLAY_STORE_URL))
                                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                            )
                                        }
                                        submitted = true
                                    } else {
                                        // Send feedback email
                                        val subject = "Dot Clash AI Feedback (${rating}★)"
                                        val body    = feedback.ifBlank { "No feedback provided." }
                                        val uri     = Uri.parse(
                                            "mailto:$FEEDBACK_EMAIL?subject=${Uri.encode(subject)}&body=${Uri.encode(body)}"
                                        )
                                        runCatching {
                                            context.startActivity(
                                                Intent(Intent.ACTION_SENDTO, uri)
                                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                            )
                                        }
                                        submitted = true
                                    }
                                },
                                modifier       = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp),
                                shape          = RoundedCornerShape(50),
                                colors         = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(
                                            Brush.horizontalGradient(
                                                if (rating >= 4)
                                                    listOf(Color(0xFFFFD700), Color(0xFFFFA000))
                                                else
                                                    listOf(Player1Blue, Purple40)
                                            ),
                                            RoundedCornerShape(50)
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        when {
                                            rating >= 4 -> "⭐ Rate on Play Store"
                                            else        -> "📧 Send Feedback"
                                        },
                                        style = MaterialTheme.typography.labelLarge.copy(
                                            color      = Color.White,
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize   = 15.sp
                                        )
                                    )
                                }
                            }
                        }

                        TextButton(onClick = onBack) {
                            Text(
                                "Maybe Later",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(0.4f)
                            )
                        }

                    } else {
                        // ── Submitted state ───────────────────────────────────
                        val confettiScale = remember { Animatable(0f) }
                        LaunchedEffect(Unit) {
                            confettiScale.animateTo(
                                1f,
                                spring(Spring.DampingRatioLowBouncy, Spring.StiffnessMediumLow)
                            )
                        }
                        Text("🎉", fontSize = 72.sp,
                            modifier = Modifier.scale(confettiScale.value))

                        Text(
                            if (rating >= 4) "You're a legend!\nThank you for the support ❤️"
                            else             "Thanks for the feedback!\nWe'll work on it 💪",
                            style     = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.ExtraBold
                            ),
                            textAlign = TextAlign.Center
                        )

                        // Show their star rating
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            repeat(5) { i ->
                                Icon(
                                    if (i < rating) Icons.Default.Star else Icons.Default.StarOutline,
                                    contentDescription = null,
                                    tint   = if (i < rating) Color(0xFFFFD700)
                                             else MaterialTheme.colorScheme.outline.copy(0.3f),
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }

                        Button(
                            onClick        = onBack,
                            modifier       = Modifier.fillMaxWidth().height(50.dp),
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
                                Text("Back to Game  →",
                                    style = MaterialTheme.typography.labelLarge.copy(
                                        color = Color.White, fontWeight = FontWeight.ExtraBold))
                            }
                        }
                    }
                }
            }
        }
    }
}
