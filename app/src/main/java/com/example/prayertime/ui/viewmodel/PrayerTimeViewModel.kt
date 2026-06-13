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
 * Immutable UI state for the Prayer Times Screen.
 * 
 * @property prayerSchedule The current daily schedule, or null if not yet calculated.
 * @property isLoading True when calculating astronomical times on a background thread.
 * @property isGeocoding True when performing a network lookup for city coordinates.
 * @property errorMessage Present if a calculation or lookup fails.
 * @property location The active location used for the current view.
 * @property isAlarmsEnabled Reflects the global alarm toggle state.
 * @property themeMode The active [AppThemeMode] (Light/Dark/System).
 * @property savedLocations List of user-favorited locations for quick access.
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
 * ViewModel responsible for bridging the Domain calculation logic with the Compose UI.
 * 
 * It manages:
 * 1. UI State through a [StateFlow] of [PrayerTimeUiState].
 * 2. Persistence of user preferences via SharedPreferences.
 * 3. Lifecycle-aware background threading using [viewModelScope] and Coroutines.
 * 4. Coordination between the [PrayerTimeCalculator] and [AlarmScheduler].
 */
class PrayerTimeViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(PrayerTimeUiState())
    /**
     * Public read-only stream of UI state. Composed UI elements observe this flow.
     */
    val uiState: StateFlow<PrayerTimeUiState> = _uiState.asStateFlow()

    private val calculator = PrayerTimeCalculator()
    private lateinit var alarmScheduler: AlarmScheduler
    private var appSettings = AppSettings()
    private lateinit var context: android.content.Context

    /**
     * Bootstraps the ViewModel with required system dependencies.
     * Called from [MainActivity.onCreate].
     * 
     * @param alarmManager System AlarmManager for scheduling notifications.
     * @param context Application context for resource and Preference access.
     */
    fun initialize(alarmManager: android.app.AlarmManager, context: android.content.Context) {
        this.alarmScheduler = AlarmScheduler(context, alarmManager)
        this.context = context
        loadSettings()
    }

    /**
     * Persists the current [appSettings] state to disk using SharedPreferences.
     * Executes on the caller's thread (typically Main).
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
            putString("method", appSettings.calculationMethod.name)
            putString("asr", appSettings.asrMethod.name)
            putInt("minutesBefore", appSettings.notificationMinutesBefore)
            
            // Serialize saved locations list into a delimited string format
            val locationsString = appSettings.savedLocations.joinToString(";") { 
                "${it.cityName}|${it.latitude}|${it.longitude}|${it.timeZone}"
            }
            putString("saved_locations", locationsString)
            apply()
        }
    }

    /**
     * Hydrates the [appSettings] and [_uiState] from disk on startup.
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
            calculationMethod = CalculationMethod.valueOf(prefs.getString("method", "MECCA") ?: "MECCA"),
            asrMethod = AsrMethod.valueOf(prefs.getString("asr", "STANDARD") ?: "STANDARD"),
            isAlarmsEnabled = prefs.getBoolean("alarms", true),
            notificationMinutesBefore = prefs.getInt("minutesBefore", 15),
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
     * Performs an asynchronous Geocoding lookup to convert a city name into GPS coordinates.
     * 
     * UI Integration: Triggered by the search icon in [LocationInputSection].
     * Callback [onResult]: Updates the local UI text fields in [MainActivity].
     */
    fun lookupCoordinates(cityName: String, onResult: (Double, Double, String) -> Unit) {
        if (cityName.isBlank()) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isGeocoding = true, errorMessage = null)
            try {
                // Offload blocking network I/O to the IO dispatcher
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
     * Orchestrates the calculation of prayer times and scheduling of alarms.
     * 
     * UI Integration: Called automatically on startup and whenever location/settings change.
     * Threading: 
     * - Uses [Dispatchers.Default] for CPU-intensive astronomical math.
     * - Uses [Dispatchers.Main] for UI state updates to ensure thread safety.
     */
    fun loadPrayerTimes() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            
            try {
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
                    
                    // Re-sync system alarms whenever the schedule is recalculated
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
     * Updates the active location and triggers a recalculation.
     * UI Integration: Called when the "Update Prayer Times" button is clicked.
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
     * Adds the current location to the [savedLocations] list.
     * UI Integration: Called by the "Heart" icon in the UI.
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
     * Removes a location from the favorites list.
     * UI Integration: Called by the 'X' on a saved location chip.
     */
    fun deleteSavedLocation(location: Location) {
        appSettings = appSettings.copy(
            savedLocations = appSettings.savedLocations.filter { it.cityName != location.cityName }
        )
        _uiState.value = _uiState.value.copy(savedLocations = appSettings.savedLocations)
        saveSettings()
    }

    /**
     * Updates the astronomical calculation standard.
     */
    fun updateCalculationMethod(method: CalculationMethod) {
        appSettings = appSettings.copy(calculationMethod = method)
        saveSettings()
        loadPrayerTimes()
    }

    /**
     * Updates the Asr calculation juristic school.
     */
    fun updateAsrMethod(asrMethod: AsrMethod) {
        appSettings = appSettings.copy(asrMethod = asrMethod)
        saveSettings()
        loadPrayerTimes()
    }

    /**
     * Globally enables or disables system alarms.
     * 
     * Logic: When enabled, immediately schedules today's alarms. 
     * When disabled, clears all pending intents from [AlarmManager].
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
     * Updates the early-warning lead time for notifications.
     */
    fun updateNotificationMinutes(minutes: Int) {
        appSettings = appSettings.copy(notificationMinutesBefore = minutes)
        saveSettings()
    }

    /**
     * Updates the UI theme (Light/Dark/System).
     */
    fun updateTheme(themeMode: AppThemeMode) {
        appSettings = appSettings.copy(themeMode = themeMode)
        _uiState.value = _uiState.value.copy(themeMode = themeMode)
        saveSettings()
    }

    /**
     * Helper to provide a curated list of timezones for the UI dropdown.
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
     * Resets the error state in the UI.
     */
    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }
}
