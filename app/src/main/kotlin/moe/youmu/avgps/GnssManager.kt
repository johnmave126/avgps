package moe.youmu.avgps

import android.annotation.SuppressLint
import android.content.Context
import android.location.GnssStatus
import android.location.LocationListener
import android.location.LocationManager
import android.location.OnNmeaMessageListener
import android.os.Handler
import android.os.Looper

class GnssManager(
    context: Context,
    private val onLocationChanged: (LocationData) -> Unit,
    private val onSatellitesChanged: (List<SatelliteData>) -> Unit,
    private val onNmeaTimestamp: (Long) -> Unit,
) {
    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager?
    private val mainHandler = Handler(Looper.getMainLooper())

    private var isListeningLocation = false
    private var isListeningSatellites = false

    private val locationListener = LocationListener { location ->
        val data = LocationData(
            timestamp = location.time,
            longitude = location.longitude,
            latitude = location.latitude,
            altitude = location.altitude,
            speed = location.speed,
            bearing = location.bearing,
            horizontalAccuracy = location.accuracy,
            verticalAccuracy = location.verticalAccuracyMeters,
        )
        onLocationChanged(data)
    }

    private val gnssCallback = object : GnssStatus.Callback() {
        override fun onSatelliteStatusChanged(status: GnssStatus) {
            super.onSatelliteStatusChanged(status)
            val count = status.satelliteCount
            val satellites = (0 until count).map { i ->
                SatelliteData(
                    type = constellationTypeToString(status.getConstellationType(i)),
                    id = status.getSvid(i),
                    azimuth = status.getAzimuthDegrees(i),
                    elevation = status.getElevationDegrees(i),
                    cnr = status.getCn0DbHz(i),
                    used = status.usedInFix(i),
                )
            }
            onSatellitesChanged(satellites)
        }

        private fun constellationTypeToString(type: Int): String = when (type) {
            GnssStatus.CONSTELLATION_BEIDOU -> "Beidou"
            GnssStatus.CONSTELLATION_GALILEO -> "Galileo"
            GnssStatus.CONSTELLATION_GPS -> "GPS"
            GnssStatus.CONSTELLATION_GLONASS -> "GLONASS"
            GnssStatus.CONSTELLATION_IRNSS -> "IRNSS"
            GnssStatus.CONSTELLATION_QZSS -> "Michibiki"
            GnssStatus.CONSTELLATION_SBAS -> "SBAS"
            else -> "Unknown"
        }
    }

    private val nmeaListener = OnNmeaMessageListener { _, timestamp ->
        onNmeaTimestamp(timestamp)
    }

    @SuppressLint("MissingPermission")
    fun startLocationUpdates() {
        if (isListeningLocation) return
        locationManager?.requestLocationUpdates(
            LocationManager.GPS_PROVIDER,
            1000L,
            0.0f,
            locationListener,
            Looper.getMainLooper(),
        )
        isListeningLocation = true
    }

    fun stopLocationUpdates() {
        if (!isListeningLocation) return
        locationManager?.removeUpdates(locationListener)
        isListeningLocation = false
    }

    @SuppressLint("MissingPermission")
    fun startSatelliteUpdates() {
        if (isListeningSatellites) return
        locationManager?.registerGnssStatusCallback(gnssCallback, mainHandler)
        locationManager?.addNmeaListener(nmeaListener, mainHandler)
        isListeningSatellites = true
    }

    fun stopSatelliteUpdates() {
        if (!isListeningSatellites) return
        locationManager?.unregisterGnssStatusCallback(gnssCallback)
        locationManager?.removeNmeaListener(nmeaListener)
        isListeningSatellites = false
    }

    fun stopAll() {
        stopLocationUpdates()
        stopSatelliteUpdates()
    }
}
