package com.cableops.stbexpiry

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import com.cableops.stbexpiry.worker.DailyExpiryWorker

/**
 * Custom [Application] subclass.
 * Creates the notification channel at startup so notifications can be posted
 * even if the [DailyExpiryWorker] fires before the app is next opened.
 */
class STBExpiryApp : Application() {

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        val notificationManager =
            getSystemService(NOTIFICATION_SERVICE) as NotificationManager

        if (notificationManager.getNotificationChannel(DailyExpiryWorker.CHANNEL_ID) == null) {
            val channel = NotificationChannel(
                DailyExpiryWorker.CHANNEL_ID,
                DailyExpiryWorker.CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Daily alerts for STBs whose subscription expires today."
                enableLights(true)
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }
}
