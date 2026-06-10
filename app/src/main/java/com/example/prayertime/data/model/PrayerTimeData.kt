package com.example.prayertime.data.model

import java.util.Locale

/**
 * Represents a single prayer with its name, start time, and end time
 */
data class Prayer(
    val name: PrayerName,
    val startTime: String,
    val endTime: String,
    val isUpcoming: Boolean = false
) {
    override fun toString(): String {
        return "$name: $startTime - $endTime"
    }
}

/**
 * Enum class for the five daily prayers plus additional times
 */
enum class PrayerName(val displayName: String, val arabicName: String) {
    FAJR("Fajr", "الفجر"),
    SUNRISE("Sunrise", "الشروق"),
    DHUHR("Dhuhr", "الظهر"),
    ASR("Asr", "العصر"),
    MAGHRIB("Maghrib", "المغرب"),
    ISHA("Isha", "العشاء");

    companion object {
        fun fromString(name: String): PrayerName? {
            return entries.find { it.name.equals(name, ignoreCase = true) }
        }
    }
}

/**
 * Location data for prayer time calculation
 */
data class Location(
    val latitude: Double,
    val longitude: Double,
    val timeZone: String = "Asia/Riyadh",
    val cityName: String = "Makkah"
) {
    fun toCoordinatesString(): String {
        return String.format(Locale.US, "%.4f, %.4f", latitude, longitude)
    }
}

/**
 * Prayer calculation method
 */
enum class CalculationMethod(val displayName: String, val fajrAngle: Double, val ishaAngle: Double) {
    UNIVERSITY_OF_ISLAMIC_SCIENCES("University of Islamic Sciences, Karachi", 18.0, 17.0),
    MUZAKKIR("Muzakkir Egypt", 19.5, 17.5),
    EGYPT("Egyptian General Authority", 19.5, 17.5),
    MECCA("Umm Al-Qura, Makkah", 18.5, 90.0), // 90 minutes after Maghrib for Isha
    MUSLIM_WORLD("Muslim World League", 18.0, 17.0),
    CUSTOM("Custom", 18.0, 17.0)
}

/**
 * Asr calculation method (Shafi'i vs Hanafi)
 */
enum class AsrMethod(val label: String, val factor: Double) {
    STANDARD("Shafi'i/Maliki/Hanbali", 1.0),
    HANAFI("Hanafi", 2.0)
}

/**
 * Complete prayer schedule for a day
 */
data class PrayerSchedule(
    val date: String,
    val location: Location,
    val prayers: List<Prayer>,
    val calculationMethod: CalculationMethod,
    val asrMethod: AsrMethod
)

/**
 * Theme modes for the application
 */
enum class AppThemeMode {
    LIGHT, DARK, SYSTEM
}

/**
 * Settings for the app
 */
data class AppSettings(
    val latitude: Double = 21.4225,
    val longitude: Double = 39.8262,
    val cityName: String = "Makkah",
    val timeZone: String = "Asia/Riyadh",
    val calculationMethod: CalculationMethod = CalculationMethod.MECCA,
    val asrMethod: AsrMethod = AsrMethod.STANDARD,
    val isAlarmsEnabled: Boolean = true,
    val notificationMinutesBefore: Int = 15,
    val themeMode: AppThemeMode = AppThemeMode.SYSTEM
)
