package com.example.prayertime.domain.calculator

import com.example.prayertime.data.model.*
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.*

/**
 * Calculator for Muslim prayer times based on local sun position.
 * Uses astronomical formulas and accounts for location, timezone, and DST.
 */
class PrayerTimeCalculator {

    /**
     * Calculates all prayer times for a given location and date.
     * Properly handles longitude, timezone, and astronomical corrections.
     */
    fun calculatePrayerTimes(
        location: Location,
        date: Date,
        calculationMethod: CalculationMethod,
        asrMethod: AsrMethod
    ): PrayerSchedule {
        // Use a UTC calendar for astronomical calculations
        val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        calendar.time = date
        // Calculate parameters for 12:00 UTC (Noon) for daily averages
        calendar.set(Calendar.HOUR_OF_DAY, 12)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)

        val jd = getJulianDate(calendar)
        val declination = getSunDeclination(jd)
        val eqTime = getEquationOfTime(jd)
        val tzOffset = getTimeZoneOffset(location.timeZone, date)

        // Solar Noon (Dhuhr): 12 + TZ_Offset - Longitude/15 - EOT/60
        // Longitude is positive for East, negative for West.
        // Example: London (0.12 W) -> Longitude = -0.12. 
        // dhuhr = 12 + 1 - (-0.12/15) - EOT/60 = 13 + 0.008 - EOT/60
        val dhuhr = 12.0 + tzOffset - (location.longitude / 15.0) - (eqTime / 60.0)

        // Sunrise/Sunset angle (accounts for atmospheric refraction 34' and sun radius 16')
        // Total -50 arc minutes = -0.8333 degrees
        val horizonAngle = -0.8333
        
        // Calculate base times
        val fajrRaw = calculateTimeAtAngle(location, declination, dhuhr, -calculationMethod.fajrAngle, true)
        val sunriseRaw = calculateTimeAtAngle(location, declination, dhuhr, horizonAngle, true)
        val sunsetRaw = calculateTimeAtAngle(location, declination, dhuhr, horizonAngle, false)
        val asrRaw = calculateAsrTime(location, declination, dhuhr, asrMethod.factor)
        
        // Maghrib calculation
        // Traditionally Sunset. Some add a small safety buffer (e.g. 2-3 minutes).
        // We add 2 minutes (0.033 hours) to ensure the sun has completely set.
        val maghribRaw = if (sunsetRaw > 0) sunsetRaw + (2.0 / 60.0) else -1.0
        
        // Isha calculation
        val ishaRaw = if (calculationMethod == CalculationMethod.MECCA) {
            // Umm Al-Qura: 90 minutes (1.5 hours) after Maghrib
            if (maghribRaw > 0) maghribRaw + 1.5 else -1.0
        } else {
            calculateTimeAtAngle(location, declination, dhuhr, -calculationMethod.ishaAngle, false)
        }

        // Handle cases where sun doesn't reach specified angles (e.g. polar regions in summer)
        // Fallback to reasonable defaults relative to Dhuhr if calculation fails
        val validatedFajr = if (fajrRaw < 0) dhuhr - 7.5 else fajrRaw
        val validatedSunrise = if (sunriseRaw < 0) dhuhr - 6.5 else sunriseRaw
        val validatedAsr = if (asrRaw < 0) dhuhr + 3.5 else asrRaw
        val validatedMaghrib = if (maghribRaw < 0) dhuhr + 7.0 else maghribRaw
        val validatedIsha = if (ishaRaw < 0) dhuhr + 8.5 else ishaRaw

