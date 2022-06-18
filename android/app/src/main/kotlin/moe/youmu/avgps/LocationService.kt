package moe.youmu.avgps

import android.Manifest
import android.annotation.SuppressLint
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.location.*
import android.os.*
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import io.flutter.plugin.common.EventChannel
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.net.*
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.*

class LocationService : Service() {
    inner class LocalBinder : Binder() {
        fun getService(): LocationService {
            return this@LocationService
        }
    }

    private val TAG = "LocationService"
    private val NOTIFICATION_ID = 11111111
    private val NOTIFICATION_ID_NAME = "server_notification"
    private val NOTIFICATION_CHANNEL_ID = "notify_channel"
    private val NOTIFICATION_CHANNEL_NAME = "GPS Server"
    private val NOTIFICATION_CHANNEL_DESC = "Indicate that the GPS server is activated to feed EFBs"

    private var deviceName = "avGPS@Android".toByteArray()
    private val binder: IBinder = LocalBinder()

    private var notificationManager: NotificationManager? = null
    private var locationManager: LocationManager? = null
    private var serviceHandler: Handler? = null

    private var locationSinks: MutableList<EventChannel.EventSink> = mutableListOf()
    private var gnssSinks: MutableList<EventChannel.EventSink> = mutableListOf()
    private var clientSinks: MutableList<EventChannel.EventSink> = mutableListOf()
    var clientList: MutableList<Client> = mutableListOf()

    private var isLocationRunning = false
    private var isGnssRunning = false
    private var isForeground = false
    private var isChangingConfiguration = false

    var lastLocation: LocationData? = null
    var timestamp: Long = 0

    private var discoveryThread = makeDiscoveryThread()

    private val onLocationUpdate = LocationListener { location ->
        val data = LocationData(
            location.time,
            location.longitude,
            location.latitude,
            location.altitude,
            location.speed,
            location.bearing,
            location.accuracy,
            location.verticalAccuracyMeters
        )
        lastLocation = data
        if (locationSinks.isNotEmpty()) {
            val message = Json.encodeToString(data)
            for (sink in locationSinks) {
                sink.success(message)
            }
        }

        if (shouldRunDelivery()) {
            val clients = clientList.map { client ->
                InetSocketAddress(
                    client.source.address,
                    client.source.port
                )
            }.toTypedArray()
            serviceHandler?.post {
                val ownshipReport = generateOwnship(data)
                val ownshipAltitudeReport = generateOwnshipAltitude(data)
                for (client in clients) {
                    val socket = DatagramSocket(null)
                    socket.connect(client)
                    //socket.send(DatagramPacket(heartbeatReport, heartbeatReport.size, client))
                    socket.send(DatagramPacket(ownshipReport, ownshipReport.size, client))
                    socket.send(
                        DatagramPacket(
                            ownshipAltitudeReport,
                            ownshipAltitudeReport.size,
                            client
                        )
                    )
                    socket.close()
                }
            }

            notificationManager?.notify(NOTIFICATION_ID, produceNotification())
        }
    }

