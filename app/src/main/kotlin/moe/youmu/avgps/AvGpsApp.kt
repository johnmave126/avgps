package moe.youmu.avgps

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.SatelliteAlt
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AvGpsApp(
    appState: AvGpsState,
    unitSettings: UnitSettings,
    missingAccess: List<RequiredAccess>,
    showPermissionExplanation: Boolean,
    onPermissionExplanationConfirmed: () -> Unit,
    onPermissionExplanationDismissed: () -> Unit,
    onOpenAccessSettings: (RequiredAccess) -> Unit,
    onClearClients: () -> Unit,
    onSetClientEnabled: (Client, Boolean) -> Unit,
    onSetAltitudeUnit: (AltitudeUnit) -> Unit,
    onSetSpeedUnit: (SpeedUnit) -> Unit,
    onSetDistanceUnit: (DistanceUnit) -> Unit,
    onOpenSource: () -> Unit,
    modifier: Modifier = Modifier,
    onSetPressureUnit: (PressureUnit) -> Unit = {},
    onSetReportPressureAltitude: (Boolean) -> Unit = {},
    onSetAltimeterSetting: (Double) -> Unit = {},
    onSetCallsign: (String) -> Unit = {},
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var currentClockTime by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(Unit) {
        while (true) {
            currentClockTime = System.currentTimeMillis()
            delay(1000.milliseconds)
        }
    }

    AvGpsTheme {
        Scaffold(
            modifier = modifier.fillMaxSize(),
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            "Ownship",
                            fontWeight = FontWeight.SemiBold,
                        )
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                )
            },
            bottomBar = {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                ) {
                    val tabs = listOf(
                        Triple("Location", Icons.Default.Place, 0),
                        Triple("Satellites", Icons.Default.SatelliteAlt, 1),
                        Triple("Settings", Icons.Default.Settings, 2),
                    )
                    tabs.forEach { (label, icon, index) ->
                        NavigationBarItem(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            icon = { Icon(icon, contentDescription = label) },
                            label = { Text(label) },
                        )
                    }
                }
            },
            containerColor = MaterialTheme.colorScheme.background,
        ) { padding ->
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                color = MaterialTheme.colorScheme.background,
            ) {
                when (selectedTab) {
                    0 -> LocationScreen(
                        state = appState,
                        settings = unitSettings,
                        nowEpochMs = currentClockTime,
                        onClearClients = onClearClients,
                        onSetClientEnabled = onSetClientEnabled,
                        onSetAltimeterSetting = onSetAltimeterSetting,
                        onSetCallsign = onSetCallsign,
                    )

                    1 -> SatellitesScreen(satellites = appState.satellites)
                    else -> SettingsScreen(
                        settings = unitSettings,
                        hasBarometer = appState.hasBarometer,
                        reportPressureAltitude = appState.reportPressureAltitude,
                        missingAccess = missingAccess,
                        onOpenAccessSettings = onOpenAccessSettings,
                        onSetAltitudeUnit = onSetAltitudeUnit,
                        onSetSpeedUnit = onSetSpeedUnit,
                        onSetDistanceUnit = onSetDistanceUnit,
                        onSetPressureUnit = onSetPressureUnit,
                        onSetReportPressureAltitude = onSetReportPressureAltitude,
                        onOpenSource = onOpenSource,
                    )
                }
            }
        }

        if (showPermissionExplanation) {
            PermissionExplanationDialog(
                missingAccess = missingAccess.filter { it != RequiredAccess.LOCATION_SERVICES },
                onConfirm = onPermissionExplanationConfirmed,
                onDismiss = onPermissionExplanationDismissed,
            )
        }
    }
}

@Composable
private fun PermissionExplanationDialog(
    missingAccess: List<RequiredAccess>,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Permissions needed for reliable GPS sharing") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Ownship will request the following access. You can decline and enable it later from Settings.")
                missingAccess.forEach { access ->
                    Column {
                        Text(
                            access.title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(access.explanation, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Continue") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Not now") } },
    )
}
