package moe.youmu.avgps

import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import kotlin.math.roundToInt

// ============================================================================
// GDL90 Protocol Framing & CRC
// ============================================================================

object Gdl90Framer {
    private const val FLAG_BYTE: Byte = 0x7E
    private const val ESCAPE_BYTE: Byte = 0x7D
    private const val ESCAPE_XOR: Byte = 0x20

    private val crcTable: IntArray = IntArray(256) { i ->
        (0 until 8).fold(i shl 8) { crc, _ ->
            (crc shl 1) xor (if (crc and 0x8000 == 0) 0 else 0x1021)
        } and 0xFFFF
    }

    /**
     * Computes the 16-bit CRC-CCITT required by the GDL90 specification.
     */
    fun computeCrc(input: ByteArray): Int {
        return input.fold(0) { remainder, byte ->
            (crcTable[(remainder shr 8) and 0xFF] xor (remainder shl 8) xor byte.toUByte().toInt()) and 0xFFFF
        }
    }

    /**
     * Frames a raw payload into a GDL90 packet:
     * Flag (0x7E) + Escaped Payload + Escaped CRC16 (LSB first) + Flag (0x7E).
     */
    fun frame(payload: ByteArray): ByteArray {
        val crc = computeCrc(payload)
        val buffer = ByteBuffer.allocate(2 + 4 + 2 * payload.size)

        buffer.put(FLAG_BYTE)
        putEscaped(buffer, payload)
        putEscaped(buffer, byteArrayOf(crc.toByte(), (crc shr 8).toByte()))
        buffer.put(FLAG_BYTE)

        buffer.flip()
        val result = ByteArray(buffer.remaining())
        buffer.get(result)
        return result
    }

    private fun putEscaped(buffer: ByteBuffer, data: ByteArray) {
        for (byte in data) {
            when (byte) {
                FLAG_BYTE, ESCAPE_BYTE -> {
                    buffer.put(ESCAPE_BYTE)
                    buffer.put((byte.toInt() xor ESCAPE_XOR.toInt()).toByte())
                }

                else -> buffer.put(byte)
            }
        }
    }
}

/**
 * Sequential buffer builder for constructing GDL90 binary payloads.
 */
class Gdl90PayloadWriter(capacity: Int) {
    private val buffer: ByteBuffer = ByteBuffer.allocate(capacity)

    fun writeByte(value: Int): Gdl90PayloadWriter {
        buffer.put(value.toByte())
        return this
    }

    fun writeInt16(value: Int): Gdl90PayloadWriter {
        buffer.put((value shr 8).toByte())
        buffer.put(value.toByte())
        return this
    }

    fun writeInt24(value: Int): Gdl90PayloadWriter {
        buffer.put((value shr 16).toByte())
        buffer.put((value shr 8).toByte())
        buffer.put(value.toByte())
        return this
    }

    fun writeZeros(count: Int): Gdl90PayloadWriter {
        repeat(count) {
            buffer.put(0.toByte())
        }
        return this
    }

    fun writeFixedPadded(bytes: ByteArray, totalLength: Int, padByte: Byte = 0): Gdl90PayloadWriter {
        val length = minOf(bytes.size, totalLength)
        buffer.put(bytes, 0, length)
        repeat(totalLength - length) {
            buffer.put(padByte)
        }
        return this
    }

    fun toByteArray(): ByteArray = buffer.array()
}

// ============================================================================
// Navigation Accuracy Category for Position (NACp / HFOM)
// ============================================================================

object NavigationAccuracyCategory {
    /**
     * Maps horizontal Estimated Position Uncertainty (EPU) to GDL90 NACp (HFOM).
     */
    fun fromDistance(distance: Distance): Byte {
        val meters = distance.toMeters()
        val nm = distance.toNauticalMiles()
        return when {
            meters < 3.0 -> 11
            meters < 10.0 -> 10
            meters < 30.0 -> 9
            nm < 0.05 -> 8
            nm < 0.10 -> 7
            nm < 0.30 -> 6
            nm < 0.50 -> 5
            nm < 1.00 -> 4
            nm < 2.00 -> 3
            nm < 4.00 -> 2
            nm < 10.0 -> 1
            else -> 0
        }
    }
}

// ============================================================================
// GDL90 Message Definitions & Builders
// ============================================================================

sealed interface Gdl90Message {
    val messageId: Byte
    fun buildPayload(): ByteArray
    fun toFramedPacket(): ByteArray = Gdl90Framer.frame(buildPayload())
}

