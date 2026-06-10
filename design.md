# PrayerTime Application Design Document

## 1. Overview
PrayerTime is an Android application designed to calculate accurate Muslim prayer times based on the user's geographical location (latitude and longitude) and local timezone. Unlike static schedules, it uses astronomical formulas to determine the sun's position and the resulting prayer start and end times.

## 2. Architecture
The application follows a clean architecture pattern with the following components:

### 2.1. Domain Layer
- **PrayerTimeCalculator**: The core logic component. It calculates the five daily prayers (Fajr, Dhuhr, Asr, Maghrib, Isha) plus Sunrise.
- **Models**: `Location`, `PrayerSchedule`, `Prayer`, `CalculationMethod`, `AsrMethod`.

### 2.2. Data Layer
- **Repository**: Handles fetching location data and storing user settings.
- **Models**: Data classes for persistence and API communication.

### 2.3. Presentation Layer
- **MainActivity**: The main entry point using Jetpack Compose for the UI.
- **ViewModels**: Manage state and interact with the domain layer to provide data to the UI.

## 3. Calculation Logic
The prayer times are calculated using the following astronomical principles:

### 3.1. Solar Position
1. **Julian Date**: Calculated for the current day at 12:00 UTC to ensure stable astronomical parameters.
2. **Sun Declination ($\delta$)**: The angle of the sun relative to the celestial equator.
3. **Equation of Time ($EOT$)**: Corrects the difference between apparent solar time and mean solar time.

### 3.2. Prayer Time Formulas
- **Dhuhr**: Solar noon. $12 + \text{TimezoneOffset} - \frac{\text{Longitude}}{15} - \frac{EOT}{60}$
- **Sunrise/Sunset**: When the sun's center is at $-0.833^\circ$ (accounting for refraction).
- **Fajr/Isha**: When the sun reaches a specific angle below the horizon (e.g., $-18^\circ$).
- **Asr**: Based on the shadow length of an object. The shadow length $S = 1 + \tan(\phi - \delta)$ for Shafi'i and $S = 2 + \tan(\phi - \delta)$ for Hanafi.
- **Maghrib**: Calculated as Sunset + 2 minutes safety buffer to ensure the sun has completely disappeared below the horizon.
- **Isha**: Either a fixed duration after Maghrib (e.g., 90 mins for Umm Al-Qura) or based on sun angle.

## 4. Features
- **Location Awareness**: Uses GPS or manual coordinates.
- **Timezone & DST**: Automatically handles local time offsets and Daylight Saving Time.
- **Calculation Methods**: Supports various regional standards (MWL, ISNA, Umm Al-Qura, etc.).
- **Alarms/Notifications**: Schedules system alarms for each prayer time.

## 5. Technology Stack
- **Language**: Kotlin
- **UI Framework**: Jetpack Compose
- **Concurrency**: Kotlin Coroutines
- **Storage**: DataStore or SharedPreferences for settings.