        val prayers = listOf(
            Prayer(PrayerName.FAJR, decimalToTime(validatedFajr), decimalToTime(validatedSunrise)),
            Prayer(PrayerName.SUNRISE, decimalToTime(validatedSunrise), decimalToTime(dhuhr)),
            Prayer(PrayerName.DHUHR, decimalToTime(dhuhr), decimalToTime(validatedAsr)),
            Prayer(PrayerName.ASR, decimalToTime(validatedAsr), decimalToTime(validatedMaghrib)),
            Prayer(PrayerName.MAGHRIB, decimalToTime(validatedMaghrib), decimalToTime(validatedIsha)),
            Prayer(PrayerName.ISHA, decimalToTime(validatedIsha), decimalToTime(validatedFajr + 24))
        )

        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        return PrayerSchedule(
            date = dateFormat.format(date),
            location = location,
            prayers = prayers,
            calculationMethod = calculationMethod,
            asrMethod = asrMethod
        )
    }

    private fun calculateTimeAtAngle(location: Location, declination: Double, dhuhr: Double, angle: Double, isBeforeNoon: Boolean): Double {
        val phi = toRadians(location.latitude)
        val alpha = toRadians(angle)
        
        // Hour Angle formula: cos(H) = (sin(alpha) - sin(phi) * sin(declination)) / (cos(phi) * cos(declination))
        val cosH = (sin(alpha) - sin(phi) * sin(declination)) / (cos(phi) * cos(declination))
        
        if (cosH > 1.0) return -1.0 // Sun always above this angle (polar day)
        if (cosH < -1.0) return -1.0 // Sun always below this angle (polar night)
        
        val hourAngle = toDegrees(acos(cosH))
        return dhuhr + (if (isBeforeNoon) -hourAngle else hourAngle) / 15.0
    }

    private fun calculateAsrTime(location: Location, declination: Double, dhuhr: Double, factor: Double): Double {
        val phi = toRadians(location.latitude)
        // Asr altitude: atan(1 / (factor + tan(abs(phi - declination))))
        val altitude = atan(1.0 / (factor + tan(abs(phi - declination))))
        
        val cosH = (sin(altitude) - sin(phi) * sin(declination)) / (cos(phi) * cos(declination))
        
        if (cosH > 1.0 || cosH < -1.0) return -1.0
        
        val hourAngle = toDegrees(acos(cosH))
        return dhuhr + hourAngle / 15.0
    }

    private fun getJulianDate(cal: Calendar): Double {
        var year = cal.get(Calendar.YEAR)
        var month = cal.get(Calendar.MONTH) + 1
        val day = cal.get(Calendar.DAY_OF_MONTH) + 
                  cal.get(Calendar.HOUR_OF_DAY) / 24.0 + 
                  cal.get(Calendar.MINUTE) / 1440.0

        if (month <= 2) {
            year -= 1
            month += 12
        }
        val a = floor(year / 100.0)
        val b = 2 - a + floor(a / 4.0)
        return floor(365.25 * (year + 4716)) + floor(30.6001 * (month + 1)) + day + b - 1524.5
    }

    private fun getSunDeclination(jd: Double): Double {
        val d = jd - 2451545.0
        val g = fixAngle(357.529 + 0.98560028 * d)
        val q = fixAngle(280.459 + 0.98564736 * d)
        val l = fixAngle(q + 1.915 * sin(toRadians(g)) + 0.020 * sin(toRadians(2 * g)))
        val e = 23.439 - 0.00000036 * d
        return asin(sin(toRadians(e)) * sin(toRadians(l)))
    }

    private fun getEquationOfTime(jd: Double): Double {
        val d = jd - 2451545.0
        val g = fixAngle(357.529 + 0.98560028 * d)
        val q = fixAngle(280.459 + 0.98564736 * d)
        val l = fixAngle(q + 1.915 * sin(toRadians(g)) + 0.020 * sin(toRadians(2 * g)))
        val e = 23.439 - 0.00000036 * d
        
        var ra = toDegrees(atan2(cos(toRadians(e)) * sin(toRadians(l)), cos(toRadians(l))))
        ra = fixAngle(ra) / 15.0
        
        var eqTime = q / 15.0 - ra
        if (eqTime > 20.0) eqTime -= 24.0
        if (eqTime < -20.0) eqTime += 24.0
        
        return eqTime * 60.0 // Result in minutes
    }

    private fun getTimeZoneOffset(timeZoneId: String, date: Date): Double {
        val tz = TimeZone.getTimeZone(timeZoneId)
        // This returns the offset including DST for the specific date
        return tz.getOffset(date.time) / 3600000.0
    }

    private fun fixAngle(a: Double): Double {
        var res = a % 360.0
        if (res < 0) res += 360.0
        return res
    }

    private fun decimalToTime(time: Double): String {
        if (time < 0) return "--:--"
        var t = time % 24
        if (t < 0) t += 24
        val hours = floor(t).toInt()
        val minutes = round((t - hours) * 60).toInt()
        
        var h = hours
        var m = minutes
        if (m >= 60) {
            m = 0
            h = (h + 1) % 24
        }
        return String.format(Locale.US, "%02d:%02d", h, m)
    }

    private fun toRadians(deg: Double): Double = Math.toRadians(deg)
    private fun toDegrees(rad: Double): Double = Math.toDegrees(rad)
}
