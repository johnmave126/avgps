package moe.youmu.avgps

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class EfbDiscoveryServerTest {
    @Test
    fun decodeMinimalDiscoveryMessage() {
        val raw = """{"App":"ForeFlight","GDL90":{"port":4000}}"""
        val msg = EfbDiscoveryServer.decodeMessage(raw)
        assertEquals("ForeFlight", msg.app)
        assertEquals(4000, msg.gdl90Port)
        assertNull(msg.version)
        assertNull(msg.deviceName)
    }

    @Test
    fun decodeForeFlightWithVersionAndDeviceName() {
        val raw = """{"Version":"18.8.5","GDL90":{"port":4000},"App":"ForeFlight","DeviceName":"伊吹萃香"}"""
        val msg = EfbDiscoveryServer.decodeMessage(raw)
        assertEquals("ForeFlight", msg.app)
        assertEquals(4000, msg.gdl90Port)
        assertEquals("18.8.5", msg.version)
        assertEquals("伊吹萃香", msg.deviceName)
    }

    @Test
    fun decodePermissivelyWithUnknownNestedFields() {
        val raw = """
            {
                "Version": "19.0.0",
                "GDL90": {
                    "port": 4000,
                    "protocol": "udp",
                    "capabilities": ["ownship", "traffic"]
                },
                "App": "ForeFlight",
                "DeviceName": "iPad Pro",
                "FutureObject": {"nested": true},
                "FutureArray": [1, 2, 3]
            }
        """.trimIndent()
        val msg = EfbDiscoveryServer.decodeMessage(raw)
        assertEquals("ForeFlight", msg.app)
        assertEquals(4000, msg.gdl90Port)
        assertEquals("19.0.0", msg.version)
        assertEquals("iPad Pro", msg.deviceName)
    }

    @Test
    fun serverBindsAndReceivesDiscoveryPacketOverUdp() {
        val testPort = 59393
        val latch = CountDownLatch(1)
        val receivedMessage = AtomicReference<EFBDiscoveryMessage>()
        val server = EfbDiscoveryServer(listenPort = testPort) { _, msg ->
            receivedMessage.set(msg)
            latch.countDown()
        }

        try {
            server.start()
            Thread.sleep(50)
            val payload = """{"Version":"18.8.5","GDL90":{"port":4000},"App":"ForeFlight","DeviceName":"伊吹萃香"}"""
                .toByteArray(Charsets.UTF_8)
            DatagramSocket().use { clientSocket ->
                val packet = DatagramPacket(payload, payload.size, InetAddress.getLoopbackAddress(), testPort)
                clientSocket.send(packet)
            }
            assertTrue("Expected discovery callback within timeout", latch.await(2, TimeUnit.SECONDS))
            val msg = receivedMessage.get()
            assertNotNull(msg)
            assertEquals("ForeFlight", msg.app)
            assertEquals(4000, msg.gdl90Port)
            assertEquals("18.8.5", msg.version)
            assertEquals("伊吹萃香", msg.deviceName)
        } finally {
            server.stop()
        }
    }
}
