package com.pixelplay.dotsboxes.analytics

import android.content.Context
import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics

/**
 * Thin wrapper over Firebase Analytics so event logging is one-liner and centralized.
 * No login required — Firebase assigns each install a pseudonymous App Instance ID.
 * Call [init] once from the Application; optionally [setUser] with the anonymous auth UID.
 */
object Analytics {

    private var fa: FirebaseAnalytics? = null

    fun init(context: Context) {
        fa = FirebaseAnalytics.getInstance(context)
    }

    /** Tie events to a stable id (e.g. anonymous/Google auth UID) when available. */
    fun setUser(uid: String?) {
        fa?.setUserId(uid)
    }

    fun setUserProperty(name: String, value: String?) {
        fa?.setUserProperty(name, value)
    }

    private fun log(event: String, params: Bundle.() -> Unit = {}) {
        fa?.logEvent(event, Bundle().apply(params))
    }

    // ── Gameplay ───────────────────────────────────────────────────────────────
    fun gameStart(mode: String, difficulty: String, grid: Int, level: Int? = null) =
        log("game_start") {
            putString("mode", mode); putString("difficulty", difficulty)
            putInt("grid", grid); level?.let { putInt("level", it) }
        }

    fun gameEnd(mode: String, result: String, myScore: Int, opScore: Int) =
        log("game_end") {
            putString("mode", mode); putString("result", result)
            putInt("my_score", myScore); putInt("op_score", opScore)
        }

    fun levelComplete(level: Int) = log("level_complete") { putInt("level", level) }

    // ── Online ─────────────────────────────────────────────────────────────────
    fun onlineRoomCreated() = log("online_room_created")
    fun onlineJoined()      = log("online_joined")
    fun onlineGatedByAd()   = log("online_ad_gate_shown")

    // ── Invite / virality ──────────────────────────────────────────────────────
    fun inviteShared()      = log("invite_shared")
    fun inviteOpened()      = log("invite_opened")

    // ── Rewards / retention ────────────────────────────────────────────────────
    fun crownCaptured(mode: String)   = log("crown_captured") { putString("mode", mode) }
    fun mysteryOpened(mode: String, reward: String) =
        log("mystery_opened") { putString("mode", mode); putString("reward", reward) }
    fun spinUsed(prize: String)       = log("spin_used") { putString("prize", prize) }
    fun dailyMissionComplete()        = log("daily_mission_complete")
    fun hintUsed()                    = log("hint_used")
    fun hintsExpired(count: Int)      = log("hints_expired") { putInt("count", count) }

    // ── Online match lifecycle ─────────────────────────────────────────────────
    fun onlineMatchStart() = log("online_match_start")
    fun onlineMatchEnd(result: String) = log("online_match_end") { putString("result", result) }

    // ── Ad funnel ──────────────────────────────────────────────────────────────
    fun interstitialShown(placement: String) = log("interstitial_shown") { putString("placement", placement) }
    fun rewardedShown(placement: String)     = log("rewarded_shown") { putString("placement", placement) }
    fun bannerLoaded()                        = log("banner_loaded")
    fun dailyRewardClaimed(xp: Int)           = log("daily_reward_claimed") { putInt("xp", xp) }

    // ── Monetization ───────────────────────────────────────────────────────────
    /** Fired when a rewarded ad completes (reward granted). */
    fun adWatched(type: String, placement: String) =
        log("ad_watched") { putString("ad_type", type); putString("placement", placement) }
    fun storeOpen()                   = log("store_open")
    fun avatarPurchased(id: Int, cost: Int) =
        log("avatar_purchased") { putInt("avatar_id", id); putInt("cost", cost) }
    fun skinPurchased(skin: String, cost: Int) =
        log("skin_purchased") { putString("skin", skin); putInt("cost", cost) }
}
