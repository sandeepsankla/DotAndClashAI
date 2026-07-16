package com.pixelplay.dotsboxes

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.pixelplay.dotsboxes.BuildConfig
import com.pixelplay.dotsboxes.analytics.Analytics
import com.pixelplay.dotsboxes.data.local.GameDataStore
import com.pixelplay.dotsboxes.data.remote.FirebaseManager
import com.pixelplay.dotsboxes.data.repository.GameRepositoryImpl
import com.pixelplay.dotsboxes.domain.repository.GameRepository
import com.pixelplay.dotsboxes.presentation.ads.InterstitialAdManager
import com.pixelplay.dotsboxes.presentation.ads.RewardedAdManager
import com.pixelplay.dotsboxes.notification.NotificationScheduler
import com.pixelplay.dotsboxes.presentation.ads.initAdMob
import com.pixelplay.dotsboxes.sound.SoundManager

class DotsBoxesApp : Application() {

    val gameDataStore   by lazy { GameDataStore(this) }
    val gameRepository: GameRepository by lazy { GameRepositoryImpl(gameDataStore) }
    val soundManager    by lazy { SoundManager(this) }
    val interstitialAd  by lazy { InterstitialAdManager(this) }
    val rewardedAd      by lazy { RewardedAdManager(this) }
    val firebaseManager by lazy { FirebaseManager() }

    /** Room code from an incoming invite deep link (dotclash://join/CODE or https link). */
    var pendingInviteCode by mutableStateOf<String?>(null)

    @Volatile private var adsStarted = false

    override fun onCreate() {
        super.onCreate()
        FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(true)
        Analytics.init(this)
        com.pixelplay.dotsboxes.config.RemoteConfig.init()
        // NOTE: ads are initialized in startAds(), called from MainActivity only AFTER
        // UMP consent is gathered (GDPR) — so we never request ads before consent.
        NotificationScheduler.schedule(this)
        if (BuildConfig.DEBUG) {
            NotificationScheduler.scheduleDebugTest(this)
        }
    }

    /**
     * Initialize the Mobile Ads SDK and preload ads. Called once, after UMP consent has
     * been resolved. Preloading only inside the init callback avoids racing the async
     * init (which would silently leave ads null).
     */
    fun startAds() {
        if (adsStarted) return
        adsStarted = true
        initAdMob(this) {
            interstitialAd.preload()
            rewardedAd.preload()
        }
    }
}
