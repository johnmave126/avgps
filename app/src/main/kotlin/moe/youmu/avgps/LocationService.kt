package moe.youmu.avgps

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Binder
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import android.hardware.SensorManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress
import java.time.Instant

class LocationService : Service() {
    companion object {
        const val NOTIFICATION_CHANNEL_ID = "notify_channel"
        private const val NOTIFICATION_ID = 11111111
        private const val NOTIFICATION_CHANNEL_NAME = "Ownship GPS Server"
        private const val NOTIFICATION_CHANNEL_DESC = "Indicate that the GPS server is activated to feed EFBs"
        private const val TAG = "LocationService"
    }

    inner class LocalBinder : Binder() {
        fun getService(): LocationService = this@LocationService
    }

    private val binder: IBinder = LocalBinder()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private lateinit var gnssManager: GnssManager
    private lateinit var discoveryServer: EfbDiscoveryServer
    private lateinit var barometerManager: BarometerManager
    private lateinit var preferences: AppPreferences
    private var notificationManager: NotificationManager? = null

    val clientList = mutableListOf<Client>()
    private var uiMonitoring = false
    private var isForeground = false

    private val mutableState = MutableStateFlow(AvGpsState())
    val state: StateFlow<AvGpsState> = mutableState.asStateFlow()

    private var deviceName = "Ownship@Android"
    var lastLocation: LocationData? = null
        private set
    private var latestNmeaTimestamp: Long = 0
    private var latestPressureHpa: Float? = null
    private var reportPressureAltitude: Boolean = false
    private var altimeterSettingHpa: Double = 1013.25
    private var callsign: String = ""

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel()

        resolveDeviceName()

        preferences = AppPreferences(this)
        barometerManager = BarometerManager(this)
        mutableState.update { it.copy(hasBarometer = barometerManager.isSupported) }

        serviceScope.launch {
            preferences.reportPressureAltitude.collect { enabled ->
                reportPressureAltitude = enabled
                mutableState.update { it.copy(reportPressureAltitude = enabled) }
                updateRunningStatus()
            }
        }

        serviceScope.launch {
            preferences.altimeterSettingHpa.collect { hpa ->
                altimeterSettingHpa = hpa
                mutableState.update { it.copy(altimeterSettingHpa = hpa) }
            }
        }

        serviceScope.launch {
            preferences.callsign.collect { cs ->
                callsign = cs
                mutableState.update { it.copy(callsign = cs) }
            }
        }

        gnssManager = GnssManager(
            context = this,
            onLocationChanged = ::handleLocationUpdate,
            onSatellitesChanged = ::handleSatellitesUpdate,
            onNmeaTimestamp = { timestamp -> latestNmeaTimestamp = timestamp },
        )

