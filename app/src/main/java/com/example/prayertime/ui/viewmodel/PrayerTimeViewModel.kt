package com.example.prayertime.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.prayertime.data.model.*
import com.example.prayertime.domain.alarm.AlarmScheduler
import com.example.prayertime.domain.calculator.PrayerTimeCalculator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.*

/**
 * UI State for Prayer Times Screen
 */
data class PrayerTimeUiState(
    val prayerSchedule: PrayerSchedule? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val location: Location = Location(latitude = 21.4225, longitude = 39.8262),
    val isAlarmsEnabled: Boolean = false
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

    /**
     * Initialize the ViewModel with AlarmManager and Context
     */
    fun initialize(alarmManager: android.app.AlarmManager, context: android.content.Context) {
        alarmScheduler = AlarmScheduler(context, alarmManager)
    }

    /**
     * Loads prayer times for today
     */
    fun loadPrayerTimes() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            
            try {
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
                
                val updatedState = _uiState.value.copy(
                    prayerSchedule = schedule,
                    isLoading = false,
                    location = location,
                    isAlarmsEnabled = appSettings.isAlarmsEnabled
                )
                
                _uiState.value = updatedState
                
                // Schedule alarms if enabled
                if (appSettings.isAlarmsEnabled && ::alarmScheduler.isInitialized) {
                    alarmScheduler.scheduleAlarms(schedule, appSettings.notificationMinutesBefore)
                }
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
        loadPrayerTimes()
    }

    /**
     * Updates the calculation method
     */
    fun updateCalculationMethod(method: CalculationMethod) {
        appSettings = appSettings.copy(calculationMethod = method)
        loadPrayerTimes()
    }

    /**
     * Updates the Asr calculation method
     */
    fun updateAsrMethod(asrMethod: AsrMethod) {
        appSettings = appSettings.copy(asrMethod = asrMethod)
        loadPrayerTimes()
    }

    /**
     * Toggles alarms on/off
     */
    fun toggleAlarms(enabled: Boolean) {
        appSettings = appSettings.copy(isAlarmsEnabled = enabled)
        _uiState.value = _uiState.value.copy(isAlarmsEnabled = enabled)
        
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

    /**
     * Updates notification minutes before prayer
     */
    fun updateNotificationMinutes(minutes: Int) {
        appSettings = appSettings.copy(notificationMinutesBefore = minutes)
    }

    /**
     * Clears error message
     */
    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }
}
