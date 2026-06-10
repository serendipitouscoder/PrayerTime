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
        // Use a UTC calendar for astronomical calculations to avoid local drift
        val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        calendar.time = date
        // Calculate parameters for 12:00 UTC (Noon) to be most accurate for the day
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
        val dhuhr = 12.0 + tzOffset - (location.longitude / 15.0) - (eqTime / 60.0)

        // Sunrise/Sunset angle (accounts for atmospheric refraction and sun radius)
        val horizonAngle = -0.833
        
        val fajr = calculateTimeAtAngle(location, declination, dhuhr, -calculationMethod.fajrAngle, true)
        val sunrise = calculateTimeAtAngle(location, declination, dhuhr, horizonAngle, true)
        val sunset = calculateTimeAtAngle(location, declination, dhuhr, horizonAngle, false)
        val asr = calculateAsrTime(location, declination, dhuhr, asrMethod.factor)
        val maghrib = sunset
        
        val isha = if (calculationMethod == CalculationMethod.MECCA) {
            maghrib + 1.5 // Umm Al-Qura: 90 minutes after Maghrib (Standard)
        } else {
            calculateTimeAtAngle(location, declination, dhuhr, -calculationMethod.ishaAngle, false)
        }

        // Handle cases where sun doesn't reach specified angles (e.g. polar regions)
        val validatedFajr = if (fajr < 0) dhuhr - 7.0 else fajr
        val validatedSunrise = if (sunrise < 0) dhuhr - 6.0 else sunrise
        val validatedSunset = if (sunset < 0) dhuhr + 6.0 else sunset
        val validatedAsr = if (asr < 0) dhuhr + 3.0 else asr
        val validatedIsha = if (isha < 0) dhuhr + 8.0 else isha

        val prayers = listOf(
            Prayer(PrayerName.FAJR, decimalToTime(validatedFajr), decimalToTime(validatedSunrise)),
            Prayer(PrayerName.SUNRISE, decimalToTime(validatedSunrise), decimalToTime(dhuhr)),
            Prayer(PrayerName.DHUHR, decimalToTime(dhuhr), decimalToTime(validatedAsr)),
            Prayer(PrayerName.ASR, decimalToTime(validatedAsr), decimalToTime(validatedSunset)),
            Prayer(PrayerName.MAGHRIB, decimalToTime(validatedSunset), decimalToTime(validatedIsha)),
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
        
        if (cosH > 1.0 || cosH < -1.0) return -1.0 // Sun never reaches this angle on this day
        
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
