package com.pixelplay.dotsboxes

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.pixelplay.dotsboxes.presentation.navigation.AppNavigation
import com.pixelplay.dotsboxes.presentation.theme.DotsBoxesTheme

class MainActivity : ComponentActivity() {

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* no-op */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        handleInviteLink(intent)

        // Ask notification permission on Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this, Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        setContent {
            DotsBoxesTheme {
                AppNavigation()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleInviteLink(intent)
    }

    /** Parse a room code from an invite deep link and stash it for AppNavigation. */
    private fun handleInviteLink(intent: Intent?) {
        val data: Uri = intent?.data ?: return
        val raw = when (data.scheme) {
            "dotclash" -> data.lastPathSegment            // dotclash://join/CODE
            else       -> data.getQueryParameter("code")  // https://dotclash.app/join?code=CODE
        }
        val code = raw?.uppercase()?.takeIf { it.matches(Regex("[A-HJ-NP-Z2-9]{6}")) }
        if (code != null) {
            (application as DotsBoxesApp).pendingInviteCode = code
        }
    }
}
