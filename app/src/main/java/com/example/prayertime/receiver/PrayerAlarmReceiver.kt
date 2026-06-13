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
 * System-level BroadcastReceiver triggered by scheduled [AlarmManager] events.
 * 
 * This class handles the "Wake Up" event when a prayer time is approaching.
 * It is responsible for:
 * 1. Extracting prayer metadata from the incoming intent.
 * 2. Creating a notification channel (for Android 8.0+).
 * 3. Posting a high-priority heads-up notification to the user.
 */
class PrayerAlarmReceiver : BroadcastReceiver() {

    companion object {
        private const val CHANNEL_ID = "prayer_alarm_channel"
        private const val CHANNEL_NAME = "Prayer Alarms"
        private const val CHANNEL_DESCRIPTION = "Notifications for upcoming prayer times"
        private const val EXTRA_PRAYER_NAME = "prayer_name"
        private const val EXTRA_PRAYER_TIME = "prayer_time"
    }

    /**
     * Entry point when the system alarm fires.
     * 
     * Logic:
     * - Retrieve name and time strings from extras.
     * - Ensure channel exists.
     * - Build and notify.
     */
    override fun onReceive(context: Context, intent: Intent) {
        val prayerName = intent.getStringExtra(EXTRA_PRAYER_NAME) ?: "Prayer"
        val prayerTime = intent.getStringExtra(EXTRA_PRAYER_TIME) ?: ""

        createNotificationChannel(context)
        showNotification(context, prayerName, prayerTime)
    }

    /**
     * Constructs and displays a system notification.
     * 
     * @param prayerName Human-readable name (e.g., "Fajr").
     * @param prayerTime The start time for display.
     */
    private fun showNotification(context: Context, prayerName: String, prayerTime: String) {
        // Build intent to bring the user back to the app main screen when tapped
        val intent = Intent(context, com.example.prayertime.MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Build notification with high priority and vibration for immediate awareness
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

        val notificationManager = ContextCompat.getSystemService(
            context, NotificationManager::class.java
        )
        // Use hashcode as ID to support multiple concurrent prayer notifications if necessary
        val notificationId = prayerName.hashCode().coerceAtMost(Int.MAX_VALUE).coerceAtLeast(0)
        notificationManager?.notify(notificationId, notification)
    }

    /**
     * Required setup for Android O (API 26) and above.
     * Configures the visual and audible behavior of notifications in this category.
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
