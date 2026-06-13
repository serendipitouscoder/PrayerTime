package com.example.prayertime.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.prayertime.data.model.*
import com.example.prayertime.domain.alarm.AlarmScheduler
import com.example.prayertime.domain.calculator.PrayerTimeCalculator
import java.util.Date

/**
 * BroadcastReceiver that ensures persistence of alarms across device reboots.
 * 
 * Android clears all [AlarmManager] events when the device powers off.
 * This receiver listens for the [Intent.ACTION_BOOT_COMPLETED] signal and
 * recreates the prayer schedule and alarms based on the last saved preferences.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            // Retrieve persistence storage
            val prefs = context.getSharedPreferences("prayer_prefs", Context.MODE_PRIVATE)
            val isAlarmsEnabled = prefs.getBoolean("alarms", true)
            
            if (isAlarmsEnabled) {
                // Initialize required domain components
                val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
                val alarmScheduler = AlarmScheduler(context, alarmManager)
                val calculator = PrayerTimeCalculator()
                
                // Hydrate location data from saved state
                val location = Location(
                    latitude = prefs.getFloat("lat", 21.4225f).toDouble(),
                    longitude = prefs.getFloat("lon", 39.8262f).toDouble(),
                    timeZone = prefs.getString("tz", "Asia/Riyadh") ?: "Asia/Riyadh",
                    cityName = prefs.getString("city", "Makkah") ?: "Makkah"
                )
                
                // Parse calculation settings with safety fallbacks
                val calculationMethod = try {
                    CalculationMethod.valueOf(
                        prefs.getString("method", "MECCA") ?: "MECCA"
                    )
                } catch (_: Exception) {
                    CalculationMethod.MECCA
                }
                
                val asrMethod = try {
                    AsrMethod.valueOf(
                        prefs.getString("asr", "STANDARD") ?: "STANDARD"
                    )
                } catch (_: Exception) {
                    AsrMethod.STANDARD
                }
                
                // Re-calculate the astronomical schedule for the current date
                val schedule = calculator.calculatePrayerTimes(
                    location = location,
                    date = Date(),
                    calculationMethod = calculationMethod,
                    asrMethod = asrMethod
                )
                
                // Re-schedule alarms in the system
                alarmScheduler.scheduleAlarms(schedule, prefs.getInt("minutesBefore", 15))
            }
        }
    }
}
