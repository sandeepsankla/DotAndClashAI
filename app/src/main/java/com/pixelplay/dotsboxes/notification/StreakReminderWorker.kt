package com.pixelplay.dotsboxes.notification

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.pixelplay.dotsboxes.data.local.GameDataStore
import kotlinx.coroutines.flow.first

class StreakReminderWorker(
    private val ctx: Context,
    params: WorkerParameters
) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        val stats = GameDataStore(ctx).observeStats().first()

        // Notify only if daily task is not complete
        if (!stats.dailyTaskComplete) {
            val winsLeft = (2 - stats.todayWins).coerceAtLeast(0)
            val streakMsg = if (stats.dailyStreak > 1)
                " Don't break your ${stats.dailyStreak}-day streak! 🔥"
            else ""

            NotificationHelper.showNotification(
                context = ctx,
                id      = NotificationHelper.NOTIF_STREAK,
                emoji   = "🎯",
                title   = "Daily Task Incomplete!",
                body    = "$winsLeft more win${if (winsLeft > 1) "s" else ""} needed today.$streakMsg"
            )
        }

        // Spin reminder
        if (stats.pendingSpins > 0) {
            NotificationHelper.showNotification(
                context = ctx,
                id      = NotificationHelper.NOTIF_SPIN,
                emoji   = "🎡",
                title   = "You have ${stats.pendingSpins} spin${if (stats.pendingSpins > 1) "s" else ""} waiting!",
                body    = "Open Dot Clash AI and spin to win up to 500 coins!"
            )
        }

        return Result.success()
    }
}
