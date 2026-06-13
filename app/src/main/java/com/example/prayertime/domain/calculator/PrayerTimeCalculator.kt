package com.example.prayertime.domain.calculator

import com.example.prayertime.data.model.*
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.*

/**
 * Core domain logic for calculating Muslim prayer times.
 * 
 * This class implements astronomical algorithms to determine the sun's position 
 * relative to any geographical location on Earth. It accounts for:
 * - Latitude and Longitude (Solar position)
 * - Timezone and Daylight Saving Time (Clock time)
 * - Atmospheric refraction (Sunrise/Sunset)
 * - Juristic shadow length rules (Asr)
 * - High latitude twilight persistent rules (Fajr/Isha fallbacks)
 */
class PrayerTimeCalculator {

    /**
     * Entry point for calculating the full day's prayer schedule.
     * 
     * @param location The geographical [Location] data.
     * @param date The specific [Date] to calculate times for.
     * @param calculationMethod The astronomical standard to apply.
     * @param asrMethod The juristic school for Asr calculation.
     * @return A [PrayerSchedule] containing all calculated times.
     */
    fun calculatePrayerTimes(
        location: Location,
        date: Date,
        calculationMethod: CalculationMethod,
        asrMethod: AsrMethod
    ): PrayerSchedule {
        // Use a UTC calendar for astronomical calculations to ensure stability across timezones
        val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        calendar.time = date
        // Calculate parameters for 12:00 UTC (Solar Noon average) to minimize daily error
        calendar.set(Calendar.HOUR_OF_DAY, 12)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)

        val jd = getJulianDate(calendar)
        val declination = getSunDeclination(jd)
        val eqTime = getEquationOfTime(jd)
        val tzOffset = getTimeZoneOffset(location.timeZone, date)

        // 1. Calculate Dhuhr (Solar Noon)
        // Formula: 12 + TZ_Offset - (Longitude / 15) - (EquationOfTime / 60)
        val dhuhr = 12.0 + tzOffset - (location.longitude / 15.0) - (eqTime / 60.0)

        // 2. Define Horizon Angle
        // Accounts for atmospheric refraction (34') and solar semi-diameter (16')
        val horizonAngle = -0.8333
        
        // 3. Calculate Raw Astronomical Times
        val fajrRaw = calculateTimeAtAngle(location, declination, dhuhr, -calculationMethod.fajrAngle, true)
        val sunriseRaw = calculateTimeAtAngle(location, declination, dhuhr, horizonAngle, true)
        val sunsetRaw = calculateTimeAtAngle(location, declination, dhuhr, horizonAngle, false)
        val asrRaw = calculateAsrTime(location, declination, dhuhr, asrMethod.factor)
        
        // 4. Maghrib Calculation
        // Standard is Sunset + a safety buffer (2 mins) to ensure the disk has fully set.
        val maghribRaw = if (sunsetRaw > 0) sunsetRaw + (2.0 / 60.0) else -1.0
        
        // 5. Isha Calculation
        val ishaRaw = if (calculationMethod == CalculationMethod.MECCA) {
            // Umm Al-Qura fixed duration (90 mins) rule
            if (maghribRaw > 0) maghribRaw + 1.5 else -1.0
        } else {
            calculateTimeAtAngle(location, declination, dhuhr, -calculationMethod.ishaAngle, false)
        }

        // 6. High Latitude Adjustments (e.g., UK in Summer)
        // If the sun doesn't reach -18 degrees (Twilight), apply the 1/7th Night fallback.
        val validatedSunrise = if (sunriseRaw < 0) dhuhr - 6.0 else sunriseRaw
        val validatedSunset = if (sunsetRaw < 0) dhuhr + 6.0 else sunsetRaw
        
        val nightDuration = (24.0 - validatedSunset) + validatedSunrise
        
        val validatedFajr = if (fajrRaw < 0 || fajrRaw >= validatedSunrise) {
            validatedSunrise - (nightDuration / 7.0)
        } else fajrRaw

