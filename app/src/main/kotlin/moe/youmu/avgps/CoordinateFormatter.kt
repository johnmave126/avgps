package moe.youmu.avgps

import android.location.Location
import java.util.Locale
import kotlin.math.abs

object CoordinateFormatter {
    fun formatLatitudeDms(latitude: Double): String {
        return formatSingleCoordinate(latitude, isLatitude = true)
    }

    fun formatLongitudeDms(longitude: Double): String {
        return formatSingleCoordinate(longitude, isLatitude = false)
    }

    fun formatDms(latitude: Double, longitude: Double): String {
        val latDms = formatLatitudeDms(latitude)
        val lonDms = formatLongitudeDms(longitude)
        return "($latDms, $lonDms)"
    }

    private fun formatSingleCoordinate(coordinate: Double, isLatitude: Boolean): String {
        val rawDeg = Location.convert(abs(coordinate), Location.FORMAT_SECONDS)
        val components = rawDeg.split(':')
        val degNum = components.getOrNull(0)?.toIntOrNull() ?: 0
        val minNum = components.getOrNull(1)?.toIntOrNull() ?: 0
        val secNum = components.getOrNull(2)?.toDoubleOrNull()?.toInt() ?: 0

        val degFormatted = "%03d".format(Locale.US, degNum)
        val minFormatted = "%02d".format(Locale.US, minNum)
        val secFormatted = "%02d".format(Locale.US, secNum)

        val direction = if (isLatitude) {
            if (coordinate < 0) "S" else "N"
        } else {
            if (coordinate < 0) "W" else "E"
        }

        return "$degFormatted°$minFormatted′$secFormatted″ $direction"
    }

    fun formatTimeSince(seconds: Long): String {
        if (seconds < 0) return "0s ago"
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        val secs = seconds % 60

        return when {
            hours > 0 -> "${hours}h ago"
            minutes > 0 -> "${minutes}m ago"
            else -> "${secs}s ago"
        }
    }
}
