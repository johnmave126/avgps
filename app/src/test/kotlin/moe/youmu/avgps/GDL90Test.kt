package moe.youmu.avgps

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GDL90Test {
    @Test
    fun framesAndEscapesReservedBytes() {
        val packet = Gdl90Framer.frame(byteArrayOf(0x7D, 0x7E))

        assertEquals(0x7E, packet.first().toInt() and 0xFF)
        assertEquals(0x7E, packet.last().toInt() and 0xFF)
        assertTrue(packet.toList().windowed(2).contains(listOf(0x7D.toByte(), 0x5D.toByte())))
        assertTrue(packet.toList().windowed(2).contains(listOf(0x7D.toByte(), 0x5E.toByte())))
    }

    @Test
    fun ownshipPacketUsesGdl90Framing() {
        val location = LocationData(0, -122.0, 47.0, 1000.0, 50f, 90f, 5f, 8f)
        val packet = generateOwnship(location)

        assertEquals(0x7E, packet.first().toInt() and 0xFF)
        assertEquals(0x7E, packet.last().toInt() and 0xFF)

        val report = Gdl90OwnshipReport(location)
        assertEquals(10.toByte(), report.messageId)
        assertEquals(28, report.buildPayload().size)
    }

    @Test
    fun geometricAltitudePacketStructure() {
        val location = LocationData(0, -122.0, 47.0, 1000.0, 50f, 90f, 5f, 8f)
        val altitudeReport = Gdl90OwnshipAltitudeReport(location)

        assertEquals(11.toByte(), altitudeReport.messageId)
        assertEquals(5, altitudeReport.buildPayload().size)
    }

    @Test
    fun heartbeatPacketStructure() {
        val heartbeat = Gdl90HeartbeatReport(isGpsValid = true, timestamp = 1700000000000L)

        assertEquals(0.toByte(), heartbeat.messageId)
        assertEquals(7, heartbeat.buildPayload().size)
    }

    @Test
    fun foreflightIdPacketStructure() {
        val idMessage = ForeFlightIdMessage("TestDevice".toByteArray())

        assertEquals(0x65.toByte(), idMessage.messageId)
        assertEquals(39, idMessage.buildPayload().size)
    }

    @Test
    fun truncatesUtf8StringsSafely() {
        val text = "avGPS Long Device Name"
        val truncated = text.truncateUtf8(16)
        val bytes = truncated.toByteArray(java.nio.charset.StandardCharsets.UTF_8)
        assertTrue(bytes.size <= 16)
        assertTrue(truncated.endsWith("…"))
    }

    @Test
    fun shortStringsAreNotTruncated() {
        val text = "ShortName"
        assertEquals(text, text.truncateUtf8(16))
    }

    @Test
    fun truncateHandlesMultiByteUtf8Characters() {
        val text = "avGPS📱LongDeviceName"
        val truncated = text.truncateUtf8(16)
        val bytes = truncated.toByteArray(java.nio.charset.StandardCharsets.UTF_8)
        assertTrue(bytes.size <= 16)
        assertTrue(truncated.endsWith("…"))
    }

    @Test
    fun generateIdFromStringHandlesTruncation() {
        val packet = generateID("avGPS Very Long Device Name Exceeding 16 Bytes")
        assertEquals(0x7E, packet.first().toInt() and 0xFF)
        assertEquals(0x7E, packet.last().toInt() and 0xFF)
    }
}
