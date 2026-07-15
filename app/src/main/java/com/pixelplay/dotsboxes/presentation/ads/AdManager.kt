package com.pixelplay.dotsboxes.presentation.ads

import android.content.Context
import android.util.Log
import androidx.compose.runtime.*
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.*
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.pixelplay.dotsboxes.BuildConfig

// ── Ad Unit IDs ───────────────────────────────────────────────────────────────
// Debug builds → Google TEST ad units (safe: no invalid clicks on real ads).
// Release builds → real ad units (revenue). App ID (in AndroidManifest) is always real.

private val BANNER_ID = if (BuildConfig.DEBUG)
    "ca-app-pub-3940256099942544/6300978111"
else
    "ca-app-pub-8558797603496151/1741809695"

private val INTERSTITIAL_ID = if (BuildConfig.DEBUG)
    "ca-app-pub-3940256099942544/1033173712"
else
    "ca-app-pub-8558797603496151/4447864086"

private val REWARDED_ID = if (BuildConfig.DEBUG)
    "ca-app-pub-3940256099942544/5224354917"
else
    "ca-app-pub-8558797603496151/9076806011"

// ── Initializer (call once in DotsBoxesApp) ───────────────────────────────────

/**
 * Initialize the Mobile Ads SDK. [onInitialized] runs on the main thread once the SDK
 * is ready — preload ads there, NOT before, otherwise the first load races the init and
 * silently fails (leaving rewarded/interstitial null so buttons appear to do nothing).
 */
fun initAdMob(context: Context, onInitialized: () -> Unit = {}) {
    MobileAds.initialize(context) {
        Log.d("AdMob", "Initialized")
        onInitialized()
    }
}

// ── Interstitial Ad ───────────────────────────────────────────────────────────

class InterstitialAdManager(private val context: Context) {

    private var ad: InterstitialAd? = null
    private var gameCount = 0
    private var lastShownAt = 0L

    // Frequency/cooldown come from Remote Config (tunable without an app update)
    private val rc get() = com.pixelplay.dotsboxes.config.RemoteConfig
    private fun cooldownOver() = System.currentTimeMillis() - lastShownAt > rc.interstitialCooldownMs

    private fun showGated(activity: android.app.Activity, placement: String, onDismissed: () -> Unit) {
        val current = ad
        if (current == null) { preload(); onDismissed(); return }
        lastShownAt = System.currentTimeMillis()
        com.pixelplay.dotsboxes.analytics.Analytics.interstitialShown(placement)
        current.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() { ad = null; preload(); onDismissed() }
            override fun onAdFailedToShowFullScreenContent(e: AdError) { ad = null; preload(); onDismissed() }
        }
        current.show(activity)
    }

    fun preload() {
        val req = AdRequest.Builder().build()
        InterstitialAd.load(context, INTERSTITIAL_ID, req,
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(a: InterstitialAd) { ad = a }
                override fun onAdFailedToLoad(e: LoadAdError) {
                    Log.w("AdMob", "Interstitial failed: ${e.message}")
                    ad = null
                }
            }
        )
    }

    /** Online match ended → show after EVERY match (mandatory). */
    fun showOnlineEnd(activity: android.app.Activity, onAdDismissed: () -> Unit = {}) {
        showGated(activity, "online_end", onAdDismissed)
    }

    /**
     * Offline game-over → Main Menu. Shows every 3rd game, but only counts games that
     * lasted long enough ([eligible]) so quick restarts don't trigger ads.
     */
    fun onGameOver(activity: android.app.Activity, eligible: Boolean, onAdDismissed: () -> Unit) {
        if (!eligible) { onAdDismissed(); return }
        gameCount++
        if (gameCount % rc.offlineInterstitialEvery == 0 && cooldownOver()) showGated(activity, "game_over", onAdDismissed)
        else onAdDismissed()
    }
}

// ── Rewarded Ad ───────────────────────────────────────────────────────────────

class RewardedAdManager(private val context: Context) {

    private var ad: RewardedAd? = null

    fun preload() {
        val req = AdRequest.Builder().build()
        RewardedAd.load(context, REWARDED_ID, req,
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(a: RewardedAd) { ad = a }
                override fun onAdFailedToLoad(e: LoadAdError) {
                    Log.w("AdMob", "Rewarded failed: ${e.message}")
                    ad = null
                }
            }
        )
    }

    val isReady: Boolean get() = ad != null

    /** Show rewarded ad. onRewarded called with coin amount if user completes ad. */
    fun show(activity: android.app.Activity, coins: Int = 2, onRewarded: (Int) -> Unit, onFailed: () -> Unit) {
        if (ad == null) { onFailed(); return }
        com.pixelplay.dotsboxes.analytics.Analytics.rewardedShown("rewarded")
        ad!!.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() { ad = null; preload() }
            override fun onAdFailedToShowFullScreenContent(e: AdError) { onFailed() }
        }
        ad!!.show(activity) { onRewarded(coins) }
    }
}

// ── Banner Ad Composable ──────────────────────────────────────────────────────

@Composable
fun BannerAdView() {
    AndroidView(
        factory = { ctx ->
            AdView(ctx).apply {
                setAdSize(AdSize.BANNER)
                adUnitId = BANNER_ID
                adListener = object : AdListener() {
                    override fun onAdLoaded() {
                        com.pixelplay.dotsboxes.analytics.Analytics.bannerLoaded()
                    }
                }
                loadAd(AdRequest.Builder().build())
            }
        },
        update = { /* no update needed — ad handles itself */ }
    )
}
