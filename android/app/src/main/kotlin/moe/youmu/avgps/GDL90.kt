package moe.youmu.avgps

import java.nio.ByteBuffer
import kotlin.experimental.xor
import kotlin.math.roundToInt

fun makeGDL90(payload: ByteArray): ByteArray {
    // 2 bytes for begin/end flag
    // 2 * 2 bytes for Frame Check Sequence, including possible bit stuffing
    // 2 * payload.size for possible bit stuffing
    val crc = crc16(payload)
    val packet = ByteBuffer.allocate(2 + 4 + 2 * payload.size)
    packet.put(0x7E)
    for (byte in payload) {
        when (byte) {
            0x7D.toByte(), 0x7E.toByte() -> {
                packet.put(0x7D)
                packet.put(byte xor 0x20)
            }
            else -> {
                packet.put(byte)
            }
        }
    }
    for (byte in arrayOf(crc.toByte(), (crc shr 8).toByte())) {
        when (byte) {
            0x7D.toByte(), 0x7E.toByte() -> {
                packet.put(0x7D)
                packet.put(byte xor 0x20)
            }
            else -> {
                packet.put(byte)
            }
        }
    }
    packet.put(0x7E)

    packet.flip()
    val result = ByteArray(packet.limit())
    packet.get(result)
    return result
}

fun generateOwnship(location: LocationData): ByteArray {
    val report = ByteArray(28)

    // Message ID: 10_10 = Ownship Report
    report[0] = 10

    // Traffic Alert status: high 4 bits 0 = No alert
    // Target Identity Type: low 4 bits 1 = Self-assigned address
    report[1] = 1

    // Target Identity Address: 3 bytes, all 0
    report.fill(0, 2, 5)

    // Latitude: 3 bytes
    val lat = (location.latitude / 180 * (1 shl 23)).roundToInt()
    report[5] = (lat shr 16).toByte()
    report[6] = (lat shr 8).toByte()
    report[7] = lat.toByte()

    // Longitude: 3 bytes
    val lon = (location.longitude / 180 * (1 shl 23)).roundToInt()
    report[8] = (lon shr 16).toByte()
    report[9] = (lon shr 8).toByte()
    report[10] = lon.toByte()

    // Altitude: 1 byte + high 4 bits
    // Miscellaneous Indicators: 1001, airborne, updated report, true track angle
    val alt = ((mToFt(location.altitude) + 1000) / 25).roundToInt().coerceIn(0, 0xFFE)
    report[11] = (alt shr 4).toByte()
    report[12] = ((alt shl 4) or 0x9).toByte()

    // Navigation Integrity Category: high 4 bits 0 = Unknown
    // Navigation Accuracy Category: low 4 bits
    report[13] = mToHfom(location.horizontalAccuracy.toDouble())

    // Horizontal Velocity: 1 byte + high 4 bits
    // Vertical Velocity: low 4 bits + 1 byte, 0x800 = no vertical rate available
    val speed = mpsToKt(location.speed.toDouble()).roundToInt().coerceIn(0, 0xFFE)
    report[14] = (speed shr 4).toByte()
    report[15] = ((speed shl 4) or 0x8).toByte()
    report[16] = 0

    // Tracking: 1 byte
    val tracking = (location.bearing / 360 * 256).roundToInt()
    report[17] = tracking.toByte()

    // Emitter Category: 1 byte, 0 = No aircraft type information
    report[18] = 0

    // Call sign: 8 bytes, all 0x20 (space)
    report.fill(0x20, 19, 27)

    // Emergency: high 4 bits, 0 = no emergency
    // Spare: low 4 bits
    report[27] = 0

    return makeGDL90(report)
}

