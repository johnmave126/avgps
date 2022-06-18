package moe.youmu.avgps

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.StrictMode
import android.provider.Settings
import android.util.Log
import androidx.annotation.NonNull
import androidx.core.content.ContextCompat
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.EventChannel
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.net.InetSocketAddress

class MainActivity : FlutterActivity() {
    private val METHOD_CHANNEL = "avgps.youmu.moe/rpc"
    private val LOCATION_CHANNEL = "avgps.youmu.moe/location"
    private val GNSS_CHANNEL = "avgps.youmu.moe/gnss"
    private val CLIENT_CHANNEL = "avgps.youmu.moe/client"

    private var permissionResult: MethodChannel.Result? = null
    private val LOCATION_PERMISSION_CODE = 0
    private val BACKGROUND_LOCATION_PERMISSION_CODE = 1

    var locationService: LocationService? = null
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as LocationService.LocalBinder
            locationService = binder.getService()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            locationService = null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startService(Intent(this, LocationService::class.java))
    }

    override fun onStart() {
        super.onStart()
        bindService(
            Intent(this, LocationService::class.java),
            serviceConnection,
            Context.BIND_AUTO_CREATE
        )
    }

    override fun onStop() {
        super.onStop()

        if (locationService != null) {
            unbindService(serviceConnection)
        }
    }

    override fun configureFlutterEngine(@NonNull flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)

        MethodChannel(
            flutterEngine.dartExecutor.binaryMessenger,
            METHOD_CHANNEL
        ).setMethodCallHandler { call, result ->
            when (call.method) {
                "getLocation" ->
                    result.success(Json.encodeToString(locationService?.lastLocation))
                "getClients" ->
                    result.success(Json.encodeToString(locationService?.clientList ?: listOf()))
                "clearClients" -> {
                    locationService?.clearEFB()
                    result.success(null)
                }
                "getPermissions" -> {
                    // Check permission in the following order
                    // LocationManager present and location enabled?
                    // GPS provider enabled?
                    // Having ACCESS_FINE_LOCATION permission?
                    // Having ACCESS_BACKGROUND_LOCATION permission?
                    val locationManager =
                        getSystemService(Context.LOCATION_SERVICE) as LocationManager?
                    if (locationManager == null) {
                        result.success(LOCATION_UNAVAILABLE)
                    } else {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                            if (!locationManager.isLocationEnabled) {
                                result.success(LOCATION_UNAVAILABLE)
                                return@setMethodCallHandler
                            }
                        } else {
                            try {
                                val locationMode = Settings.Secure.getInt(
                                    contentResolver,
                                    Settings.Secure.LOCATION_MODE
                                )
                                if (locationMode == Settings.Secure.LOCATION_MODE_OFF) {
                                    result.success(LOCATION_UNAVAILABLE)
                                    return@setMethodCallHandler
                                }
                            } catch (e: Settings.SettingNotFoundException) {
                                result.success(LOCATION_UNAVAILABLE)
                                return@setMethodCallHandler
                            }
                        }
                        if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                            result.success(LOCATION_UNAVAILABLE)
                            return@setMethodCallHandler
                        }
                        if (ContextCompat.checkSelfPermission(
                                this,
                                Manifest.permission.ACCESS_FINE_LOCATION
                            ) != PackageManager.PERMISSION_GRANTED
                        ) {
                            result.success(LOCATION_DENIED)
                        } else if (ContextCompat.checkSelfPermission(
                                this,
                                Manifest.permission.ACCESS_BACKGROUND_LOCATION
                            ) != PackageManager.PERMISSION_GRANTED
                        ) {
                            result.success(LOCATION_BACKGROUND_DENIED)
                        } else {
                            result.success(GRANTED)
                        }
                    }
                }
                "openLocationSettings" -> {
                    startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                    result.success(null)
                }
                "requestLocationPermission" -> {
                    permissionResult = result
                    requestPermissions(
                        arrayOf(
                            Manifest.permission.ACCESS_COARSE_LOCATION,
                            Manifest.permission.ACCESS_FINE_LOCATION
                        ), LOCATION_PERMISSION_CODE
                    )
                    result.success(null)
                }
                "requestLocationBackgroundPermission" -> {
                    permissionResult = result
                    requestPermissions(
                        arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION),
                        BACKGROUND_LOCATION_PERMISSION_CODE
                    )
                    result.success(null)
                }
                "setEFBEnabled" -> {
                    val host =
                        Json.decodeFromString(
                            InetSocketAddressSerializer,
                            call.argument<String>("client")!!
                        )
                    val enabled = call.argument<Boolean>("enabled")
                    locationService?.setEFBenable(host, enabled!!)
                    result.success(null)
                }
            }
        }

        EventChannel(flutterEngine.dartExecutor.binaryMessenger, LOCATION_CHANNEL)
            .setStreamHandler(LocationStreamHandler(this))

        EventChannel(flutterEngine.dartExecutor.binaryMessenger, GNSS_CHANNEL)
            .setStreamHandler(GnssStreamHandler(this))

        EventChannel(flutterEngine.dartExecutor.binaryMessenger, CLIENT_CHANNEL)
            .setStreamHandler(ClientStreamHandler(this))
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        when (requestCode) {
            LOCATION_PERMISSION_CODE -> {
                if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                    if (ContextCompat.checkSelfPermission(
                            this,
                            Manifest.permission.ACCESS_BACKGROUND_LOCATION
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        permissionResult?.success(LOCATION_BACKGROUND_DENIED)
                    } else {
                        permissionResult?.success(GRANTED)
                    }
                } else {
                    permissionResult?.success(LOCATION_DENIED)
                }
            }
            BACKGROUND_LOCATION_PERMISSION_CODE -> {
                if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                    permissionResult?.success(GRANTED)
                } else {
                    permissionResult?.success(LOCATION_BACKGROUND_DENIED)
                }
            }
            else -> {}
        }
    }
}