        discoveryServer = EfbDiscoveryServer { host, message ->
            val source = InetSocketAddress(host, message.gdl90.port)
            handleEfbDiscovered(source, message.app)
        }
    }

    override fun onDestroy() {
        discoveryServer.stop()
        gnssManager.stopAll()
        barometerManager.stopListening()
        if (isForeground) stopForeground(STOP_FOREGROUND_REMOVE)
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val isStopping = intent?.getBooleanExtra("ownship.disable_all", false) ?: false
        if (isStopping) {
            for (i in clientList.indices) {
                clientList[i] = clientList[i].copy(isEnabled = false)
            }
            publishClients()
            updateRunningStatus()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder {
        discoveryServer.start()
        return binder
    }

    override fun onRebind(intent: Intent?) {
        super.onRebind(intent)
        discoveryServer.start()
    }

    override fun onUnbind(intent: Intent?): Boolean {
        discoveryServer.stop()
        return true
    }

    private fun handlePressureUpdate(pressureHpa: Float) {
        latestPressureHpa = pressureHpa
        mutableState.update { it.copy(pressureHpa = pressureHpa) }
    }

    private fun handleLocationUpdate(location: LocationData) {
        lastLocation = location
        mutableState.update { it.copy(location = location) }

        if (shouldDeliverToClients()) {
            val targets = activeClientAddresses()
            val pressureAltitudeFeet = if (reportPressureAltitude && latestPressureHpa != null) {
                val altMeters = SensorManager.getAltitude(SensorManager.PRESSURE_STANDARD_ATMOSPHERE, latestPressureHpa!!)
                Altitude.ofMeters(altMeters.toDouble()).toFeet()
            } else {
                null
            }
            val ownshipReport = generateOwnship(location, pressureAltitudeFeet, callSign = callsign)
            val ownshipAltitudeReport = generateOwnshipAltitude(location)
            serviceScope.launch {
                broadcastUdp(targets, ownshipReport, ownshipAltitudeReport)
            }
            notificationManager?.notify(NOTIFICATION_ID, produceNotification())
        }
    }

    private fun handleSatellitesUpdate(satellites: List<SatelliteData>) {
        mutableState.update { it.copy(satellites = satellites) }

        if (shouldDeliverToClients()) {
            val targets = activeClientAddresses()
            val heartbeatReport = generateHeartbeat(satellites.any { it.used }, latestNmeaTimestamp)
            serviceScope.launch {
                broadcastUdp(targets, heartbeatReport)
            }
        }
    }

    private fun handleEfbDiscovered(source: InetSocketAddress, appName: String) {
        val timestamp = Instant.now()
        val existing = clientList.find { it.source == source }
        if (existing != null) {
            val index = clientList.indexOf(existing)
            clientList[index] = existing.copy(lastDiscover = timestamp)
        } else {
            clientList.add(Client(source, appName, timestamp, false))
        }
        publishClients()
    }

    fun setUiMonitoring(enabled: Boolean) {
        uiMonitoring = enabled
        updateRunningStatus()
    }

    fun refreshMonitoring() = updateRunningStatus()

    fun setEfbEnabled(source: InetSocketAddress, enabled: Boolean) {
        val client = clientList.find { it.source == source }
        if (client != null) {
            if (!client.isEnabled && enabled) {
                serviceScope.launch {
                    sendUdp(client.source, generateID(deviceName))
                }
            }
            val index = clientList.indexOf(client)
            clientList[index] = client.copy(isEnabled = enabled)
            publishClients()
            updateRunningStatus()
        }
    }

    fun clearEFB() {
        clientList.clear()
        publishClients()
        updateRunningStatus()
    }

    private fun activeClientAddresses(): List<InetSocketAddress> =
        clientList.filter { it.isEnabled }.map { it.source }

    private fun shouldDeliverToClients(): Boolean =
        clientList.any { it.isEnabled }

    private fun publishClients() {
        mutableState.update { it.copy(clients = clientList.toList()) }
    }

    private fun updateRunningStatus() {
        val hasPermission = hasLocationPermission()
        val deliver = shouldDeliverToClients()
        val needLocation = (uiMonitoring || deliver) && hasPermission
        val needGnss = (uiMonitoring || deliver) && hasPermission
        val needBaro = (uiMonitoring || (deliver && reportPressureAltitude)) && barometerManager.isSupported

        if (needLocation) gnssManager.startLocationUpdates() else gnssManager.stopLocationUpdates()
        if (needGnss) gnssManager.startSatelliteUpdates() else gnssManager.stopSatelliteUpdates()
        if (needBaro) barometerManager.startListening(::handlePressureUpdate) else barometerManager.stopListening()

        if (deliver && !isForeground && hasPermission) {
            startForeground(NOTIFICATION_ID, produceNotification())
            isForeground = true
        } else if (!deliver && isForeground) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            isForeground = false
        }
    }

    private fun broadcastUdp(targets: List<InetSocketAddress>, vararg payloads: ByteArray) {
        if (targets.isEmpty() || payloads.isEmpty()) return
        for (target in targets) {
            sendUdp(target, *payloads)
        }
    }

    private fun sendUdp(target: InetSocketAddress, vararg payloads: ByteArray) {
        try {
            DatagramSocket().use { socket ->
                socket.connect(target)
                for (payload in payloads) {
                    socket.send(DatagramPacket(payload, payload.size, target))
                }
            }
        } catch (error: IOException) {
            Log.w(TAG, "Failed to transmit UDP packet to $target", error)
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            NOTIFICATION_CHANNEL_NAME,
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = NOTIFICATION_CHANNEL_DESC
        }
        notificationManager?.createNotificationChannel(channel)
    }

    private fun produceNotification(): Notification {
        val location = lastLocation
        val locationString = if (location != null) {
            CoordinateFormatter.formatDms(location.latitude, location.longitude)
        } else {
            "Unknown Location"
        }

        val numActiveClients = clientList.count { it.isEnabled }
        val titleString = "Serving $numActiveClients client${if (numActiveClients == 1) "" else "s"}"

        val disableAllIntent = Intent(this, LocationService::class.java).apply {
            putExtra("ownship.disable_all", true)
        }

        val launchIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )

        val stopPendingIntent = PendingIntent.getService(
            this,
            0,
            disableAllIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .addAction(0, "Stop", stopPendingIntent)
            .setContentText(locationString)
            .setContentTitle(titleString)
            .setOngoing(true)
            .setTicker(titleString)
            .setChannelId(NOTIFICATION_CHANNEL_ID)
            .setContentIntent(launchIntent)
            .setOnlyAlertOnce(true)
            .setWhen(System.currentTimeMillis())
            .build()
    }

    private fun resolveDeviceName() {
        val systemName = Settings.Global.getString(contentResolver, "bluetooth_name")
            ?: Settings.Global.getString(contentResolver, Settings.Global.DEVICE_NAME)
        if (systemName != null) {
            deviceName = systemName
        }
    }

    private fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
}
