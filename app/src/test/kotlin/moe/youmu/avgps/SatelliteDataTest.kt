package moe.youmu.avgps

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SatelliteDataTest {
    @Test
    fun mergeByPhysicalSatelliteDeduplicatesAndKeepsBestSignal() {
        val raw = listOf(
            // GPS SVID 1 on L1 (used, CNR 32.0) and L5 (not used, CNR 38.5)
            SatelliteData(type = "GPS", id = 1, azimuth = 120f, elevation = 45f, cnr = 32.0f, used = true),
            SatelliteData(type = "GPS", id = 1, azimuth = 121f, elevation = 46f, cnr = 38.5f, used = false),
            // Galileo SVID 5 on E1 (not used, CNR 20.0) and E5a (not used, CNR 28.0)
            SatelliteData(type = "Galileo", id = 5, azimuth = 210f, elevation = 30f, cnr = 20.0f, used = false),
            SatelliteData(type = "Galileo", id = 5, azimuth = 210f, elevation = 30f, cnr = 28.0f, used = false),
        )

        val merged = raw.mergeByPhysicalSatellite()
        assertEquals(2, merged.size)

        val gps1 = merged.first { it.type == "GPS" && it.id == 1 }
        assertTrue(gps1.used)
        assertEquals(32.0f, gps1.cnr, 0.001f)
        assertEquals(120f, gps1.azimuth, 0.001f)
        assertEquals(45f, gps1.elevation, 0.001f)

        val galileo5 = merged.first { it.type == "Galileo" && it.id == 5 }
        assertEquals(false, galileo5.used)
        assertEquals(28.0f, galileo5.cnr, 0.001f)
    }
}

