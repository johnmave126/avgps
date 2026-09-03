package moe.youmu.avgps

import java.util.Locale

// ============================================================================
// Aviation Conversion Constants (Exact International Standards)
// ============================================================================

object AviationConstants {
    const val METERS_PER_FOOT = 0.3048
    const val METERS_PER_NM = 1852.0
    const val METERS_PER_SM = 1609.344
    const val MPS_PER_KNOT = 1852.0 / 3600.0
    const val HPA_PER_INHG = 33.863886666667
}

// ============================================================================
// Unit Enums
// ============================================================================

enum class AltitudeUnit(val label: String) {
    METERS("Meters"),
    FEET("Feet"),
}

enum class SpeedUnit(val label: String) {
    METERS_PER_SECOND("m/s"),
    KILOMETERS_PER_HOUR("km/h"),
    MILES_PER_HOUR("mph"),
    KNOTS("knot"),
}

enum class DistanceUnit(val label: String) {
    METERS("Meters"),
    FEET("Feet"),
    NAUTICAL_MILES("Nautical miles"),
    STATUTE_MILES("Statute miles"),
}

enum class PressureUnit(val label: String) {
    INHG("inHg"),
    HPA("hPa"),
}

// ============================================================================
// Strongly-Typed Value Classes (Zero Allocation Overhead via @JvmInline)
// ============================================================================

@JvmInline
value class Altitude(val meters: Double) {
    fun toMeters(): Double = meters
    fun toFeet(): Double = meters / AviationConstants.METERS_PER_FOOT

    fun format(unit: AltitudeUnit): String = when (unit) {
        AltitudeUnit.METERS -> "${toMeters().toInt()} m"
        AltitudeUnit.FEET -> "${toFeet().toInt()} ft"
    }

    companion object {
        fun ofMeters(meters: Double): Altitude = Altitude(meters)
        fun ofFeet(feet: Double): Altitude = Altitude(feet * AviationConstants.METERS_PER_FOOT)
    }
}

@JvmInline
value class Speed(val metersPerSecond: Double) {
    fun toMps(): Double = metersPerSecond
    fun toKnots(): Double = metersPerSecond / AviationConstants.MPS_PER_KNOT
    fun toKmh(): Double = metersPerSecond * 3.6
    fun toMph(): Double = (metersPerSecond * 3600.0) / AviationConstants.METERS_PER_SM

    fun format(unit: SpeedUnit): String {
        val value = when (unit) {
            SpeedUnit.METERS_PER_SECOND -> toMps()
            SpeedUnit.KILOMETERS_PER_HOUR -> toKmh()
            SpeedUnit.MILES_PER_HOUR -> toMph()
            SpeedUnit.KNOTS -> toKnots()
        }
        return "%.1f %s".format(Locale.US, value, unit.label)
    }

    companion object {
        fun ofMps(mps: Double): Speed = Speed(mps)
        fun ofKnots(knots: Double): Speed = Speed(knots * AviationConstants.MPS_PER_KNOT)
    }
}

@JvmInline
value class Distance(val meters: Double) {
    fun toMeters(): Double = meters
    fun toFeet(): Double = meters / AviationConstants.METERS_PER_FOOT
    fun toNauticalMiles(): Double = meters / AviationConstants.METERS_PER_NM
    fun toStatuteMiles(): Double = meters / AviationConstants.METERS_PER_SM

    fun format(unit: DistanceUnit): String {
        val value = when (unit) {
            DistanceUnit.METERS -> toMeters()
            DistanceUnit.FEET -> toFeet()
            DistanceUnit.NAUTICAL_MILES -> toNauticalMiles()
            DistanceUnit.STATUTE_MILES -> toStatuteMiles()
        }
        val suffix = when (unit) {
            DistanceUnit.METERS -> "m"
            DistanceUnit.FEET -> "ft"
            DistanceUnit.NAUTICAL_MILES -> "nm"
            DistanceUnit.STATUTE_MILES -> "mi"
        }
        return if (unit == DistanceUnit.METERS || unit == DistanceUnit.FEET) {
            "${value.toInt()} $suffix"
        } else {
            "%.2f %s".format(Locale.US, value, suffix)
        }
    }

    companion object {
        fun ofMeters(meters: Double): Distance = Distance(meters)
        fun ofNauticalMiles(nm: Double): Distance = Distance(nm * AviationConstants.METERS_PER_NM)
    }
}

@JvmInline
value class Pressure(val hectopascals: Double) {
    fun toHpa(): Double = hectopascals
    fun toInHg(): Double = hectopascals / AviationConstants.HPA_PER_INHG

    fun format(unit: PressureUnit): String = when (unit) {
        PressureUnit.INHG -> "%.2f inHg".format(Locale.US, toInHg())
        PressureUnit.HPA -> "%.0f hPa".format(Locale.US, toHpa())
    }

    companion object {
        fun ofHpa(hpa: Double): Pressure = Pressure(hpa)
        fun ofInHg(inHg: Double): Pressure = Pressure(inHg * AviationConstants.HPA_PER_INHG)
    }
}