        val validatedAsr = if (asrRaw < 0) dhuhr + 3.5 else asrRaw
        
        val validatedMaghrib = if (maghribRaw < 0) validatedSunset + (2.0 / 60.0) else maghribRaw
        
        val validatedIsha = if (ishaRaw < 0 || ishaRaw <= validatedMaghrib) {
            if (calculationMethod == CalculationMethod.MECCA) {
                validatedMaghrib + 1.5
            } else {
                validatedSunset + (nightDuration / 7.0)
            }
        } else ishaRaw

        // 7. Format into UI-ready Prayers
        val prayers = listOf(
            Prayer(PrayerName.FAJR, decimalToTime(validatedFajr), decimalToTime(validatedSunrise)),
            Prayer(PrayerName.SUNRISE, decimalToTime(validatedSunrise), decimalToTime(dhuhr)),
            Prayer(PrayerName.DHUHR, decimalToTime(dhuhr), decimalToTime(validatedAsr)),
            Prayer(PrayerName.ASR, decimalToTime(validatedAsr), decimalToTime(validatedSunset)),
            Prayer(PrayerName.SUNSET, decimalToTime(validatedSunset), decimalToTime(validatedMaghrib)),
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

    /**
     * Calculates the time when the sun reaches a specific altitude angle.
     * Uses the fundamental hour angle formula: cos(H) = (sin(alpha) - sin(phi)sin(delta)) / (cos(phi)cos(delta))
     */
    private fun calculateTimeAtAngle(location: Location, declination: Double, dhuhr: Double, angle: Double, isBeforeNoon: Boolean): Double {
        val phi = toRadians(location.latitude)
        val alpha = toRadians(angle)
        
        val cosH = (sin(alpha) - sin(phi) * sin(declination)) / (cos(phi) * cos(declination))
        
        if (cosH > 1.0 || cosH < -1.0) return -1.0 
        
        val hourAngle = toDegrees(acos(cosH))
        return dhuhr + (if (isBeforeNoon) -hourAngle else hourAngle) / 15.0
    }

    /**
     * Juristic calculation for Asr time based on shadow length.
     */
    private fun calculateAsrTime(location: Location, declination: Double, dhuhr: Double, factor: Double): Double {
        val phi = toRadians(location.latitude)
        val altitude = atan(1.0 / (factor + tan(abs(phi - declination))))
        
        val cosH = (sin(altitude) - sin(phi) * sin(declination)) / (cos(phi) * cos(declination))
        
        if (cosH > 1.0 || cosH < -1.0) return -1.0
        
        val hourAngle = toDegrees(acos(cosH))
        return dhuhr + hourAngle / 15.0
    }

    /**
     * Converts a standard Gregorian date into a Julian Date.
     */
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

    /**
     * Calculates Solar Declination.
     */
    private fun getSunDeclination(jd: Double): Double {
        val d = jd - 2451545.0
        val g = fixAngle(357.529 + 0.98560028 * d)
        val q = fixAngle(280.459 + 0.98564736 * d)
        val l = fixAngle(q + 1.915 * sin(toRadians(g)) + 0.020 * sin(toRadians(2 * g)))
        val e = 23.439 - 0.00000036 * d
        return asin(sin(toRadians(e)) * sin(toRadians(l)))
    }

    /**
     * Calculates the Equation of Time (discrepancy between solar and mean time).
     */
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
        
        return eqTime * 60.0 
    }

    /**
     * Resolves the local timezone offset including DST for a specific date.
     */
    private fun getTimeZoneOffset(timeZoneId: String, date: Date): Double {
        val tz = TimeZone.getTimeZone(timeZoneId)
        return tz.getOffset(date.time) / 3600000.0
    }

    /**
     * Normalizes an angle to the [0, 360) range.
     */
    private fun fixAngle(a: Double): Double {
        var res = a % 360.0
        if (res < 0) res += 360.0
        return res
    }

    /**
     * Formats decimal hours into an HH:mm string.
     */
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
