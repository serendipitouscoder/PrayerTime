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
 * Service class responsible for scheduling system-level alarms for prayer times.
 * 
 * This class interacts with the Android [AlarmManager] to set high-precision,
 * exact alarms that can fire even when the device is idle (Doze mode).
 * 
 * @property context Application context used for creating intents and pending intents.
 * @property alarmManager System service for scheduling alarms.
 */
class AlarmScheduler(
    private val context: Context,
    private val alarmManager: AlarmManager
) {

    /**
     * Schedules alarms for all valid prayers in a given schedule.
     * 
     * This method first clears any existing alarms to prevent duplicates or 
     * stale notifications, then iterates through today's prayers.
     * 
     * @param schedule The full day's [PrayerSchedule].
     * @param notificationMinutesBefore User preference for how many minutes 
     * before the actual prayer time the alarm should trigger.
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
     * Robust cancellation of all possible prayer alarms.
     */
    fun cancelAllAlarms() {
        PrayerName.entries.forEach { prayerName ->
            cancelAlarm(prayerName)
        }
    }

    /**
     * Cancels a specific prayer alarm by its [PrayerName].
     * 
     * Uses a unique action string and the enum's ordinal as a request code 
     * to identify the specific [PendingIntent].
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
     * Performs the actual low-level scheduling with the Android system.
     * 
     * Uses [AlarmManager.setExactAndAllowWhileIdle] to ensure the alarm triggers 
     * at the precise time even during power-saving modes.
     * 
     * @param prayer The prayer object containing metadata.
     * @param alarmTimeMillis The target time in UTC milliseconds.
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
     * Logic to determine if a specific time entry requires a notification.
     * Typically, Fajr through Isha require alarms, while Sunrise/Sunset are informational.
     */
    private fun shouldScheduleAlarmForPrayer(prayer: Prayer): Boolean {
        return prayer.name != PrayerName.SUNRISE && prayer.name != PrayerName.SUNSET
    }

    /**
     * Calculates the target trigger time by applying the user's lead-time offset.
     * 
     * If the calculated time for today has already passed, it automatically 
     * rolls the alarm forward by 24 hours.
     * 
     * @return UTC milliseconds for the alarm, or null if input is malformed.
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

        return if (alarmTime <= now) {
            calendar.add(java.util.Calendar.DAY_OF_YEAR, 1)
            calendar.timeInMillis
        } else {
            alarmTime
        }
    }
}