/**
 * Encodes pressure altitude referenced to 29.92 inHg using 25-foot resolution,
 * offset by +1,000 feet. Returns 0xFFF if altitude is invalid, null, or out of range.
 * Minimum valid altitude is -1,000 ft (0x000), maximum is +101,350 ft (0xFFE).
 */
fun encodePressureAltitude(altitudeFeet: Double?): Int {
    if (altitudeFeet == null || altitudeFeet.isNaN() || altitudeFeet < -1000.0 || altitudeFeet > 101350.0) {
        return 0xFFF
    }
    val encoded = ((altitudeFeet + 1000.0) / 25.0).roundToInt()
    return encoded.coerceIn(0x000, 0xFFE)
}

/**
 * Ownship Report (Message ID 10).
 */
data class Gdl90OwnshipReport(
    val location: LocationData,
    val callSign: String = "        ",
    val pressureAltitudeFeet: Double? = null,
) : Gdl90Message {
    override val messageId: Byte = 10

    override fun buildPayload(): ByteArray {
        val writer = Gdl90PayloadWriter(28)

        // Message ID: 10_10 = Ownship Report
        writer.writeByte(messageId.toInt())

        // Traffic Alert status: high 4 bits 0 = No alert
        // Target Identity Type: low 4 bits 1 = Self-assigned address
        writer.writeByte(1)

        // Target Identity Address: 3 bytes, all 0
        writer.writeZeros(3)

        // Latitude: 3 bytes (semicircle conversion: lat / 180 * 2^23)
        val lat = (location.latitude / 180.0 * (1 shl 23)).roundToInt()
        writer.writeInt24(lat)

        // Longitude: 3 bytes (semicircle conversion: lon / 180 * 2^23)
        val lon = (location.longitude / 180.0 * (1 shl 23)).roundToInt()
        writer.writeInt24(lon)

        // Altitude: 1 byte + high 4 bits
        // Miscellaneous Indicators: 1001, airborne, updated report, true track angle
        val altCode = encodePressureAltitude(pressureAltitudeFeet)
        writer.writeByte((altCode shr 4) and 0xFF)
        writer.writeByte(((altCode and 0x0F) shl 4) or 0x09)

        // Navigation Integrity Category: high 4 bits 0 = Unknown
        // Navigation Accuracy Category: low 4 bits
        val nacp = NavigationAccuracyCategory.fromDistance(Distance.ofMeters(location.horizontalAccuracy.toDouble()))
        writer.writeByte(nacp.toInt())

        // Horizontal Velocity: 1 byte + high 4 bits
        // Vertical Velocity: low 4 bits + 1 byte, 0x800 = no vertical rate available
        val speedKnots = Speed.ofMps(location.speed.toDouble()).toKnots().roundToInt().coerceIn(0, 0xFFE)
        writer.writeByte(speedKnots shr 4)
        writer.writeByte((speedKnots shl 4) or 0x8)
        writer.writeByte(0)

        // Tracking: 1 byte (bearing / 360 * 256)
        val tracking = (location.bearing / 360.0 * 256.0).roundToInt()
        writer.writeByte(tracking)

        // Emitter Category: 1 byte, 0 = No aircraft type information
        writer.writeByte(0)

        // Call sign: 8 bytes, all 0x20 (space)
        writer.writeFixedPadded(callSign.toByteArray(StandardCharsets.US_ASCII), 8, 0x20.toByte())

        // Emergency: high 4 bits, 0 = no emergency
        // Spare: low 4 bits
        writer.writeByte(0)

        return writer.toByteArray()
    }
}

/**
 * Ownship Geometric Altitude Report (Message ID 11).
 */
data class Gdl90OwnshipAltitudeReport(
    val location: LocationData,
) : Gdl90Message {
    override val messageId: Byte = 11

    override fun buildPayload(): ByteArray {
        val writer = Gdl90PayloadWriter(5)

        // Message ID: 10_10 = Ownship Geometric Altitude
        writer.writeByte(messageId.toInt())

        // Ownship Geometric Altitude: 2 bytes (5-foot increments)
        val altitudeFeet = Altitude.ofMeters(location.altitude).toFeet()
        val altitude = (altitudeFeet / 5.0).roundToInt().coerceIn(-32768, 32767)
        writer.writeInt16(altitude)

        // Vertical Warning Indicator: most significant bit, 0 = No Warning
        // Vertical Figure of Merit: low 7 bits + 1 byte
        val vfom = location.verticalAccuracy.roundToInt().coerceIn(0, 1 shl 15)
        writer.writeInt16(vfom)

        return writer.toByteArray()
    }
}

