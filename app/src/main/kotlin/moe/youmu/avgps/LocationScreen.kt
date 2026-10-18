package moe.youmu.avgps

import android.hardware.SensorManager
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import java.net.InetSocketAddress
import java.util.Locale
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun LocationScreen(
    state: AvGpsState,
    settings: UnitSettings,
    nowEpochMs: Long,
    onClearClients: () -> Unit,
    onSetClientEnabled: (Client, Boolean) -> Unit,
    modifier: Modifier = Modifier,
    onSetAltimeterSetting: (Double) -> Unit = {},
    onSetCallsign: (String) -> Unit = {},
    onAddClient: (String, String, Int) -> Unit = { _, _, _ -> },
    onUpdateClient: (InetSocketAddress, String, String, Int) -> Unit = { _, _, _, _ -> },
    onRemoveClient: (Client) -> Unit = {},
) {
    val location = state.location
    val hasLocation = location != null
    var showAltimeterDialog by remember { mutableStateOf(false) }
    var showCallsignDialog by remember { mutableStateOf(false) }
    var showClientDialog by remember { mutableStateOf(false) }
    var editingClient by remember { mutableStateOf<Client?>(null) }

    val lastFixString = if (hasLocation) {
        val elapsedSec = ((nowEpochMs - location.timestamp) / 1000).coerceAtLeast(0)
        CoordinateFormatter.formatTimeSince(elapsedSec)
    } else {
        "Never"
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // --- Card 1: GPS Position & Flight Telemetry ---
        item {
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    // Coordinates Section
                    Column(horizontalAlignment = Alignment.Start) {
                        Text(
                            "coordinates",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (hasLocation) {
                            Text(
                                CoordinateFormatter.formatLatitudeDms(location.latitude),
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Normal,
                            )
                            Text(
                                CoordinateFormatter.formatLongitudeDms(location.longitude),
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Normal,
                            )
                        } else {
                            Text(
                                "N/A",
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Normal,
                            )
                        }
                    }

                    // 3 Columns x 2 Rows Metrics Grid
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        // Col 1: Altitude, Horizontal Accuracy, Pressure Altitude
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            MetricBox(
                                label = "altitude",
                                value = if (hasLocation) Altitude.ofMeters(location.altitude)
                                    .format(settings.altitude) else "N/A",
                            )
                            MetricBox(
                                label = "horizontal accuracy",
                                value = if (hasLocation) Distance.ofMeters(location.horizontalAccuracy.toDouble())
                                    .format(settings.distance) else "N/A",
                            )
                            if (state.hasBarometer) {
                                val altString = if (state.pressureHpa != null) {
                                    val meters = SensorManager.getAltitude(
                                        state.altimeterSettingHpa.toFloat(),
                                        state.pressureHpa,
                                    )
                                    Altitude.ofMeters(meters.toDouble()).format(settings.altitude)
                                } else {
                                    "N/A"
                                }
                                MetricBox(
                                    label = "pressure altitude",
                                    value = altString,
                                )
                            }
                        }

                        // Col 2: Speed, Vertical Accuracy, Altimeter Setting
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            MetricBox(
                                label = "speed",
                                value = if (hasLocation) Speed.ofMps(location.speed.toDouble())
                                    .format(settings.speed) else "N/A",
                            )
                            MetricBox(
                                label = "vertical accuracy",
                                value = if (hasLocation) Altitude.ofMeters(location.verticalAccuracy.toDouble())
                                    .format(settings.altitude) else "N/A",
                            )
                            if (state.hasBarometer) {
                                val altimeterDisplay = Pressure.ofHpa(state.altimeterSettingHpa).format(settings.pressure)
                                MetricBox(
                                    label = "altimeter",
                                    value = altimeterDisplay,
                                    onClick = { showAltimeterDialog = true },
                                )
                            }
                        }

                        // Col 3: Bearing, Last Fix, Callsign
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            MetricBox(
                                label = "bearing",
                                value = if (hasLocation) "${location.bearing.roundToInt()}°" else "N/A",
                            )
                            MetricBox(
                                label = "last fix",
                                value = lastFixString,
                            )
                            MetricBox(
                                label = "callsign",
                                value = state.callsign.ifBlank { "N/A" },
                                onClick = { showCallsignDialog = true },
                            )
                        }
                    }
                }
            }
        }

        // --- Card 2: EFB Clients List ---
        item {
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // Small yellow note section if barometer reporting is on
                    if (state.hasBarometer && state.reportPressureAltitude) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp),
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFFFFF9C4), // Yellow 100
                            border = BorderStroke(1.dp, Color(0xFFFBC02D)), // Amber 700
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = Color(0xFFF57F17),
                                    modifier = Modifier.size(20.dp),
                                )
                                Text(
                                    text = "Pressure altitude is being relayed to EFBs. Do not use in a pressurized cabin.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFF5D4037),
                                    fontWeight = FontWeight.Medium,
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextButton(
                            onClick = onClearClients,
                            enabled = state.clients.isNotEmpty(),
                        ) {
                            Text("Clear")
                        }
                        TextButton(
                            onClick = {
                                editingClient = null
                                showClientDialog = true
                            },
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Add")
                        }
                    }

                    if (state.clients.isEmpty()) {
                        Text(
                            "No EFBs discovered yet on local network",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 16.dp),
                        )
                    } else {
                        state.clients.forEach { client ->
                            val elapsedSec = ((nowEpochMs - client.lastDiscover.toEpochMilli()) / 1000).coerceAtLeast(0)
                            val lastSeenText = CoordinateFormatter.formatTimeSince(elapsedSec)

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .then(
                                        if (client.isManual) {
                                            Modifier.clickable {
                                                editingClient = client
                                                showClientDialog = true
                                            }
                                        } else Modifier
                                    )
                                    .padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        val title = if (!client.deviceName.isNullOrBlank()) {
                                            "${client.deviceName} (${client.efb})"
                                        } else {
                                            client.efb
                                        }
                                        Text(
                                            title,
                                            style = MaterialTheme.typography.titleMedium,
                                            modifier = Modifier.alignByBaseline(),
                                        )
                                        if (!client.version.isNullOrBlank()) {
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                "v${client.version}",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.alignByBaseline(),
                                            )
                                        }
                                        if (client.isManual) {
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Icon(
                                                imageVector = Icons.Default.Edit,
                                                contentDescription = "Edit manual client",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(14.dp),
                                            )
                                        }
                                    }
                                    Text(
                                        "${client.source.hostString}:${client.source.port} last seen: $lastSeenText",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(
                                        onClick = { onRemoveClient(client) },
                                        modifier = Modifier.size(36.dp),
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.DeleteOutline,
                                            contentDescription = "Remove client",
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(20.dp),
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Switch(
                                        checked = client.isEnabled,
                                        onCheckedChange = { onSetClientEnabled(client, it) },
                                    )
                                }
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        }
                    }
                }
            }
        }
    }

    if (showAltimeterDialog) {
        AltimeterDialog(
            currentHpa = state.altimeterSettingHpa,
            unit = settings.pressure,
            onDismiss = { showAltimeterDialog = false },
            onConfirm = { newHpa ->
                onSetAltimeterSetting(newHpa)
                showAltimeterDialog = false
            },
        )
    }

    if (showCallsignDialog) {
        CallsignDialog(
            initialCallsign = state.callsign,
            onDismiss = { showCallsignDialog = false },
            onConfirm = { newCallsign ->
                onSetCallsign(newCallsign)
                showCallsignDialog = false
            },
        )
    }

    if (showClientDialog) {
        ClientDialog(
            client = editingClient,
            onDismiss = {
                showClientDialog = false
                editingClient = null
            },
            onSave = { name, ip, port ->
                val target = editingClient
                if (target != null) {
                    onUpdateClient(target.source, name, ip, port)
                } else {
                    onAddClient(name, ip, port)
                }
                showClientDialog = false
                editingClient = null
            },
        )
    }
}