    private val onGnssUpdate = object : GnssStatus.Callback() {
        override fun onSatelliteStatusChanged(status: GnssStatus) {
            super.onSatelliteStatusChanged(status)

            val numSatellite = status.satelliteCount
            val satellites = (0 until numSatellite).map { i ->
                SatelliteData(
                    typeToString(status.getConstellationType(i)),
                    status.getSvid(i),
                    status.getAzimuthDegrees(i),
                    status.getElevationDegrees(i),
                    status.getCn0DbHz(i),
                    status.usedInFix(i)
                )
            }.toList()

            if (gnssSinks.isNotEmpty()) {
                val message = Json.encodeToString(satellites)
                for (sink in gnssSinks) {
                    sink.success(message)
                }
            }

            if (shouldRunDelivery()) {
                val clients = clientList.map { client ->
                    InetSocketAddress(
                        client.source.address,
                        client.source.port
                    )
                }.toTypedArray()
                serviceHandler?.post {
                    val heartbeatReport =
                        generateHeartbeat(satellites.any { satellite -> satellite.used }, timestamp)
                    for (client in clients) {
                        val socket = DatagramSocket(null)
                        socket.connect(client)
                        socket.send(DatagramPacket(heartbeatReport, heartbeatReport.size, client))
                        socket.close()
                    }
                }
                //Log.d(TAG, "gnss update: $message")
            }
        }

        fun typeToString(type: Int): String {
            return when (type) {
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
    }

    private val onNmeaMessageListener =
        OnNmeaMessageListener { _, ts -> timestamp = ts }

    override fun onCreate() {
        super.onCreate()

        val handlerThread = HandlerThread(TAG)
        handlerThread.start()
        serviceHandler = Handler(handlerThread.looper)

        notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val notificationChannel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            NOTIFICATION_CHANNEL_NAME,
            NotificationManager.IMPORTANCE_DEFAULT
        )
        notificationChannel.description = NOTIFICATION_CHANNEL_DESC
        notificationManager?.createNotificationChannel(notificationChannel)

        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager?

        val systemDeviceName = Settings.Global.getString(contentResolver, "bluetooth_name")
            ?: Settings.Global.getString(contentResolver, Settings.Global.DEVICE_NAME)
        if (systemDeviceName != null) {
            deviceName = truncateString(systemDeviceName, 16)
        }

        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val isStopping = intent?.getBooleanExtra("avgps.disable_all", false) ?: false
        if (isStopping) {
            clientList.forEach { client -> client.isEnabled = false }
            updateRunningStatus()
            deliverEFBMessage()
        }
        return START_STICKY
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        isChangingConfiguration = true
    }

    override fun onBind(intent: Intent?): IBinder {
        discoveryThread = makeDiscoveryThread()
        discoveryThread.start()
        isChangingConfiguration = false
        return binder
    }

    override fun onRebind(intent: Intent?) {
        super.onRebind(intent)
        discoveryThread = makeDiscoveryThread()
        discoveryThread.start()
        isChangingConfiguration = false
    }

    override fun onUnbind(intent: Intent?): Boolean {
        if (!isChangingConfiguration) {
            discoveryThread.interrupt()
        }
        return true
    }

    fun onEFBDiscovery(host: InetAddress, data: EFBDiscoveryMessage) {
        val source = InetSocketAddress(host, data.GDL90.port)
        val elem = clientList.find { client -> client.source == source }
        val timestamp = Date.from(Instant.now())
        if (elem != null) {
            elem.lastDiscover = timestamp
        } else {
            clientList.add(Client(source, data.App, timestamp, false))
        }

        deliverEFBMessage()
    }

    fun setEFBenable(source: InetSocketAddress, enabled: Boolean) {
        val client = clientList.find { client -> client.source == source }
        if (client != null) {
            if (!client.isEnabled && enabled) {
                serviceHandler?.post {
                    val idReport = generateID(deviceName)
                    val socket = DatagramSocket(null)
                    socket.connect(client.source)
                    socket.send(DatagramPacket(idReport, idReport.size, client.source))
                }
            }
            client.isEnabled = enabled
            updateRunningStatus()
            deliverEFBMessage()
        }
    }

    fun clearEFB() {
        clientList.clear()
        updateRunningStatus()
        deliverEFBMessage()
    }

    private fun deliverEFBMessage() {
        if (clientSinks.isNotEmpty()) {
            val message = Json.encodeToString(clientList)
            for (sink in clientSinks) {
                sink.success(message)
            }
        }
    }

    private fun shouldRunGnss(): Boolean {
        return gnssSinks.isNotEmpty() || shouldRunDelivery()
    }

    private fun shouldRunDelivery(): Boolean {
        return clientList.any { client -> client.isEnabled }
    }

    private fun shouldRunLocation(): Boolean {
        return locationSinks.isNotEmpty() || shouldRunGnss() || shouldRunDelivery()
    }

    private fun updateRunningStatus() {
        val shouldRunLocation = shouldRunLocation()
        val shouldRunGnss = shouldRunGnss()
        val shouldRunDelivery = shouldRunDelivery()

        if (shouldRunLocation && !isLocationRunning) {
            listenLocationUpdates()
            isLocationRunning = shouldRunLocation
        } else if (!shouldRunLocation && isLocationRunning) {
            stopLocationUpdates()
            isLocationRunning = shouldRunLocation
        }

        if (shouldRunGnss && !isGnssRunning) {
            listenGnssUpdates()
            isGnssRunning = shouldRunGnss
        } else if (!shouldRunGnss && isGnssRunning) {
            stopGnssUpdates()
            isGnssRunning = shouldRunGnss
        }

        if (shouldRunDelivery && !isForeground) {
            startForeground(NOTIFICATION_ID, produceNotification())
            isForeground = true
        } else if (!shouldRunDelivery && isForeground) {
            stopForeground(true)
            isForeground = false
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            NOTIFICATION_CHANNEL_NAME,
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = NOTIFICATION_CHANNEL_DESC
        }
        notificationManager?.createNotificationChannel(channel)
    }

    private fun produceNotification(): Notification {
        val locationString = if (lastLocation == null) {
            "Unknown Location"
        } else {
            val latBuilder = coordToString(lastLocation!!.latitude)
            latBuilder.append(
                if (lastLocation!!.latitude < 0) {
                    " S"
                } else {
                    " N"
                }
            )
            val lonBuilder = coordToString(lastLocation!!.longitude)
            lonBuilder.append(
                if (lastLocation!!.longitude < 0) {
                    " W"
                } else {
                    " E"
                }
            )
            "($latBuilder, $lonBuilder)"
        }
        val numActiveClient = clientList.count { client -> client.isEnabled }
        val titleString = "Serving $numActiveClient client${
            if (numActiveClient > 1) {
                "s"
            } else {
                ""
            }
        }"

        val intent = Intent(this, LocationService::class.java)
        intent.putExtra("avgps.disable_all", true)

        val launchIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        val pendingIntent =
            PendingIntent.getService(
                this,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

        return NotificationCompat.Builder(this, NOTIFICATION_ID_NAME)
            .setSmallIcon(R.drawable.ic_notification)
            .addAction(0, "Stop", pendingIntent)
            .setContentText(locationString).setContentTitle(titleString).setOngoing(true)
            .setTicker(titleString).setChannelId(NOTIFICATION_CHANNEL_ID)
            .setContentIntent(launchIntent).setOnlyAlertOnce(true)
            .setWhen(System.currentTimeMillis()).build()
    }

    @SuppressLint("MissingPermission")
    private fun listenLocationUpdates() {
        locationManager?.requestLocationUpdates(
            LocationManager.GPS_PROVIDER,
            1000,
            0.0f,
            onLocationUpdate,
            Looper.getMainLooper()
        )
    }

    private fun stopLocationUpdates() {
        locationManager?.removeUpdates(onLocationUpdate)
    }

    @SuppressLint("MissingPermission")
    private fun listenGnssUpdates() {
        locationManager?.registerGnssStatusCallback(onGnssUpdate, Handler(Looper.getMainLooper()))
        locationManager?.addNmeaListener(onNmeaMessageListener, Handler(Looper.getMainLooper()))
    }

    private fun stopGnssUpdates() {
        locationManager?.unregisterGnssStatusCallback(onGnssUpdate)
        locationManager?.removeNmeaListener(onNmeaMessageListener)
    }

    private fun truncateString(s: String, limit: Int): ByteArray {
        val encoder = StandardCharsets.UTF_8.newEncoder()
        val byteBuffer = ByteBuffer.allocate(limit)
        val charBuffer = CharBuffer.wrap(s)
        val result = encoder.encode(charBuffer, byteBuffer, true)
        return if (result.isOverflow) {
            var availableByte = byteBuffer.remaining()
            var position = charBuffer.position() - 1
            while (availableByte < 3) {
                availableByte += s.substring(position, position + 1).toByteArray().size
                position--
            }
            charBuffer.position(position + 1)
            charBuffer.put('⋯')
            charBuffer.flip().toString().toByteArray()
        } else {
            byteBuffer.array()
        }
    }

    private fun coordToString(coordinate: Double): StringBuilder {
        val builder = StringBuilder()
        val rawDeg = Location.convert(coordinate, Location.FORMAT_SECONDS)
        val coordComponents = rawDeg.split(':')

        builder.append(coordComponents[0])
        builder.append('°')
        builder.append(coordComponents[1])
        builder.append('′')
        builder.append(coordComponents[2])
        builder.append('″')

        return builder
    }

    fun addLocationSink(sink: EventChannel.EventSink) {
        locationSinks.add(sink)
        updateRunningStatus()
    }

    fun removeLocationSink(sink: EventChannel.EventSink) {
        locationSinks.remove(sink)
        updateRunningStatus()
    }

    fun addGnssSink(sink: EventChannel.EventSink) {
        gnssSinks.add(sink)
        updateRunningStatus()
    }

    fun removeGnssSink(sink: EventChannel.EventSink) {
        gnssSinks.remove(sink)
        updateRunningStatus()
    }

    fun addClientSink(sink: EventChannel.EventSink) {
        clientSinks.add(sink)
    }

    fun removeClientSink(sink: EventChannel.EventSink) {
        clientSinks.remove(sink)
    }

    private fun makeDiscoveryThread(): Thread {
        return object : Thread() {
            var socket: DatagramSocket? = null

            override fun run() {
                // Assume typical MTU of 1500
                val rawPacket = ByteArray(1500)
                val handler = Handler(Looper.getMainLooper())
                socket = DatagramSocket(null)
                socket!!.reuseAddress = true
                socket!!.bind(InetSocketAddress(63093))

                while (true) {
                    try {
                        val packet = DatagramPacket(rawPacket, rawPacket.size)
                        socket!!.receive(packet)
                        val message = String(rawPacket, 0, packet.length)
                        val data: EFBDiscoveryMessage = Json.decodeFromString(message)

                        handler.post {
                            this@LocationService.onEFBDiscovery(packet.address, data)
                        }
                    } catch (e: SocketException) {
                        // due to close()
                        break
                    } catch (e: SerializationException) {
                        // Do nothing
                    } catch (e: Exception) {
                        Log.e(TAG, "UDP listener: $e")
                        break
                    }
                }
            }

            override fun interrupt() {
                super.interrupt()
                socket?.close()
            }
        }
    }
}
