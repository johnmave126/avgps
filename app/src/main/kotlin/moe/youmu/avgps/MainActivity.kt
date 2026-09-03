package moe.youmu.avgps

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.core.net.toUri
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle

class MainActivity : ComponentActivity() {
    private val viewModel: AvGpsViewModel by viewModels()
    private var missingAccess by mutableStateOf<List<RequiredAccess>>(emptyList())
    private var showPermissionExplanation by mutableStateOf(false)
    private var initialPromptShown = false
    private val attemptedAccess = mutableSetOf<RequiredAccess>()

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { continuePermissionRequests() }

    private val backgroundPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { continuePermissionRequests() }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { continuePermissionRequests() }

    private val specialAccessLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { continuePermissionRequests() }

    private val settingsLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { refreshAccessState() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initialPromptShown = savedInstanceState?.getBoolean(PROMPT_SHOWN_KEY) ?: false
        refreshAccessState()
        setContent {
            val appState by viewModel.appState.collectAsStateWithLifecycle()
            val unitSettings by viewModel.unitSettings.collectAsStateWithLifecycle()
            AvGpsApp(
                appState = appState,
                unitSettings = unitSettings,
                missingAccess = missingAccess,
                showPermissionExplanation = showPermissionExplanation,
                onPermissionExplanationConfirmed = {
                    showPermissionExplanation = false
                    attemptedAccess.clear()
                    continuePermissionRequests()
                },
                onPermissionExplanationDismissed = { showPermissionExplanation = false },
                onOpenAccessSettings = { access -> settingsLauncher.launch(settingsIntent(access)) },
                onClearClients = viewModel::clearClients,
                onSetClientEnabled = viewModel::setClientEnabled,
                onSetAltitudeUnit = viewModel::setAltitudeUnit,
                onSetSpeedUnit = viewModel::setSpeedUnit,
                onSetDistanceUnit = viewModel::setDistanceUnit,
                onSetPressureUnit = viewModel::setPressureUnit,
                onSetReportPressureAltitude = viewModel::setReportPressureAltitude,
                onSetAltimeterSetting = viewModel::setAltimeterSettingHpa,
                onOpenSource = {
                    startActivity(Intent(Intent.ACTION_VIEW, SOURCE_URL.toUri()))
                },
            )
        }
    }

    override fun onStart() {
        super.onStart()
        viewModel.setMonitoring(true)
    }

    override fun onResume() {
        super.onResume()
        refreshAccessState()
        if (!initialPromptShown && missingAccess.any { it != RequiredAccess.LOCATION_SERVICES }) {
            initialPromptShown = true
            showPermissionExplanation = true
        }
    }

    override fun onStop() {
        viewModel.setMonitoring(false)
        super.onStop()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean(PROMPT_SHOWN_KEY, initialPromptShown)
        super.onSaveInstanceState(outState)
    }

    private fun refreshAccessState() {
        missingAccess = missingAccess()
        viewModel.refreshMonitoring()
    }

    @SuppressLint("BatteryLife")
    private fun continuePermissionRequests() {
        refreshAccessState()
        when {
            requestNext(RequiredAccess.LOCATION) -> locationPermissionLauncher.launch(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
            )

            RequiredAccess.LOCATION !in missingAccess &&
                    requestNext(RequiredAccess.BACKGROUND_LOCATION) &&
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ->
                backgroundPermissionLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)

            requestNext(RequiredAccess.NOTIFICATIONS) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ->
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)

            requestNext(RequiredAccess.BATTERY_OPTIMIZATION) -> {
                // Background GPS and UDP broadcast telemetry requires disabling battery optimization during flight
                val request = Intent(
                    Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    "package:$packageName".toUri(),
                )
                specialAccessLauncher.launch(request)
            }

            else -> refreshAccessState()
        }
    }

    private fun requestNext(access: RequiredAccess): Boolean {
        if (access !in missingAccess || access in attemptedAccess) return false
        attemptedAccess.add(access)
        return true
    }

    companion object {
        private const val PROMPT_SHOWN_KEY = "permission_prompt_shown"
        private const val SOURCE_URL = "https://github.com/johnmave126/avGPS"
    }
}
