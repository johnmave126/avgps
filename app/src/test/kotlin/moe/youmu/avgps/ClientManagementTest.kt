package moe.youmu.avgps

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetSocketAddress
import java.time.Instant

class ClientManagementTest {
    @Test
    fun defaultClientIsAutoDiscovered() {
        val address = InetSocketAddress("192.168.1.100", 4000)
        val client = Client(
            source = address,
            efb = "ForeFlight",
            lastDiscover = Instant.EPOCH,
            isEnabled = false,
        )

        assertFalse(client.isManual)
        assertFalse(client.isEnabled)
        assertEquals("ForeFlight", client.efb)
        assertEquals(4000, client.source.port)
    }

    @Test
    fun manualClientProperties() {
        val address = InetSocketAddress("10.0.0.50", 4000)
        val client = Client(
            source = address,
            efb = "iPad Mini",
            lastDiscover = Instant.now(),
            isEnabled = true,
            isManual = true,
        )

        assertTrue(client.isManual)
        assertTrue(client.isEnabled)
        assertEquals("iPad Mini", client.efb)
        assertEquals(4000, client.source.port)
    }

    @Test
    fun clientCopyMaintainsOrUpdatesManualFlag() {
        val address = InetSocketAddress("192.168.43.15", 4000)
        val manual = Client(address, "Manual Target", Instant.now(), isEnabled = true, isManual = true)
        val disabled = manual.copy(isEnabled = false)

        assertTrue(disabled.isManual)
        assertFalse(disabled.isEnabled)
    }
}
