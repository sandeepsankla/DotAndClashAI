package com.pixelplay.dotsboxes.notification

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.pixelplay.dotsboxes.data.local.GameDataStore
import kotlinx.coroutines.flow.first

/** Reminds the player to use their hints before they expire (2-day window). */
class HintExpiryWorker(
    private val ctx: Context,
    params: WorkerParameters
) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        val stats = GameDataStore(ctx).observeStats().first()
        val daysLeft = stats.hintsExpiryDaysLeft

        // Notify only when hints exist and expire today or tomorrow
        if (stats.hintCoins > 0 && daysLeft in 0..1) {
            val whenStr = if (daysLeft <= 0) "today" else "tomorrow"
            NotificationHelper.showNotification(
                context = ctx,
                id      = NotificationHelper.NOTIF_HINT_EXPIRY,
                emoji   = "💡",
                title   = "Your hints expire $whenStr!",
                body    = "You have ${stats.hintCoins} hint${if (stats.hintCoins > 1) "s" else ""} left — " +
                          "play a game and use them before they're gone."
            )
        }
        return Result.success()
    }
}