@Composable
private fun MetricBox(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val isClickable = onClick != null
    Column(
        modifier = modifier
            .then(
                if (isClickable) {
                    Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .clickable(onClick = onClick)
                } else {
                    Modifier
                }
            ),
        horizontalAlignment = Alignment.Start,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (isClickable) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = "Edit $label",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(12.dp),
                )
            }
        }
        Text(
            value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Normal,
            color = if (isClickable) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun DigitBox(
    char: Char?,
    isFocusedSlot: Boolean,
    modifier: Modifier = Modifier,
) {
    val borderColor = when {
        isFocusedSlot -> MaterialTheme.colorScheme.primary
        char != null -> MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
        else -> MaterialTheme.colorScheme.outlineVariant
    }
    val borderWidth = if (isFocusedSlot) 2.dp else 1.dp
    val containerColor = if (isFocusedSlot) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
    }

    Surface(
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(borderWidth, borderColor),
        color = containerColor,
        modifier = modifier
            .width(42.dp)
            .height(52.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (char != null) {
                Text(
                    text = char.toString(),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@Composable
private fun AltimeterDialog(
    currentHpa: Double,
    unit: PressureUnit,
    onDismiss: () -> Unit,
    onConfirm: (Double) -> Unit,
) {
    val initialDigits = remember(currentHpa, unit) {
        if (unit == PressureUnit.INHG) {
            val inHg = Pressure.ofHpa(currentHpa).toInHg()
            "%.2f".format(Locale.US, inHg).replace(".", "")
        } else {
            "%.0f".format(Locale.US, currentHpa)
        }
    }

    var textValue by remember {
        mutableStateOf(TextFieldValue(initialDigits, selection = TextRange(initialDigits.length)))
    }
    var hasStartedEditing by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        delay(100.milliseconds)
        focusRequester.requestFocus()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Set Altimeter") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = "Enter altimeter setting in ${unit.label}:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                val digits = textValue.text
                val activeSlot = if (!hasStartedEditing) 0 else digits.length.coerceAtMost(3)

                BasicTextField(
                    value = textValue,
                    onValueChange = { newValue ->
                        errorMessage = null
                        if (!hasStartedEditing) {
                            hasStartedEditing = true
                            val newChar = newValue.text.lastOrNull { it.isDigit() }
                            textValue = if (newChar != null) {
                                TextFieldValue(newChar.toString(), selection = TextRange(1))
                            } else {
                                TextFieldValue("", selection = TextRange(0))
                            }
                        } else {
                            if (newValue.text.length < textValue.text.length) {
                                // Backspace pressed
                                val filtered = newValue.text.filter { it.isDigit() }
                                textValue = TextFieldValue(filtered, selection = TextRange(filtered.length))
                            } else {
                                val newDigits = newValue.text.filter { it.isDigit() }
                                val result = if (textValue.text.length >= 4 && newDigits.length > 4) {
                                    newDigits.takeLast(4)
                                } else {
                                    newDigits.take(4)
                                }
                                textValue = TextFieldValue(result, selection = TextRange(result.length))
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            validateAndConfirm(digits, unit, { errorMessage = it }, onConfirm)
                        }
                    ),
                    cursorBrush = SolidColor(Color.Transparent),
                    textStyle = TextStyle(color = Color.Transparent),
                    decorationBox = {
                        if (unit == PressureUnit.INHG) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                DigitBox(
                                    char = digits.getOrNull(0),
                                    isFocusedSlot = activeSlot == 0,
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                DigitBox(
                                    char = digits.getOrNull(1),
                                    isFocusedSlot = activeSlot == 1,
                                )

                                Text(
                                    text = ".",
                                    style = MaterialTheme.typography.displaySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 6.dp),
                                )

                                DigitBox(
                                    char = digits.getOrNull(2),
                                    isFocusedSlot = activeSlot == 2,
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                DigitBox(
                                    char = digits.getOrNull(3),
                                    isFocusedSlot = activeSlot == 3,
                                )

                                Text(
                                    text = "inHg",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(start = 10.dp),
                                )
                            }
                        } else {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                DigitBox(
                                    char = digits.getOrNull(0),
                                    isFocusedSlot = activeSlot == 0,
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                DigitBox(
                                    char = digits.getOrNull(1),
                                    isFocusedSlot = activeSlot == 1,
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                DigitBox(
                                    char = digits.getOrNull(2),
                                    isFocusedSlot = activeSlot == 2,
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                DigitBox(
                                    char = digits.getOrNull(3),
                                    isFocusedSlot = activeSlot == 3,
                                )

                                Text(
                                    text = "hPa",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(start = 10.dp),
                                )
                            }
                        }
                    }
                )

                if (errorMessage != null) {
                    Text(
                        text = errorMessage!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val stdLabel = if (unit == PressureUnit.INHG) "Standard (29.92)" else "Standard (1013)"
                    TextButton(
                        onClick = {
                            hasStartedEditing = false
                            errorMessage = null
                            val stdDigits = if (unit == PressureUnit.INHG) "2992" else "1013"
                            textValue = TextFieldValue(stdDigits, selection = TextRange(stdDigits.length))
                            focusRequester.requestFocus()
                        }
                    ) {
                        Text(stdLabel)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    validateAndConfirm(textValue.text, unit, { errorMessage = it }, onConfirm)
                }
            ) {
                Text("Set")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

private fun validateAndConfirm(
    digits: String,
    unit: PressureUnit,
    onError: (String) -> Unit,
    onConfirm: (Double) -> Unit,
) {
    if (unit == PressureUnit.INHG) {
        if (digits.length < 3) {
            onError("Please enter at least 3 digits (e.g. 29.92)")
            return
        }
        val formatted = if (digits.length == 3) {
            digits.substring(0, 2) + "." + digits.substring(2) + "0"
        } else {
            digits.substring(0, 2) + "." + digits.substring(2, 4)
        }
        val inHg = formatted.toDoubleOrNull()
        if (inHg == null || inHg < 20.0 || inHg > 35.0) {
            onError("Value must be between 20.00 and 35.00 inHg")
            return
        }
        onConfirm(Pressure.ofInHg(inHg).toHpa())
    } else {
        if (digits.length < 3) {
            onError("Please enter at least 3 digits (e.g. 1013)")
            return
        }
        val hpa = digits.toDoubleOrNull()
        if (hpa == null || hpa < 600.0 || hpa > 1200.0) {
            onError("Value must be between 600 and 1200 hPa")
            return
        }
        onConfirm(hpa)
    }
}

@Composable
private fun CallsignDialog(
    initialCallsign: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var text by remember { mutableStateOf(initialCallsign) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        delay(100.milliseconds)
        focusRequester.requestFocus()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Set Callsign") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Enter tail number or flight ID to transmit to connected EFBs (max 8 characters):",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = { input ->
                        text = input.uppercase().filter { it.isLetterOrDigit() || it == '-' }.take(8)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                    singleLine = true,
                    placeholder = { Text("e.g. N12345") },
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Characters,
                        keyboardType = KeyboardType.Ascii,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            onConfirm(text.trim())
                        }
                    ),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text.trim()) }) {
                Text("Save")
            }
        },
        dismissButton = {
            Row {
                if (initialCallsign.isNotBlank()) {
                    TextButton(onClick = { onConfirm("") }) {
                        Text("Clear")
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        },
    )
}

@Composable
private fun ClientDialog(
    client: Client?,
    onDismiss: () -> Unit,
    onSave: (name: String, ip: String, port: Int) -> Unit,
) {
    val isEditing = client != null
    var nameText by remember { mutableStateOf(client?.efb?.takeUnless { it.startsWith("Manual (") } ?: "") }
    var ipText by remember { mutableStateOf(client?.source?.hostString ?: "") }
    var portText by remember { mutableStateOf(client?.source?.port?.toString() ?: "4000") }

    val focusRequester = remember { FocusRequester() }

    val trimmedIp = ipText.trim()
    val portInt = portText.trim().toIntOrNull()
    val isValidPort = portInt != null && portInt in 1..65535
    val isValidIp = trimmedIp.isNotEmpty() && !trimmedIp.contains(" ")
    val canSave = isValidIp && isValidPort

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(if (isEditing) "Edit EFB Client" else "Add EFB Client")
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = nameText,
                    onValueChange = { nameText = it },
                    label = { Text("Name / Label (optional)") },
                    placeholder = { Text("e.g. iPad ForeFlight") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Words,
                        imeAction = ImeAction.Next,
                    ),
                )
                OutlinedTextField(
                    value = ipText,
                    onValueChange = { ipText = it },
                    label = { Text("IP Address or Hostname") },
                    placeholder = { Text("e.g. 192.168.1.50") },
                    singleLine = true,
                    isError = ipText.isNotEmpty() && !isValidIp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Next,
                    ),
                )
                OutlinedTextField(
                    value = portText,
                    onValueChange = { portText = it },
                    label = { Text("UDP Port (default 4000)") },
                    placeholder = { Text("4000") },
                    singleLine = true,
                    isError = portText.isNotEmpty() && !isValidPort,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            if (canSave) {
                                onSave(nameText.trim(), trimmedIp, portInt)
                            }
                        }
                    ),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (canSave) {
                        onSave(nameText.trim(), trimmedIp, portInt)
                    }
                },
                enabled = canSave,
            ) {
                Text(if (isEditing) "Save" else "Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}
