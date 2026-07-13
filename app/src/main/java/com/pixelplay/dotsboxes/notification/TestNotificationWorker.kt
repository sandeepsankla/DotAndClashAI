package com.pixelplay.dotsboxes.notification

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

/** Debug-only: fires a notification a few seconds after launch to verify the pipeline works. */
class TestNotificationWorker(
    private val ctx: Context,
    params: WorkerParameters
) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        NotificationHelper.showNotification(
            context = ctx,
            id      = NotificationHelper.NOTIF_DAILY_FLASH,
            emoji   = "⚡",
            title   = "Flash Challenge is Ready!",
            body    = "Can you beat the AI today? 15 seconds — let's go! (test)"
        )
        return Result.success()
    }
}
