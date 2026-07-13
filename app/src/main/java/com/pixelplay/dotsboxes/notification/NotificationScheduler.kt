package com.pixelplay.dotsboxes.notification

import android.content.Context
import androidx.work.*
import java.util.Calendar
import java.util.concurrent.TimeUnit

object NotificationScheduler {

    private const val TAG_FLASH  = "daily_flash_notif"
    private const val TAG_STREAK = "streak_notif"
    private const val TAG_HINT   = "hint_expiry_notif"

    fun schedule(context: Context) {
        NotificationHelper.createChannel(context)
        val mgr = WorkManager.getInstance(context)

        // ── Daily Flash — 10:00 AM every day ─────────────────────────────────
        mgr.enqueueUniquePeriodicWork(
            TAG_FLASH,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<DailyFlashWorker>(24, TimeUnit.HOURS)
                .setInitialDelay(delayUntil(hour = 10, minute = 0), TimeUnit.MILLISECONDS)
                .setConstraints(Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                    .build())
                .build()
        )

        // ── Streak / Spin reminder — 7:00 PM every day ───────────────────────
        mgr.enqueueUniquePeriodicWork(
            TAG_STREAK,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<StreakReminderWorker>(24, TimeUnit.HOURS)
                .setInitialDelay(delayUntil(hour = 19, minute = 0), TimeUnit.MILLISECONDS)
                .setConstraints(Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                    .build())
                .build()
        )

        // ── Hint expiry reminder — 6:00 PM every day ─────────────────────────
        mgr.enqueueUniquePeriodicWork(
            TAG_HINT,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<HintExpiryWorker>(24, TimeUnit.HOURS)
                .setInitialDelay(delayUntil(hour = 18, minute = 0), TimeUnit.MILLISECONDS)
                .setConstraints(Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                    .build())
                .build()
        )
    }

    /** Debug-only: fire a test notification ~12s after launch to verify the pipeline. */
    fun scheduleDebugTest(context: Context) {
        NotificationHelper.createChannel(context)
        WorkManager.getInstance(context).enqueue(
            OneTimeWorkRequestBuilder<TestNotificationWorker>()
                .setInitialDelay(12, TimeUnit.SECONDS)
                .build()
        )
    }

    /** Milliseconds until the next occurrence of hour:minute today (or tomorrow if passed). */
    private fun delayUntil(hour: Int, minute: Int): Long {
        val now    = Calendar.getInstance()
        val target = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE,      minute)
            set(Calendar.SECOND,      0)
            set(Calendar.MILLISECOND, 0)
        }
        if (target.before(now)) target.add(Calendar.DAY_OF_YEAR, 1)
        return target.timeInMillis - now.timeInMillis
    }
}
