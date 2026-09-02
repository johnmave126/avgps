package moe.youmu.avgps

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.util.Locale

private val SlateHeaderBg = Color(0xFF6A7F85)
private val ActiveSatelliteGreen = Color(0xFF2E7D32)
private val InactiveSatelliteGrey = Color(0xFFB0BEC5)

@Composable
fun SatellitesScreen(
    satellites: List<SatelliteData>,
    modifier: Modifier = Modifier,
) {
    val constellationOrder = listOf(
        "GPS",
        "Beidou",
        "Galileo",
        "GLONASS",
        "Michibiki",
        "IRNSS",
        "SBAS",
        "Unknown",
    )

    val satellitesByType = satellites.groupBy { it.type }
    val presentConstellations = constellationOrder.filter { satellitesByType.containsKey(it) }

    if (satellites.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "Waiting for satellite status data...",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    LazyColumn(modifier = modifier.fillMaxSize()) {
        presentConstellations.forEach { constellation ->
            val list = satellitesByType[constellation]?.sortedBy { it.id }.orEmpty()

            // Header Banner
            item(key = "header_$constellation") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SlateHeaderBg)
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                ) {
                    Text(
                        constellation,
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Spacer(Modifier.weight(1f))
                        Text(
                            "ID",
                            modifier = Modifier.weight(2f),
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            "CNR",
                            modifier = Modifier.weight(4f),
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            "Azimuth",
                            modifier = Modifier.weight(4f),
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            "Elevation",
                            modifier = Modifier.weight(4f),
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }

            // Satellite Data Rows
            items(list, key = { "${it.type}_${it.id}" }) { satellite ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(
                                    if (satellite.used) ActiveSatelliteGreen else InactiveSatelliteGrey,
                                    shape = CircleShape,
                                ),
                        )
                    }
                    Text(
                        satellite.id.toString(),
                        modifier = Modifier.weight(2f),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        "%.1f".format(Locale.US, satellite.cnr),
                        modifier = Modifier.weight(4f),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        "%.1f°".format(Locale.US, satellite.azimuth),
                        modifier = Modifier.weight(4f),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        "%.1f°".format(Locale.US, satellite.elevation),
                        modifier = Modifier.weight(4f),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}
