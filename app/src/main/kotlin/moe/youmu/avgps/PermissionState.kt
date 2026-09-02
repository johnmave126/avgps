package moe.youmu.avgps

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri

enum class RequiredAccess(val title: String, val explanation: String) {
    LOCATION(
        "Precise location",
        "Reads GPS coordinates, altitude, speed, bearing, and satellite status.",
    ),
    BACKGROUND_LOCATION(
        "Background location",
        "Keeps sending GPS data to enabled EFB clients while avGPS is not visible.",
    ),
    BATTERY_OPTIMIZATION(
        "Unrestricted battery use",
        "Prevents Android from suspending GPS delivery during long flights.",
    ),
    NOTIFICATIONS(
        "Notifications",
        "Shows the required foreground-service status while GPS data is being shared.",
    ),
    LOCATION_SERVICES(
        "Location services",
        "Android location services and the GPS provider must be enabled.",
    ),
}

fun Context.missingAccess(): List<RequiredAccess> = RequiredAccess.entries.filterNot { access ->
    when (access) {
        RequiredAccess.LOCATION -> hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)
        RequiredAccess.BACKGROUND_LOCATION -> Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
                hasPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION)

        RequiredAccess.BATTERY_OPTIMIZATION ->
            (getSystemService(Context.POWER_SERVICE) as PowerManager).isIgnoringBatteryOptimizations(packageName)

        RequiredAccess.NOTIFICATIONS -> Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                (hasPermission(Manifest.permission.POST_NOTIFICATIONS) &&
                        NotificationManagerCompat.from(this).areNotificationsEnabled())

        RequiredAccess.LOCATION_SERVICES -> {
            val manager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
            manager.isLocationEnabled && manager.isProviderEnabled(LocationManager.GPS_PROVIDER)
        }
    }
}

fun Context.settingsIntent(access: RequiredAccess): Intent = when (access) {
    RequiredAccess.LOCATION,
    RequiredAccess.BACKGROUND_LOCATION,
        -> Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:$packageName".toUri())

    RequiredAccess.BATTERY_OPTIMIZATION -> Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
    RequiredAccess.NOTIFICATIONS -> Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
        putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
        putExtra(NotificationManager.EXTRA_NOTIFICATION_CHANNEL_ID, LocationService.NOTIFICATION_CHANNEL_ID)
    }

    RequiredAccess.LOCATION_SERVICES -> Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
}

private fun Context.hasPermission(permission: String): Boolean =
    ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
