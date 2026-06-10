package com.example.prayertime

import android.Manifest
import android.app.AlarmManager
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.prayertime.data.model.*
import com.example.prayertime.ui.theme.PrayerTimeTheme
import com.example.prayertime.ui.viewmodel.PrayerTimeViewModel
import java.util.Calendar
import java.util.Locale

class MainActivity : ComponentActivity() {

    private val viewModel: PrayerTimeViewModel by viewModels()

    private val requestPermissionLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            Toast.makeText(this, "Notification permission granted", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Notification permission denied", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize ViewModel
        val alarmManager = getSystemService(ALARM_SERVICE) as AlarmManager
        viewModel.initialize(alarmManager, this)

        setContent {
            PrayerTimeTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    PrayerTimeScreen(viewModel)
                }
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun PrayerTimeScreen(viewModel: PrayerTimeViewModel) {
        var latitude by remember { mutableStateOf("") }
        var longitude by remember { mutableStateOf("") }
        var timezone by remember { mutableStateOf("Asia/Riyadh") }
        var cityName by remember { mutableStateOf("") }

        val uiState by viewModel.uiState.collectAsState()

        LaunchedEffect(Unit) {
            viewModel.loadPrayerTimes()
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Prayer Times - Local Sun Position") },
                    actions = {
                        IconButton(onClick = { viewModel.loadPrayerTimes() }) {
                            Icon(Icons.Default.Refresh, "Refresh")
                        }
                        IconButton(onClick = {
                            viewModel.toggleAlarms(!uiState.isAlarmsEnabled)
                            if (!uiState.isAlarmsEnabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                if (ContextCompat.checkSelfPermission(
                                        this@MainActivity,
                                        Manifest.permission.POST_NOTIFICATIONS
                                    ) != PackageManager.PERMISSION_GRANTED
                                ) {
                                    requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                }
                            }
                        }) {
                            Icon(
                                imageVector = if (uiState.isAlarmsEnabled) Icons.Default.Notifications else Icons.Default.NotificationsOff,
                                contentDescription = "Toggle Alarms",
                                tint = if (uiState.isAlarmsEnabled) Color.Green else Color.Gray
                            )
                        }
                    }
                )
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                // Input section for location
                LocationInputSection(
                    latitude = latitude,
                    longitude = longitude,
                    timezone = timezone,
                    cityName = cityName,
                    onLatitudeChange = { latitude = it },
                    onLongitudeChange = { longitude = it },
                    onTimezoneChange = { timezone = it },
                    onCityNameChange = { cityName = it },
                    onCalculateClick = {
                        viewModel.updateLocation(
                            latitude = latitude.toDoubleOrNull() ?: 21.4225,
                            longitude = longitude.toDoubleOrNull() ?: 39.8262,
                            cityName = cityName.ifBlank { "Unknown" },
                            timeZone = timezone
                        )
                    }
                )

                // Prayer times section
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    when {
                        uiState.isLoading -> {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator()
                            }
                        }
                        uiState.errorMessage != null -> {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = "Error: ${uiState.errorMessage}",
                                        color = Color.Red
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Button(onClick = { viewModel.loadPrayerTimes() }) {
                                        Text("Retry")
                                    }
                                }
                            }
                        }
                        uiState.prayerSchedule != null -> {
                            PrayerTimeList(uiState.prayerSchedule!!)
                        }
                    }
                }
            }
        }
    }

    @Composable
    fun LocationInputSection(
        latitude: String,
        longitude: String,
        timezone: String,
        cityName: String,
        onLatitudeChange: (String) -> Unit,
        onLongitudeChange: (String) -> Unit,
        onTimezoneChange: (String) -> Unit,
        onCityNameChange: (String) -> Unit,
        onCalculateClick: () -> Unit
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Enter Location Details",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                Text(
                    text = "Prayer times calculated based on local sun position at entered coordinates",
                    fontSize = 12.sp,
                    color = Color.Gray,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                // City Name
                OutlinedTextField(
                    value = cityName,
                    onValueChange = onCityNameChange,
                    label = { Text("City Name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Latitude and Longitude row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = latitude,
                        onValueChange = onLatitudeChange,
                        label = { Text("Latitude") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        placeholder = { Text("e.g., 21.4225 (Makkah)") }
                    )

                    OutlinedTextField(
                        value = longitude,
                        onValueChange = onLongitudeChange,
                        label = { Text("Longitude") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        placeholder = { Text("e.g., 39.8262") }
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Timezone
                OutlinedTextField(
                    value = timezone,
                    onValueChange = onTimezoneChange,
                    label = { Text("Timezone (e.g., Asia/Riyadh, Europe/London, America/New_York)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    leadingIcon = {
                        Icon(Icons.Default.AccessTime, contentDescription = "Timezone")
                    }
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Calculate button
                Button(
                    onClick = onCalculateClick,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Calculate, contentDescription = "Calculate", modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Calculate Prayer Times")
                }
            }
        }
    }

    @Composable
    fun PrayerTimeList(schedule: PrayerSchedule) {
        val upcomingPrayerName = findUpcomingPrayer(schedule.prayers)
        var selectedPrayer by remember { mutableStateOf<Prayer?>(null) }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Location and date info - showing local position and timezone
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = schedule.location.cityName,
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        // Local coordinates
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(bottom = 4.dp)
                        ) {
                            Icon(
                                Icons.Default.LocationOn,
                                contentDescription = "Location",
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = String.format(Locale.US, "Lat: %.4f°, Lon: %.4f°", schedule.location.latitude, schedule.location.longitude),
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                        
                        // Timezone
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(bottom = 4.dp)
                        ) {
                            Icon(
                                Icons.Default.AccessTime,
                                contentDescription = "Timezone",
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Timezone: ${schedule.location.timeZone}",
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                        
                        // Date
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.CalendarToday,
                                contentDescription = "Date",
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = schedule.date,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }
                }
            }

            // Prayer times with durations
            items(schedule.prayers, key = { it.name.name }) { prayer ->
                PrayerTimeCardWithDuration(
                    prayer = prayer,
                    isUpcoming = prayer.name.name == upcomingPrayerName,
                    isSelected = prayer == selectedPrayer,
                    onClick = { selectedPrayer = if (selectedPrayer == prayer) null else prayer },
                    calculateDuration = {
                        calculatePrayerDuration(prayer.startTime, prayer.endTime)
                    }
                )
            }

            // Calculation method info
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "Calculation Details",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Method: ${schedule.calculationMethod.displayName}",
                            fontSize = 12.sp
                        )
                        Text(
                            text = "Asr Method: ${schedule.asrMethod.label}",
                            fontSize = 12.sp
                        )
                        Text(
                            text = "Based on: Local Sun Position",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }

    @Composable
    fun PrayerTimeCardWithDuration(
        prayer: Prayer,
        isUpcoming: Boolean,
        isSelected: Boolean,
        onClick: () -> Unit,
        calculateDuration: () -> String
    ) {
        val duration = calculateDuration()
        
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isUpcoming) {
                    MaterialTheme.colorScheme.primary
                } else if (isSelected) {
                    MaterialTheme.colorScheme.secondary
                } else {
                    MaterialTheme.colorScheme.surface
                }
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = if (isUpcoming) 4.dp else 2.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .clickable(onClick = onClick),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = prayer.name.name,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = if (isUpcoming) Color.White else MaterialTheme.colorScheme.onSurface
                        )
                        if (isUpcoming) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                color = Color.White.copy(alpha = 0.3f),
                                shape = MaterialTheme.shapes.small
                            ) {
                                Text(
                                    text = "UPCOMING",
                                    fontSize = 10.sp,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    // Duration
                    Text(
                        text = "Duration: $duration",
                        fontSize = 12.sp,
                        color = if (isUpcoming) Color.White.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        fontWeight = FontWeight.Medium
                    )
                }

                Column(
                    horizontalAlignment = Alignment.End,
                    modifier = Modifier.widthIn(min = 80.dp)
                ) {
                    Text(
                        text = prayer.startTime,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isUpcoming) Color.White else MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "to ${prayer.endTime}",
                        fontSize = 12.sp,
                        color = if (isUpcoming) Color.White.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }
        }
    }

    /**
     * Calculate prayer duration from start and end times
     */
    private fun calculatePrayerDuration(startTime: String, endTime: String): String {
        return try {
            val startParts = startTime.split(":")
            val endParts = endTime.split(":")
            
            val startMinutes = startParts[0].toInt() * 60 + startParts[1].toInt()
            var endMinutes = endParts[0].toInt() * 60 + endParts[1].toInt()
            
            // Handle if end time is next day (e.g., Isha to Fajr)
            if (endMinutes <= startMinutes) {
                endMinutes += 24 * 60
            }
            
            val durationMinutes = endMinutes - startMinutes
            val hours = durationMinutes / 60
            val minutes = durationMinutes % 60
            
            if (hours > 0 && minutes > 0) {
                "$hours hr $minutes min"
            } else if (hours > 0) {
                "$hours hr"
            } else {
                "$minutes min"
            }
        } catch (_: Exception) {
            "-"
        }
    }

    /**
     * Find the next upcoming prayer based on current time
     */
    private fun findUpcomingPrayer(prayers: List<Prayer>): String? {
        val currentCalendar = Calendar.getInstance()
        val currentMinutes = currentCalendar.get(Calendar.HOUR_OF_DAY) * 60 +
                currentCalendar.get(Calendar.MINUTE)

        for (prayer in prayers) {
            val parts = prayer.startTime.split(":")
            val prayerMinutes = parts[0].toInt() * 60 + parts[1].toInt()

            if (prayerMinutes > currentMinutes) {
                return prayer.name.name
            }
        }

        // If no prayer found for today, return Fajr (next day)
        return PrayerName.FAJR.name
    }
}
