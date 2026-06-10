package com.example.prayertime.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * BroadcastReceiver that reschedules alarms on device boot
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            // Reschedule alarms when device boots
            val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
            val isAlarmsEnabled = prefs.getBoolean("isAlarmsEnabled", true)
            
            if (isAlarmsEnabled) {
                val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
                val alarmScheduler = com.example.prayertime.domain.alarm.AlarmScheduler(context, alarmManager)
                val calculator = com.example.prayertime.domain.calculator.PrayerTimeCalculator()
                
                val location = com.example.prayertime.data.model.Location(
                    latitude = prefs.getFloat("latitude", 21.4225f).toDouble(),
                    longitude = prefs.getFloat("longitude", 39.8262f).toDouble(),
                    timeZone = prefs.getString("timeZone", "Asia/Riyadh") ?: "Asia/Riyadh",
                    cityName = prefs.getString("cityName", "Makkah") ?: "Makkah"
                )
                
                val calculationMethod = try {
                    com.example.prayertime.data.model.CalculationMethod.valueOf(
                        prefs.getString("calculationMethod", "MECCA") ?: "MECCA"
                    )
                } catch (_: Exception) {
                    com.example.prayertime.data.model.CalculationMethod.MECCA
                }
                
                val asrMethod = try {
                    com.example.prayertime.data.model.AsrMethod.valueOf(
                        prefs.getString("asrMethod", "STANDARD") ?: "STANDARD"
                    )
                } catch (_: Exception) {
                    com.example.prayertime.data.model.AsrMethod.STANDARD
                }
                
                val schedule = calculator.calculatePrayerTimes(
                    location = location,
                    date = java.util.Date(),
                    calculationMethod = calculationMethod,
                    asrMethod = asrMethod
                )
                
                alarmScheduler.scheduleAlarms(schedule, prefs.getInt("notificationMinutesBefore", 15))
            }
        }
    }
}