/**
 * Heartbeat Report (Message ID 0).
 */
data class Gdl90HeartbeatReport(
    val isGpsValid: Boolean,
    val timestamp: Long,
) : Gdl90Message {
    override val messageId: Byte = 0

    override fun buildPayload(): ByteArray {
        val writer = Gdl90PayloadWriter(7)

        // Message ID: 0_10 = Heartbeat
        writer.writeByte(messageId.toInt())

        // TODO: Currently we only target Foreflight, so we don't set other status bits,
        //  but eventually we want to at least set GPS low voltage bit
        // Status Byte 1: bit 7: GPS position valid, bit 0: initialized
        val statusByte1 = if (isGpsValid) 0x81 else 0x01
        writer.writeByte(statusByte1)

        // Status Byte 2: bit 7: MSB of timestamp, bit 0: UTC valid
        // Timestamp: 2 bytes, low 16 bits of timestamp (seconds since UTC midnight)
        val sinceDayStart = timestamp / (24 * 60 * 60 * 1000)
        val statusByte2 = (((sinceDayStart shr 16) shl 7) or 0x1).toInt()
        writer.writeByte(statusByte2)
        writer.writeByte((sinceDayStart shr 8).toInt())
        writer.writeByte(sinceDayStart.toInt())

        // Message Counts: 2 bytes, all 0
        writer.writeZeros(2)

        return writer.toByteArray()
    }
}

/**
 * ForeFlight ID / Device Identity Message (Message ID 0x65).
 */
data class ForeFlightIdMessage(
    val deviceName: ByteArray,
) : Gdl90Message {
    override val messageId: Byte = 0x65

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ForeFlightIdMessage) return false
        return deviceName.contentEquals(other.deviceName)
    }

    override fun hashCode(): Int = deviceName.contentHashCode()

    override fun buildPayload(): ByteArray {
        val writer = Gdl90PayloadWriter(39)

        // Message ID: 0x65 = Foreflight Message
        writer.writeByte(messageId.toInt())

        // Sub-ID
        writer.writeByte(0)

        // Version
        writer.writeByte(1)

        // Device Serial Number: 8 bytes, all 0xFF = invalid
        writer.writeFixedPadded(ByteArray(0), 8, 0xFF.toByte())

        // Device Name: 8 bytes, avGPS
        writer.writeFixedPadded("avGPS".toByteArray(StandardCharsets.US_ASCII), 8, 0)

        // Device Long Name: 16 bytes, will use device name
        writer.writeFixedPadded(deviceName, 16, 0)

        // Capabilities Mask: 4 bytes, lowest bit: 0 = WGS-84 Ellipsoid
        writer.writeZeros(4)

        return writer.toByteArray()
    }
}

// ============================================================================
// Public Utility & Compatibility Functions
// ============================================================================

fun generateOwnship(
    location: LocationData,
    pressureAltitudeFeet: Double? = null,
    callSign: String = "        ",
): ByteArray =
    Gdl90OwnshipReport(
        location = location,
        pressureAltitudeFeet = pressureAltitudeFeet,
        callSign = callSign.ifBlank { "        " },
    ).toFramedPacket()

fun generateHeartbeat(isGPSValid: Boolean, timestamp: Long): ByteArray =
    Gdl90HeartbeatReport(isGPSValid, timestamp).toFramedPacket()

fun generateID(deviceName: ByteArray): ByteArray =
    ForeFlightIdMessage(deviceName).toFramedPacket()

fun generateID(deviceName: String): ByteArray =
    generateID(deviceName.truncateUtf8(16).encodeToByteArray())

/**
 * Truncates this string so that its UTF-8 representation occupies at most [maxBytes].
 * If truncated, appends [ellipsis] without exceeding [maxBytes].
 */
fun String.truncateUtf8(maxBytes: Int, ellipsis: String = "…"): String {
    val bytes = encodeToByteArray()
    if (bytes.size <= maxBytes) return this

    val ellipsisBytes = ellipsis.encodeToByteArray()
    val targetBytes = (maxBytes - ellipsisBytes.size).coerceAtLeast(0)

    val truncatedPrefix = bytes.copyOf(targetBytes)
        .decodeToString()
        .trimEnd { it == '\uFFFD' }

    return truncatedPrefix + ellipsis
}

fun generateOwnshipAltitude(location: LocationData): ByteArray =
    Gdl90OwnshipAltitudeReport(location).toFramedPacket()