fun generateHeartbeat(isGPSValid: Boolean, timestamp: Long): ByteArray {
    val report = ByteArray(7)

    // Message ID: 0_10 = Heartbeat
    report[0] = 0

    // TODO: Currently we only target Foreflight, so we don't set other status bits,
    //  but eventually we want to at least set GPS low voltage bit
    // Status Byte 1: bit 7: GPS position valid, bit 0: initialized
    report[1] = if (isGPSValid) {
        0x81.toByte()
    } else {
        0x01.toByte()
    }

    // Status Byte 2: bit 7: MSB of timestamp, bit 0: UTC valid
    // Timestamp: 2 bytes, low 16 bits of timestamp
    val sinceDayStart = timestamp / (24 * 60 * 60 * 1000)
    report[2] = (((sinceDayStart shr 16) shl 7) or 0x1).toByte()
    report[3] = (sinceDayStart shr 8).toByte()
    report[4] = sinceDayStart.toByte()

    // Message Counts: 2 bytes, all 0
    report.fill(0, 5, 7)

    return makeGDL90(report)
}

fun generateID(deviceName: ByteArray): ByteArray {
    val report = ByteArray(39)

    // Message ID: 0x65 = Foreflight Message
    report[0] = 0x65

    // Sub-ID
    report[1] = 0

    // Version
    report[2] = 1

    // Device Serial Number: 8 bytes, all 0xFF = invalid
    report.fill(0xFF.toByte(), 3, 11)

    // Device Name: 8 bytes, avGPS
    report.fill(0, 11, 19)
    ByteBuffer.wrap(report, 11, 8).put("avGPS".toByteArray())

    // Device Long Name: 16 bytes, will use device name
    report.fill(0, 19, 35)
    ByteBuffer.wrap(report, 19, 16).put(deviceName)

    // Capabilities Mask: 4 bytes, lowest bit: 0 = WGS-84 Ellipsoid
    report.fill(0, 35, 39)

    return makeGDL90(report)
}

fun generateOwnshipAltitude(location: LocationData): ByteArray {
    val report = ByteArray(5)

    // Message ID: 10_10 = Ownship Geometric Altitude
    report[0] = 11

    // Ownship Geometric Altitude: 2 bytes
    val altitude = (mToFt(location.altitude) / 5).roundToInt().coerceIn(-32768, 32767)
    report[1] = (altitude shr 8).toByte()
    report[2] = altitude.toByte()

    // Vertical Warning Indicator: most significant bit, 0 = No Warning
    // Vertical Figure of Merit: low 7 bits + 1 byte
    val vfom = location.verticalAccuracy.roundToInt().coerceIn(0, 1 shl 15)
    report[3] = (vfom shr 8).toByte()
    report[4] = vfom.toByte()

    return makeGDL90(report)
}

val crc16Table = (0 until 256).map {
    (0 until 8).fold(it shl 8) { crc, _ ->
        (crc shl 1) xor (if (crc and 0x8000 == 0) {
            0
        } else {
            0x1021
        })
    } and 0xFFFF
}.toTypedArray()

fun crc16(input: ByteArray): Int {
    return input.fold(0) { remainder, byte ->
        (crc16Table[(remainder shr 8)] xor (remainder shl 8) xor byte.toUByte().toInt()) and 0xFFFF

    }
}

fun ftToM(x: Double): Double {
    return x * 0.3048
}

private fun mToFt(x: Double): Double {
    return x / 0.3048
}

fun ktToMps(x: Double): Double {
    return x * 0.5144444444
}

private fun mpsToKt(x: Double): Double {
    return x / 0.5144444444
}

private fun mToNm(x: Double): Double {
    return x / 1852
}

private fun mToHfom(x: Double): Byte {
    return if (x < 3) {
        11
    } else if (x < 10) {
        10
    } else if (x < 30) {
        9
    } else {
        nmToFom(mToNm(x))
    }
}

private fun nmToFom(x: Double): Byte {
    return if (x < 0.05) {
        8
    } else if (x < 0.1) {
        7
    } else if (x < 0.3) {
        6
    } else if (x < 0.5) {
        5
    } else if (x < 1.0) {
        4
    } else if (x < 2.0) {
        3
    } else if (x < 4.0) {
        2
    } else if (x < 10.0) {
        1
    } else {
        0
    }
}