package com.example.prayertime.domain.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.example.prayertime.data.model.Prayer
import com.example.prayertime.data.model.PrayerName
import com.example.prayertime.data.model.PrayerSchedule
import com.example.prayertime.receiver.PrayerAlarmReceiver

/**
 * Manages scheduling and cancellation of prayer alarms
 */
class AlarmScheduler(
    private val context: Context,
    private val alarmManager: AlarmManager
) {

    /**
     * Schedules alarms for all prayers in the schedule
     */
    fun scheduleAlarms(schedule: PrayerSchedule, notificationMinutesBefore: Int) {
        cancelAllAlarms()

        schedule.prayers.forEach { prayer ->
            if (shouldScheduleAlarmForPrayer(prayer)) {
                val alarmTime = calculateAlarmTime(prayer.startTime, notificationMinutesBefore)
                if (alarmTime != null) {
                    scheduleAlarm(prayer, alarmTime)
                }
            }
        }
    }

    /**
     * Cancels all scheduled alarms
     */
    fun cancelAllAlarms() {
        PrayerName.entries.forEach { prayerName ->
            cancelAlarm(prayerName)
        }
    }

    /**
     * Cancels a specific prayer alarm
     */
    fun cancelAlarm(prayerName: PrayerName) {
        val intent = Intent(context, PrayerAlarmReceiver::class.java).apply {
            action = "PRAYER_ALARM_${prayerName.name}"
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            prayerName.ordinal,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        pendingIntent.cancel()
    }

    /**
     * Schedules a single alarm for a prayer
     */
    private fun scheduleAlarm(prayer: Prayer, alarmTimeMillis: Long) {
        val intent = Intent(context, PrayerAlarmReceiver::class.java).apply {
            action = "PRAYER_ALARM_${prayer.name.name}"
            putExtra("prayer_name", prayer.name.displayName)
            putExtra("prayer_time", prayer.startTime)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            prayer.name.ordinal,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            alarmTimeMillis,
            pendingIntent
        )
    }

    /**
     * Checks if an alarm should be scheduled for this prayer
     */
    private fun shouldScheduleAlarmForPrayer(prayer: Prayer): Boolean {
        // Don't alarm for sunrise (just informational)
        return prayer.name != PrayerName.SUNRISE
    }

    /**
     * Calculates the alarm time based on prayer start time and notification offset
     */
    private fun calculateAlarmTime(prayerStartTime: String, minutesBefore: Int): Long? {
        val parts = prayerStartTime.split(":")
        val hour = parts[0].toIntOrNull() ?: return null
        val minute = parts[1].toIntOrNull() ?: return null

        val calendar = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, hour)
            set(java.util.Calendar.MINUTE, minute - minutesBefore)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }

        val alarmTime = calendar.timeInMillis
        val now = java.util.Calendar.getInstance().timeInMillis

        // If the alarm time is in the past, schedule for tomorrow
        return if (alarmTime <= now) {
            calendar.add(java.util.Calendar.DAY_OF_YEAR, 1)
            calendar.timeInMillis
        } else {
            alarmTime
        }
    }
}
