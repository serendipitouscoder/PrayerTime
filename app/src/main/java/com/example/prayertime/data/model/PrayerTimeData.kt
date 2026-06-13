package com.example.prayertime.data.model

import java.util.Locale

/**
 * Data class representing a single prayer event.
 * 
 * @property name The [PrayerName] identifier for this prayer.
 * @property startTime The formatted start time string (e.g., "05:30").
 * @property endTime The formatted end time string, typically the start of the next prayer.
 * @property isUpcoming Boolean flag used by the UI to highlight the next scheduled prayer.
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
 * Enumeration of all trackable daily times.
 * Includes the five mandatory prayers plus astronomical sunrise and sunset.
 * 
 * @property displayName User-friendly name for display in the UI.
 * @property arabicName Traditional Arabic name.
 */
enum class PrayerName(val displayName: String, val arabicName: String) {
    FAJR("Fajr", "الفجر"),
    SUNRISE("Sunrise", "الشروق"),
    DHUHR("Dhuhr", "الظهر"),
    ASR("Asr", "العصر"),
    SUNSET("Sunset", "الغروب"),
    MAGHRIB("Maghrib", "المغرب"),
    ISHA("Isha", "العشاء");

    companion object {
        /**
         * Safely converts a string to a [PrayerName] entry.
         */
        fun fromString(name: String): PrayerName? {
            return entries.find { it.name.equals(name, ignoreCase = true) }
        }
    }
}

/**
 * Data class holding geographical and temporal location information.
 * Used as input for the astronomical calculations.
 * 
 * @property latitude GPS latitude in decimal degrees.
 * @property longitude GPS longitude in decimal degrees.
 * @property timeZone IANA Timezone ID (e.g., "Europe/London").
 * @property cityName Human-readable name of the location.
 */
data class Location(
    val latitude: Double,
    val longitude: Double,
    val timeZone: String = "Asia/Riyadh",
    val cityName: String = "Makkah"
) {
    /**
     * Returns a formatted string of coordinates for display.
     */
    fun toCoordinatesString(): String {
        return String.format(Locale.US, "%.4f, %.4f", latitude, longitude)
    }
}

/**
 * Enumeration of supported astronomical calculation methods.
 * Each method defines specific angles for Fajr and Isha.
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
 * Supported juristic methods for Asr time calculation.
 * 
 * @property label Description of the method.
 * @property factor The shadow length factor (1.0 for Standard/Shafi'i, 2.0 for Hanafi).
 */
enum class AsrMethod(val label: String, val factor: Double) {
    STANDARD("Shafi'i/Maliki/Hanbali", 1.0),
    HANAFI("Hanafi", 2.0)
}

/**
 * Aggregate data class containing a full day's prayer schedule.
 * This is the primary data model passed from the Domain layer to the UI.
 * 
 * @property date The date of the schedule in YYYY-MM-DD format.
 * @property location The location these times were calculated for.
 * @property prayers List of individual [Prayer] objects.
 * @property calculationMethod The astronomical standard used.
 * @property asrMethod The juristic method used for Asr.
 */
data class PrayerSchedule(
    val date: String,
    val location: Location,
    val prayers: List<Prayer>,
    val calculationMethod: CalculationMethod,
    val asrMethod: AsrMethod
)

/**
 * Supported UI theme modes.
 */
enum class AppThemeMode {
    LIGHT, DARK, SYSTEM
}

/**
 * Global application settings persisted in SharedPreferences.
 * 
 * @property isAlarmsEnabled Global toggle for notification alarms.
 * @property notificationMinutesBefore Offset in minutes for early alerts.
 * @property savedLocations List of locations favorited by the user.
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
    val themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    val savedLocations: List<Location> = emptyList()
)
