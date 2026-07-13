package com.pixelplay.dotsboxes.config

import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.remoteConfigSettings

/**
 * Firebase Remote Config wrapper for ad tuning without an app update.
 * Change these keys in the Firebase console → Remote Config to A/B test frequency,
 * then publish — live apps pick up the new values on the next fetch.
 *
 * Keys:
 *  - offline_interstitial_every : show offline interstitial every Nth game (default 3)
 *  - interstitial_cooldown_sec  : min seconds between interstitials (default 180)
 *  - online_ad_mandatory        : show interstitial after every online match (default true)
 *  - free_online_games          : free online games per day before the ad gate (default 2)
 */
object RemoteConfig {

    private val rc: FirebaseRemoteConfig by lazy { FirebaseRemoteConfig.getInstance() }

    private const val KEY_OFFLINE_EVERY   = "offline_interstitial_every"
    private const val KEY_COOLDOWN_SEC    = "interstitial_cooldown_sec"
    private const val KEY_ONLINE_MANDATORY = "online_ad_mandatory"
    private const val KEY_FREE_ONLINE     = "free_online_games"

    fun init() {
        rc.setConfigSettingsAsync(
            remoteConfigSettings { minimumFetchIntervalInSeconds = 3600 }
        )
        rc.setDefaultsAsync(
            mapOf(
                KEY_OFFLINE_EVERY    to 3L,
                KEY_COOLDOWN_SEC     to 180L,
                KEY_ONLINE_MANDATORY to true,
                KEY_FREE_ONLINE      to 2L
            )
        )
        rc.fetchAndActivate()
    }

    val offlineInterstitialEvery: Int get() = rc.getLong(KEY_OFFLINE_EVERY).toInt().coerceAtLeast(1)
    val interstitialCooldownMs: Long  get() = rc.getLong(KEY_COOLDOWN_SEC) * 1000L
    val onlineAdMandatory: Boolean    get() = rc.getBoolean(KEY_ONLINE_MANDATORY)
    val freeOnlineGames: Int          get() = rc.getLong(KEY_FREE_ONLINE).toInt().coerceAtLeast(0)
}
