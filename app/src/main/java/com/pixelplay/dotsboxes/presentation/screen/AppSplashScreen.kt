package com.pixelplay.dotsboxes.presentation.screen

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.pixelplay.dotsboxes.R
import kotlinx.coroutines.delay

/**
 * Full-screen branded splash: shows the designed splash image for a short beat, then
 * calls [onDone]. Kept brief (Play Store guideline) — just displays the static art.
 */
@Composable
fun AppSplashScreen(onDone: () -> Unit) {
    LaunchedEffect(Unit) {
        delay(1800)
        onDone()
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0A18))   // matches the image's dark background
    ) {
        Image(
            painter            = painterResource(R.drawable.splash_bg),
            contentDescription = null,
            modifier           = Modifier.fillMaxSize(),
            contentScale       = ContentScale.Crop
        )
    }
}
