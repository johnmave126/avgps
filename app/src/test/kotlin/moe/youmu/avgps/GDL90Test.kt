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
        val text = "Ownship Long Device Name"
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
        val text = "Ownship📱LongDeviceName"
        val truncated = text.truncateUtf8(16)
        val bytes = truncated.toByteArray(java.nio.charset.StandardCharsets.UTF_8)
        assertTrue(bytes.size <= 16)
        assertTrue(truncated.endsWith("…"))
    }

    @Test
    fun generateIdFromStringHandlesTruncation() {
        val packet = generateID("Ownship Very Long Device Name Exceeding 16 Bytes")
        assertEquals(0x7E, packet.first().toInt() and 0xFF)
        assertEquals(0x7E, packet.last().toInt() and 0xFF)
    }

    @Test
    fun encodesPressureAltitudeCorrectly() {
        // Minimum valid altitude: -1,000 ft -> 0x000
        assertEquals(0x000, encodePressureAltitude(-1000.0))
        // 0 ft -> 0x028
        assertEquals(0x028, encodePressureAltitude(0.0))
        // +1000 ft -> 0x050
        assertEquals(0x050, encodePressureAltitude(1000.0))
        // Maximum valid altitude: +101,350 ft -> 0xFFE
        assertEquals(0xFFE, encodePressureAltitude(101350.0))

        // Invalid or unavailable values -> 0xFFF
        assertEquals(0xFFF, encodePressureAltitude(null))
        assertEquals(0xFFF, encodePressureAltitude(Double.NaN))
        assertEquals(0xFFF, encodePressureAltitude(-1001.0))
        assertEquals(0xFFF, encodePressureAltitude(101351.0))
    }

    @Test
    fun ownshipPacketEncodesPressureAltitudeInPayload() {
        val location = LocationData(0, -122.0, 47.0, 1000.0, 50f, 90f, 5f, 8f)

        // Without pressure altitude: default 0xFFF (bytes 0xFF, 0xF9)
        val defaultPayload = Gdl90OwnshipReport(location).buildPayload()
        assertEquals(0xFF.toByte(), defaultPayload[11])
        assertEquals(0xF9.toByte(), defaultPayload[12])

        // With 0 ft (0x028): high 8 bits = 0x02, low 4 bits = 0x8 | misc 0x9 -> 0x89
        val zeroFtPayload = Gdl90OwnshipReport(location, pressureAltitudeFeet = 0.0).buildPayload()
        assertEquals(0x02.toByte(), zeroFtPayload[11])
        assertEquals(0x89.toByte(), zeroFtPayload[12])

        // With 1000 ft (0x050): high 8 bits = 0x05, low 4 bits = 0x0 | misc 0x9 -> 0x09
        val thousandFtPayload = Gdl90OwnshipReport(location, pressureAltitudeFeet = 1000.0).buildPayload()
        assertEquals(0x05.toByte(), thousandFtPayload[11])
        assertEquals(0x09.toByte(), thousandFtPayload[12])
    }

    @Test
    fun ownshipPacketEncodesCallsignInPayload() {
        val location = LocationData(0, -122.0, 47.0, 1000.0, 50f, 90f, 5f, 8f)

        // Default / blank callsign: 8 ASCII spaces (0x20) at bytes 19..26
        val defaultPayload = Gdl90OwnshipReport(location).buildPayload()
        for (i in 19..26) {
            assertEquals(0x20.toByte(), defaultPayload[i])
        }

        // Custom callsign "N12345": 6 ASCII chars followed by 2 spaces
        val reportPayload = Gdl90OwnshipReport(location, callSign = "N12345").buildPayload()
        val callsignBytes = reportPayload.sliceArray(19..26)
        assertEquals("N12345  ", String(callsignBytes, java.nio.charset.StandardCharsets.US_ASCII))

        // Also test generateOwnship
        val framed = generateOwnship(location, callSign = "N12345")
        // Framed starts with 0x7E, then payload bytes
        val framedCallsign = framed.sliceArray(20..27) // 1 byte offset for 0x7E flag
        assertEquals("N12345  ", String(framedCallsign, java.nio.charset.StandardCharsets.US_ASCII))
    }
}
