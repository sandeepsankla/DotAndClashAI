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
import androidx.compose.runtime.LaunchedEffect
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import com.pixelplay.dotsboxes.presentation.navigation.AppNavigation
import com.pixelplay.dotsboxes.presentation.theme.DotsBoxesTheme

class MainActivity : ComponentActivity() {

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* no-op */ }

    // Keeps the system splash (branded logo) on screen through app startup until Compose
    // has drawn the splash art — so there's no blank gap during a slow cold start.
    @Volatile private var contentReady = false

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        splash.setKeepOnScreenCondition { !contentReady }
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        gatherConsentThenStartAds()
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
                // First composition -> release the branded system splash (D-C logo) straight to Home.
                LaunchedEffect(Unit) { contentReady = true }
                AppNavigation()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleInviteLink(intent)
    }

    /**
     * GDPR/EEA consent via the User Messaging Platform. Requests the latest consent info,
     * shows the consent form when required (EEA/UK), and only then starts the Ads SDK —
     * so ads are never requested before consent. Non-EEA users see no form and ads start
     * normally. startAds() is idempotent, so calling it in multiple paths is safe.
     */
    private fun gatherConsentThenStartAds() {
        val app = application as DotsBoxesApp
        val consentInformation = UserMessagingPlatform.getConsentInformation(this)
        val params = ConsentRequestParameters.Builder().build()
        consentInformation.requestConsentInfoUpdate(this, params, {
            UserMessagingPlatform.loadAndShowConsentFormIfRequired(this) {
                if (consentInformation.canRequestAds()) app.startAds()
            }
        }, {
            // Consent update failed (e.g. offline) — non-EEA users can still get ads.
            if (consentInformation.canRequestAds()) app.startAds()
        })
        // Returning users who already have a valid consent choice can start ads immediately.
        if (consentInformation.canRequestAds()) app.startAds()
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
