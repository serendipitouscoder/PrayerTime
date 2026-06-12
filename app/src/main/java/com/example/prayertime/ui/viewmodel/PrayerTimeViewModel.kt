package com.example.prayertime.ui.viewmodel

import android.location.Geocoder
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.prayertime.data.model.*
import com.example.prayertime.domain.alarm.AlarmScheduler
import com.example.prayertime.domain.calculator.PrayerTimeCalculator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.*

/**
 * UI State for Prayer Times Screen
 */
data class PrayerTimeUiState(
    val prayerSchedule: PrayerSchedule? = null,
    val isLoading: Boolean = false,
    val isGeocoding: Boolean = false,
    val errorMessage: String? = null,
    val location: Location = Location(latitude = 21.4225, longitude = 39.8262),
    val isAlarmsEnabled: Boolean = false,
    val themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    val savedLocations: List<Location> = emptyList()
)

/**
 * View Model for managing prayer time business logic and UI state
 */
class PrayerTimeViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(PrayerTimeUiState())
    val uiState: StateFlow<PrayerTimeUiState> = _uiState.asStateFlow()

    private val calculator = PrayerTimeCalculator()
    private lateinit var alarmScheduler: AlarmScheduler
    private var appSettings = AppSettings()
    private lateinit var context: android.content.Context

    /**
     * Initialize the ViewModel with AlarmManager and Context
     */
    fun initialize(alarmManager: android.app.AlarmManager, context: android.content.Context) {
        this.alarmScheduler = AlarmScheduler(context, alarmManager)
        this.context = context
        loadSettings()
    }

    /**
     * Saves current settings to SharedPreferences
     */
    private fun saveSettings() {
        val prefs = context.getSharedPreferences("prayer_prefs", android.content.Context.MODE_PRIVATE)
        prefs.edit().apply {
            putFloat("lat", appSettings.latitude.toFloat())
            putFloat("lon", appSettings.longitude.toFloat())
            putString("city", appSettings.cityName)
            putString("tz", appSettings.timeZone)
            putString("theme", appSettings.themeMode.name)
            putBoolean("alarms", appSettings.isAlarmsEnabled)
            
            // Simple string encoding for saved locations: "city|lat|lon|tz;city|lat|lon|tz"
            val locationsString = appSettings.savedLocations.joinToString(";") { 
                "${it.cityName}|${it.latitude}|${it.longitude}|${it.timeZone}"
            }
            putString("saved_locations", locationsString)
            apply()
        }
    }

    /**
     * Loads settings from SharedPreferences
     */
    private fun loadSettings() {
        val prefs = context.getSharedPreferences("prayer_prefs", android.content.Context.MODE_PRIVATE)
        val savedLocsStr = prefs.getString("saved_locations", "") ?: ""
        val savedLocs = if (savedLocsStr.isEmpty()) emptyList() else {
            savedLocsStr.split(";").mapNotNull {
                val parts = it.split("|")
                if (parts.size == 4) {
                    Location(parts[1].toDouble(), parts[2].toDouble(), parts[3], parts[0])
                } else null
            }
        }

        appSettings = AppSettings(
            latitude = prefs.getFloat("lat", 21.4225f).toDouble(),
            longitude = prefs.getFloat("lon", 39.8262f).toDouble(),
            cityName = prefs.getString("city", "Makkah") ?: "Makkah",
            timeZone = prefs.getString("tz", "Asia/Riyadh") ?: "Asia/Riyadh",
            themeMode = AppThemeMode.valueOf(prefs.getString("theme", "SYSTEM") ?: "SYSTEM"),
            isAlarmsEnabled = prefs.getBoolean("alarms", true),
            savedLocations = savedLocs
        )
        
        _uiState.value = _uiState.value.copy(
            location = Location(appSettings.latitude, appSettings.longitude, appSettings.timeZone, appSettings.cityName),
            isAlarmsEnabled = appSettings.isAlarmsEnabled,
            themeMode = appSettings.themeMode,
            savedLocations = appSettings.savedLocations
        )
        loadPrayerTimes()
    }

    /**
     * Lookup coordinates for a given city name
     */
    fun lookupCoordinates(cityName: String, onResult: (Double, Double, String) -> Unit) {
        if (cityName.isBlank()) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isGeocoding = true, errorMessage = null)
            try {
                withContext(Dispatchers.IO) {
                    val geocoder = Geocoder(context, Locale.getDefault())
                    @Suppress("DEPRECATION")
                    val addresses = geocoder.getFromLocationName(cityName, 1)
                    
                    if (!addresses.isNullOrEmpty()) {
                        val address = addresses[0]
                        withContext(Dispatchers.Main) {
                            onResult(address.latitude, address.longitude, address.adminArea ?: cityName)
                            _uiState.value = _uiState.value.copy(isGeocoding = false)
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            _uiState.value = _uiState.value.copy(
                                isGeocoding = false,
                                errorMessage = "Could not find coordinates for '$cityName'"
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(
                        isGeocoding = false,
                        errorMessage = "Location lookup failed: ${e.message}"
                    )
                }
            }
        }
    }

    /**
     * Loads prayer times for today
     */
    fun loadPrayerTimes() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            
            try {
                // Move heavy calculation and alarm scheduling to a background thread
                val updatedState = withContext(Dispatchers.Default) {
                    val location = Location(
                        latitude = appSettings.latitude,
                        longitude = appSettings.longitude,
                        timeZone = appSettings.timeZone,
                        cityName = appSettings.cityName
                    )
                    
                    val schedule = calculator.calculatePrayerTimes(
                        location = location,
                        date = Date(),
                        calculationMethod = appSettings.calculationMethod,
                        asrMethod = appSettings.asrMethod
                    )
                    
                    // Schedule alarms if enabled
                    if (appSettings.isAlarmsEnabled && ::alarmScheduler.isInitialized) {
                        alarmScheduler.scheduleAlarms(schedule, appSettings.notificationMinutesBefore)
                    }

                    _uiState.value.copy(
                        prayerSchedule = schedule,
                        isLoading = false,
                        location = location,
                        isAlarmsEnabled = appSettings.isAlarmsEnabled,
                        themeMode = appSettings.themeMode
                    )
                }
                
                _uiState.value = updatedState
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = e.message ?: "Failed to calculate prayer times"
                )
            }
        }
    }

    /**
     * Updates the location settings
     */
    fun updateLocation(latitude: Double, longitude: Double, cityName: String, timeZone: String) {
        appSettings = appSettings.copy(
            latitude = latitude,
            longitude = longitude,
            cityName = cityName,
            timeZone = timeZone
        )
        saveSettings()
        loadPrayerTimes()
    }

    /**
     * Saves the current location to the saved locations list
     */
    fun saveCurrentLocation() {
        val currentLocation = Location(
            appSettings.latitude,
            appSettings.longitude,
            appSettings.timeZone,
            appSettings.cityName
        )
        
        if (!appSettings.savedLocations.any { it.cityName == currentLocation.cityName }) {
            appSettings = appSettings.copy(
                savedLocations = appSettings.savedLocations + currentLocation
            )
            _uiState.value = _uiState.value.copy(savedLocations = appSettings.savedLocations)
            saveSettings()
        }
    }

    /**
     * Deletes a location from saved locations
     */
    fun deleteSavedLocation(location: Location) {
        appSettings = appSettings.copy(
            savedLocations = appSettings.savedLocations.filter { it.cityName != location.cityName }
        )
        _uiState.value = _uiState.value.copy(savedLocations = appSettings.savedLocations)
        saveSettings()
    }

    /**
     * Updates the calculation method
     */
    fun updateCalculationMethod(method: CalculationMethod) {
        appSettings = appSettings.copy(calculationMethod = method)
        saveSettings()
        loadPrayerTimes()
    }

    /**
     * Updates the Asr calculation method
     */
    fun updateAsrMethod(asrMethod: AsrMethod) {
        appSettings = appSettings.copy(asrMethod = asrMethod)
        saveSettings()
        loadPrayerTimes()
    }

    /**
     * Toggles alarms on/off
     */
    fun toggleAlarms(enabled: Boolean) {
        viewModelScope.launch {
            appSettings = appSettings.copy(isAlarmsEnabled = enabled)
            _uiState.value = _uiState.value.copy(isAlarmsEnabled = enabled)
            saveSettings()
            
            withContext(Dispatchers.Default) {
                if (enabled && ::alarmScheduler.isInitialized) {
                    val schedule = _uiState.value.prayerSchedule
                    if (schedule != null) {
                        alarmScheduler.scheduleAlarms(schedule, appSettings.notificationMinutesBefore)
                    }
                } else {
                    if (::alarmScheduler.isInitialized) {
                        alarmScheduler.cancelAllAlarms()
                    }
                }
            }
        }
    }

    /**
     * Updates notification minutes before prayer
     */
    fun updateNotificationMinutes(minutes: Int) {
        appSettings = appSettings.copy(notificationMinutesBefore = minutes)
        saveSettings()
    }

    /**
     * Updates the application theme
     */
    fun updateTheme(themeMode: AppThemeMode) {
        appSettings = appSettings.copy(themeMode = themeMode)
        _uiState.value = _uiState.value.copy(themeMode = themeMode)
        saveSettings()
    }

    /**
     * Returns a list of major timezones for the dropdown
     */
    fun getMajorTimeZones(): List<String> {
        return listOf(
            "UTC",
            "Europe/London",
            "Europe/Paris",
            "Europe/Istanbul",
            "Asia/Riyadh",
            "Asia/Dubai",
            "Asia/Karachi",
            "Asia/Dhaka",
            "Asia/Jakarta",
            "Asia/Kuala_Lumpur",
            "Asia/Singapore",
            "Asia/Tokyo",
            "America/New_York",
            "America/Chicago",
            "America/Los_Angeles",
            "Australia/Sydney",
            "Africa/Cairo",
            "Africa/Casablanca",
            "Africa/Johannesburg"
        ).sorted()
    }

    /**
     * Clears error message
     */
    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }
}
