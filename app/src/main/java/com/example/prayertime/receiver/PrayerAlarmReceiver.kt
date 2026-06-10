package com.example.prayertime.receiver

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

/**
 * BroadcastReceiver that handles prayer alarm notifications
 */
class PrayerAlarmReceiver : BroadcastReceiver() {

    companion object {
        private const val CHANNEL_ID = "prayer_alarm_channel"
        private const val CHANNEL_NAME = "Prayer Alarms"
        private const val CHANNEL_DESCRIPTION = "Notifications for upcoming prayer times"
        private const val EXTRA_PRAYER_NAME = "prayer_name"
        private const val EXTRA_PRAYER_TIME = "prayer_time"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val prayerName = intent.getStringExtra(EXTRA_PRAYER_NAME) ?: "Prayer"
        val prayerTime = intent.getStringExtra(EXTRA_PRAYER_TIME) ?: ""

        createNotificationChannel(context)
        showNotification(context, prayerName, prayerTime)
    }

    /**
     * Shows a notification for the prayer alarm
     */
    private fun showNotification(context: Context, prayerName: String, prayerTime: String) {
        // Build intent to open app when notification is tapped
        val intent = Intent(context, com.example.prayertime.MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Build notification
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Prayer Time")
            .setContentText("$prayerName is at $prayerTime")
            .setStyle(NotificationCompat.BigTextStyle().bigText("Prayer: $prayerName\nTime: $prayerTime"))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .build()

        // Post notification
        val notificationManager = ContextCompat.getSystemService(
            context, NotificationManager::class.java
        )
        val notificationId = prayerName.hashCode().coerceAtMost(Int.MAX_VALUE).coerceAtLeast(0)
        notificationManager?.notify(notificationId, notification)
    }

    /**
     * Creates the notification channel for Android O and above
     */
    private fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = CHANNEL_DESCRIPTION
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 250, 250, 250)
            }

            val notificationManager = ContextCompat.getSystemService(
                context, NotificationManager::class.java
            )
            notificationManager?.createNotificationChannel(channel)
        }
    }
}
