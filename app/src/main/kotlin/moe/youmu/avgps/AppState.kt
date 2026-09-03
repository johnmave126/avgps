package moe.youmu.avgps

import java.net.InetSocketAddress
import java.time.Instant

data class LocationData(
    val timestamp: Long,
    val longitude: Double,
    val latitude: Double,
    val altitude: Double,
    val speed: Float,
    val bearing: Float,
    val horizontalAccuracy: Float,
    val verticalAccuracy: Float,
)

data class SatelliteData(
    val type: String,
    val id: Int,
    val azimuth: Float,
    val elevation: Float,
    val cnr: Float,
    val used: Boolean,
)

data class Client(
    val source: InetSocketAddress,
    val efb: String,
    val lastDiscover: Instant,
    val isEnabled: Boolean,
)

data class AvGpsState(
    val location: LocationData? = null,
    val clients: List<Client> = emptyList(),
    val satellites: List<SatelliteData> = emptyList(),
    val hasBarometer: Boolean = false,
    val pressureHpa: Float? = null,
    val reportPressureAltitude: Boolean = false,
    val altimeterSettingHpa: Double = 1013.25,
    val callsign: String = "",
)

data class UnitSettings(
    val altitude: AltitudeUnit = AltitudeUnit.METERS,
    val speed: SpeedUnit = SpeedUnit.METERS_PER_SECOND,
    val distance: DistanceUnit = DistanceUnit.METERS,
    val pressure: PressureUnit = PressureUnit.INHG,
)
