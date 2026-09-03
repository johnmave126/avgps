package moe.youmu.avgps

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.util.Locale

import androidx.compose.material3.Switch

private enum class UnitPickerType {
    ALTITUDE,
    SPEED,
    DISTANCE,
    PRESSURE,
}

private const val LEGALESE =
    "All information in Ownship is provided \"as is\", with no guarantee of completeness, " +
            "accuracy, timeliness or of the results obtained from the use of this information, " +
            "and without warranty of any kind, express or implied, including, but not limited to " +
            "warranties of performance, merchantability and fitness for a particular purpose.\n\n" +
            "The author will not be liable to You or anyone else for any decision made or action " +
            "taken in reliance on the information given by Ownship or for any consequential, special " +
            "or similar damages, even if advised of the possibility of such damages."

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: UnitSettings,
    onSetAltitudeUnit: (AltitudeUnit) -> Unit,
    onSetSpeedUnit: (SpeedUnit) -> Unit,
    onSetDistanceUnit: (DistanceUnit) -> Unit,
    onSetPressureUnit: (PressureUnit) -> Unit,
    onSetReportPressureAltitude: (Boolean) -> Unit,
    onOpenSource: () -> Unit,
    modifier: Modifier = Modifier,
    hasBarometer: Boolean = false,
    reportPressureAltitude: Boolean = false,
    missingAccess: List<RequiredAccess> = emptyList(),
    onOpenAccessSettings: (RequiredAccess) -> Unit = {},
) {
    var activeBottomSheet by remember { mutableStateOf<UnitPickerType?>(null) }
    var showAboutDialog by remember { mutableStateOf(false) }
    var showCabinWarningDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface),
    ) {
        // --- Group: Unit ---
        item {
            Text(
                "Unit",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 24.dp, top = 24.dp, bottom = 8.dp),
            )
        }

        item {
            ListItem(
                headlineContent = { Text("Altitude") },
                supportingContent = { Text(settings.altitude.label.lowercase(Locale.US)) },
                modifier = Modifier.clickable { activeBottomSheet = UnitPickerType.ALTITUDE },
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
            )
        }

        item {
            ListItem(
                headlineContent = { Text("Speed") },
                supportingContent = { Text(settings.speed.label) },
                modifier = Modifier.clickable { activeBottomSheet = UnitPickerType.SPEED },
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
            )
        }

        item {
            ListItem(
                headlineContent = { Text("Horizontal Accuracy") },
                supportingContent = { Text(settings.distance.label.lowercase(Locale.US)) },
                modifier = Modifier.clickable { activeBottomSheet = UnitPickerType.DISTANCE },
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
            )
        }

        item {
            ListItem(
                headlineContent = { Text("Pressure") },
                supportingContent = { Text(settings.pressure.label) },
                modifier = Modifier.clickable { activeBottomSheet = UnitPickerType.PRESSURE },
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
            )
        }

        item { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) }

        // --- Group: Barometer ---
        item {
            Text(
                "Barometer",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 24.dp, top = 8.dp, bottom = 8.dp),
            )
        }

        item {
            val isChecked = hasBarometer && reportPressureAltitude
            ListItem(
                headlineContent = { Text("Report pressure altitude") },
                supportingContent = {
                    Text(
                        if (hasBarometer) {
                            "Relay barometric pressure altitude to connected EFBs"
                        } else {
                            "Barometer sensor is not supported on this device"
                        }
                    )
                },
                trailingContent = {
                    Switch(
                        checked = isChecked,
                        onCheckedChange = { checked ->
                            if (checked) {
                                showCabinWarningDialog = true
                            } else {
                                onSetReportPressureAltitude(false)
                            }
                        },
                        enabled = hasBarometer,
                    )
                },
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
            )
        }

        item { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) }

        // --- Group: Permissions and System Access (if missing) ---
        if (missingAccess.isNotEmpty()) {
            item {
                Text(
                    "Permissions and system access",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(start = 24.dp, top = 8.dp, bottom = 8.dp),
                )
            }
            items(missingAccess, key = { it.name }) { access ->
                ListItem(
                    headlineContent = { Text(access.title, fontWeight = FontWeight.SemiBold) },
                    supportingContent = { Text(access.explanation) },
                    trailingContent = {
                        TextButton(onClick = { onOpenAccessSettings(access) }) {
                            Text("Open")
                        }
                    },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
                )
            }
            item { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) }
        }

        // --- Group: About ---
        item {
            Text(
                "About",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 24.dp, top = 8.dp, bottom = 8.dp),
            )
        }

        item {
            ListItem(
                headlineContent = { Text("About") },
                modifier = Modifier.clickable { showAboutDialog = true },
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
            )
        }

        item {
            ListItem(
                headlineContent = { Text("Source Code") },
                modifier = Modifier.clickable { onOpenSource() },
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
            )
        }
    }

    // Modal Bottom Sheet Unit Picker
    activeBottomSheet?.let { pickerType ->
        val sheetState = rememberModalBottomSheetState()
        ModalBottomSheet(
            onDismissRequest = { activeBottomSheet = null },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            Column(modifier = Modifier.padding(bottom = 32.dp)) {
                Text(
                    text = when (pickerType) {
                        UnitPickerType.ALTITUDE -> "Altitude"
                        UnitPickerType.SPEED -> "Speed"
                        UnitPickerType.DISTANCE -> "Horizontal Accuracy"
                        UnitPickerType.PRESSURE -> "Pressure"
                    },
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                )

                when (pickerType) {
                    UnitPickerType.ALTITUDE -> {
                        AltitudeUnit.entries.forEach { option ->
                            UnitOptionRow(
                                title = option.label.lowercase(Locale.US),
                                isSelected = settings.altitude == option,
                                onSelect = {
                                    onSetAltitudeUnit(option)
                                    activeBottomSheet = null
                                },
                            )
                        }
                    }

                    UnitPickerType.SPEED -> {
                        SpeedUnit.entries.forEach { option ->
                            UnitOptionRow(
                                title = option.label,
                                isSelected = settings.speed == option,
                                onSelect = {
                                    onSetSpeedUnit(option)
                                    activeBottomSheet = null
                                },
                            )
                        }
                    }

                    UnitPickerType.DISTANCE -> {
                        DistanceUnit.entries.forEach { option ->
                            UnitOptionRow(
                                title = option.label.lowercase(Locale.US),
                                isSelected = settings.distance == option,
                                onSelect = {
                                    onSetDistanceUnit(option)
                                    activeBottomSheet = null
                                },
                            )
                        }
                    }

                    UnitPickerType.PRESSURE -> {
                        PressureUnit.entries.forEach { option ->
                            UnitOptionRow(
                                title = option.label,
                                isSelected = settings.pressure == option,
                                onSelect = {
                                    onSetPressureUnit(option)
                                    activeBottomSheet = null
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    if (showCabinWarningDialog) {
        AlertDialog(
            onDismissRequest = { showCabinWarningDialog = false },
            title = { Text("Pressurized Cabin Warning") },
            text = {
                Text(
                    "Pressure altitude will be relayed to connected EFBs.\n\n" +
                            "This must NEVER be enabled in a pressurized cabin, as cabin pressure does not reflect the aircraft's actual altitude."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showCabinWarningDialog = false
                    onSetReportPressureAltitude(true)
                }) {
                    Text("Enable")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCabinWarningDialog = false }) {
                    Text("Cancel")
                }
            },
        )
    }

    if (showAboutDialog) {
        AlertDialog(
            onDismissRequest = { showAboutDialog = false },
            title = { Text("Ownship ${BuildConfig.VERSION_NAME}") },
            text = {
                Text(
                    LEGALESE,
                    style = MaterialTheme.typography.bodySmall,
                )
            },
            confirmButton = {
                TextButton(onClick = { showAboutDialog = false }) {
                    Text("OK")
                }
            },
        )
    }
}

@Composable
private fun UnitOptionRow(
    title: String,
    isSelected: Boolean,
    onSelect: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect() }
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        RadioButton(
            selected = isSelected,
            onClick = onSelect,
        )
    }
}
