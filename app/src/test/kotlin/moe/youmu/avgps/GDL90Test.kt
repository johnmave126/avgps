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
        val packet = generateOwnship(
            LocationData(0, -122.0, 47.0, 1000.0, 50f, 90f, 5f, 8f),
        )

        assertEquals(0x7E, packet.first().toInt() and 0xFF)
        assertEquals(0x7E, packet.last().toInt() and 0xFF)
    }
}
