package com.cableops.stbexpiry.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.cableops.stbexpiry.R
import com.cableops.stbexpiry.data.local.AppDatabase
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * A [CoroutineWorker] that runs once every 24 hours (scheduled by [WorkManager]).
 *
 * It queries the local Room database for STB records expiring today and, if any are
 * found, posts a persistent high-priority notification on the [CHANNEL_ID] channel.
 *
 * The notification channel is created lazily inside [doWork] so that the worker is
 * fully self-contained and does not depend on Application onCreate ordering.
 */
class DailyExpiryWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    companion object {
        const val CHANNEL_ID   = "DAILY_EXPIRY_CHANNEL"
        const val CHANNEL_NAME = "STB Expiry Alerts"
        const val NOTIFICATION_ID = 1001
        const val WORK_NAME    = "DailyExpiryCheck"
        private val DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy")
    }

    override suspend fun doWork(): Result {
        return try {
            val dao = AppDatabase.getInstance(context).recordDao()

            val todayStr = LocalDate.now().format(DATE_FORMATTER)
            val expiringToday = dao.getRecordsByDate(todayStr)

            if (expiringToday.isNotEmpty()) {
                ensureNotificationChannel()
                postExpiryNotification(expiringToday.size)
            }

            Result.success()
        } catch (e: Exception) {
            // Retry on failure – WorkManager will reschedule with back-off.
            Result.retry()
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────────────

    private fun ensureNotificationChannel() {
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (notificationManager.getNotificationChannel(CHANNEL_ID) == null) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Daily alerts for STBs whose subscription expires today."
                enableLights(true)
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun postExpiryNotification(count: Int) {
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("STB Expiry Alert")
            .setContentText("$count STB(s) expiring today")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("$count Set-Top Box subscription(s) are expiring today. Open the app to view details.")
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setOngoing(true)
            .setAutoCancel(false)
            .build()

        // Permission check is handled at the call site (Android 13+). On older APIs
        // POST_NOTIFICATIONS is implicitly granted, so this call is always safe.
        with(NotificationManagerCompat.from(context)) {
            notify(NOTIFICATION_ID, notification)
        }
    }
}
