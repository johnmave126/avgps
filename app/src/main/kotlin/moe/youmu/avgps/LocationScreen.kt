package moe.youmu.avgps

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

@Composable
fun LocationScreen(
    state: AvGpsState,
    settings: UnitSettings,
    nowEpochMs: Long,
    onClearClients: () -> Unit,
    onSetClientEnabled: (Client, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val location = state.location
    val hasLocation = location != null

    val lastFixString = if (hasLocation) {
        val elapsedSec = ((nowEpochMs - location!!.timestamp) / 1000).coerceAtLeast(0)
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
                                CoordinateFormatter.formatLatitudeDms(location!!.latitude),
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
                        // Col 1: Altitude & Horizontal Accuracy
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            MetricBox(
                                label = "altitude",
                                value = if (hasLocation) Altitude.ofMeters(location!!.altitude)
                                    .format(settings.altitude) else "N/A",
                            )
                            MetricBox(
                                label = "horizontal accuracy",
                                value = if (hasLocation) Distance.ofMeters(location!!.horizontalAccuracy.toDouble())
                                    .format(settings.distance) else "N/A",
                            )
                        }

                        // Col 2: Speed & Vertical Accuracy
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            MetricBox(
                                label = "speed",
                                value = if (hasLocation) Speed.ofMps(location!!.speed.toDouble())
                                    .format(settings.speed) else "N/A",
                            )
                            MetricBox(
                                label = "vertical accuracy",
                                value = if (hasLocation) Altitude.ofMeters(location!!.verticalAccuracy.toDouble())
                                    .format(settings.altitude) else "N/A",
                            )
                        }

                        // Col 3: Bearing & Last Fix
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            MetricBox(
                                label = "bearing",
                                value = if (hasLocation) "${location!!.bearing.roundToInt()}°" else "N/A",
                            )
                            MetricBox(
                                label = "last fix",
                                value = lastFixString,
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
                    TextButton(
                        onClick = onClearClients,
                        enabled = state.clients.isNotEmpty(),
                        modifier = Modifier.align(Alignment.Start),
                    ) {
                        Text("Clear", color = MaterialTheme.colorScheme.primary)
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
                                    .padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        client.efb,
                                        style = MaterialTheme.typography.titleMedium,
                                    )
                                    Text(
                                        "${client.source} last seen: $lastSeenText",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Switch(
                                    checked = client.isEnabled,
                                    onCheckedChange = { onSetClientEnabled(client, it) },
                                )
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricBox(label: String, value: String) {
    Column(horizontalAlignment = Alignment.Start) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Normal,
        )
    }
}
