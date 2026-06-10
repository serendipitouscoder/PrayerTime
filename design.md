# PrayerTime Application Design Document

## 1. Overview
PrayerTime is an Android application designed to calculate accurate Muslim prayer times based on the user's geographical location (latitude and longitude) and local timezone. Unlike static schedules, it uses astronomical formulas to determine the sun's position and the resulting prayer start and end times.

## 2. Architecture
The application follows a clean architecture pattern with the following components:

### 2.1. Domain Layer
- **PrayerTimeCalculator**: The core logic component. It calculates the five daily prayers (Fajr, Dhuhr, Asr, Maghrib, Isha) plus Sunrise and Sunset.
- **Models**: `Location`, `PrayerSchedule`, `Prayer`, `CalculationMethod`, `AsrMethod`.

### 2.2. Data Layer
- **Repository**: Handles fetching location data and storing user settings.
- **Models**: Data classes for persistence and API communication.

### 2.3. Presentation Layer
- **MainActivity**: The main entry point using Jetpack Compose for the UI.
- **ViewModels**: Manage state and interact with the domain layer to provide data to the UI.

## 3. Calculation Logic & Mathematical Formulas

The application employs astronomical algorithms to determine the position of the sun and subsequently the prayer times.

### 3.1. Fundamental Astronomical Parameters

#### 1. Julian Date (JD)
Calculated for the date and time (UTC):
$$JD = 367 \cdot Y - \lfloor \frac{7(Y + \lfloor \frac{M+9}{12} \rfloor)}{4} \rfloor + \lfloor \frac{275M}{9} \rfloor + D + 1721013.5 + \frac{h + \frac{m}{60}}{24}$$
Where $Y$, $M$, $D$, $h$, $m$ are Year, Month, Day, Hour, and Minute.

#### 2. Sun Declination ($\delta$)
Determines the sun's angle relative to the celestial equator.
$$d = JD - 2451545.0$$
$$g = 357.529 + 0.98560028 \cdot d \pmod{360}$$
$$q = 280.459 + 0.98564736 \cdot d \pmod{360}$$
$$L = q + 1.915 \cdot \sin(g) + 0.020 \cdot \sin(2g) \pmod{360}$$
$$\epsilon = 23.439 - 0.00000036 \cdot d$$
$$\delta = \arcsin(\sin(\epsilon) \cdot \sin(L))$$

#### 3. Equation of Time ($EOT$)
Corrects the difference between solar time and clock time:
$$RA = \arctan2(\cos(\epsilon) \cdot \sin(L), \cos(L)) \pmod{360}$$
$$EOT = \frac{q}{15} - \frac{RA}{15}$$
(Result converted to minutes for use in Dhuhr formula).

### 3.2. Prayer Time Calculations

Let:
- $\phi$: Local Latitude
- $\lambda$: Local Longitude
- $TZ$: Timezone Offset (hours)
- $H(\alpha)$: Hour Angle for a given sun altitude $\alpha$

#### Hour Angle Formula
The hour angle $H$ for a sun altitude $\alpha$ is:
$$H(\alpha) = \arccos\left(\frac{\sin(\alpha) - \sin(\phi)\sin(\delta)}{\cos(\phi)\cos(\delta)}\right)$$

#### 1. Dhuhr (Solar Noon)
$$Dhuhr = 12 + TZ - \frac{\lambda}{15} - \frac{EOT}{60}$$

#### 2. Sunrise / Sunset
Defined when the sun's center is at $\alpha = -0.8333^\circ$ (accounting for atmospheric refraction and solar radius):
$$Sunrise = Dhuhr - \frac{H(-0.8333)}{15}$$
$$Sunset = Dhuhr + \frac{H(-0.8333)}{15}$$

#### 3. Fajr
Fajr begins when the sun is at a specific angle below the horizon ($-\alpha_{fajr}$):
$$Fajr = Dhuhr - \frac{H(-\alpha_{fajr})}{15}$$
(Common angles: $18^\circ, 15^\circ$, or $18.5^\circ$)

#### 4. Asr
Asr time is determined by the shadow length $s$. The required sun altitude $\alpha_{asr}$ is:
$$\alpha_{asr} = \text{arccot}(s + \tan(|\phi - \delta|))$$
Where $s = 1$ for Shafi'i/Standard and $s = 2$ for Hanafi.
$$Asr = Dhuhr + \frac{H(\alpha_{asr})}{15}$$

#### 5. Maghrib
Calculated as Sunset plus a safety buffer (e.g., 2 minutes):
$$Maghrib = Sunset + \frac{2}{60}$$

#### 6. Isha
Isha begins at a specific angle below the horizon ($-\alpha_{isha}$):
$$Isha = Dhuhr + \frac{H(-\alpha_{isha})}{15}$$
*Note: For Umm Al-Qura (Mecca), Isha is defined as Maghrib + 90 minutes.*

### 3.3. High Latitude Adjustments
In regions where astronomical twilight does not occur (e.g., North during summer), the **One-Seventh of the Night** rule is applied:
$$NightDuration = (24 - Sunset) + Sunrise$$
$$Fajr = Sunrise - \frac{NightDuration}{7}$$
$$Isha = Sunset + \frac{NightDuration}{7}$$

## 4. Features
- **Location Awareness**: Uses GPS or manual coordinates.
- **Dynamic Banner**: The top bar updates to show the currently selected city.
- **Configuration Hub**: A dedicated settings menu for managing app preferences.
- **Theme Customization**: Supports Light, Dark, and System theme modes.
- **Automatic GPS Lookup**: Integrates Geocoder service to find coordinates from city names.
- **High Latitude Support**: Robust handling of summer nights via astronomical adjustments.
- **Quick Clearing**: One-tap clear buttons for all user input fields.

## 5. Technology Stack
- **Language**: Kotlin
- **UI Framework**: Jetpack Compose
- **Concurrency**: Kotlin Coroutines
- **Storage**: DataStore / SharedPreferences
- **Location Services**: Android Geocoder API
