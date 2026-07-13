package com.pixelplay.dotsboxes.notification

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.pixelplay.dotsboxes.data.local.GameDataStore
import kotlinx.coroutines.flow.first

class DailyFlashWorker(
    private val ctx: Context,
    params: WorkerParameters
) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        val stats = GameDataStore(ctx).observeStats().first()

        if (!stats.hasPlayedFlashToday) {
            NotificationHelper.showNotification(
                context = ctx,
                id      = NotificationHelper.NOTIF_DAILY_FLASH,
                emoji   = "⚡",
                title   = "Flash Challenge is Ready!",
                body    = "Can you beat the AI today? New board is waiting — 15 seconds, go!"
            )
        }
        return Result.success()
    }
}
