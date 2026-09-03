package moe.youmu.avgps

import org.junit.Assert.assertEquals
import org.junit.Test

class UnitsTest {
    @Test
    fun formatsAviationUnits() {
        assertEquals("3280 ft", Altitude.ofMeters(1000.0).format(AltitudeUnit.FEET))
        assertEquals("194.4 knot", Speed.ofMps(100.0).format(SpeedUnit.KNOTS))
        assertEquals("1.00 nm", Distance.ofMeters(1852.0).format(DistanceUnit.NAUTICAL_MILES))
    }

    @Test
    fun formatsMetricUnits() {
        assertEquals("1000 m", Altitude.ofMeters(1000.9).format(AltitudeUnit.METERS))
        assertEquals("10.0 m/s", Speed.ofMps(10.0).format(SpeedUnit.METERS_PER_SECOND))
        assertEquals("25 m", Distance.ofMeters(25.9).format(DistanceUnit.METERS))
    }

    @Test
    fun valueClassConversionsAreExact() {
        val alt = Altitude.ofFeet(1000.0)
        assertEquals(304.8, alt.toMeters(), 0.001)

        val speed = Speed.ofKnots(100.0)
        assertEquals(51.444, speed.toMps(), 0.001)

        val dist = Distance.ofNauticalMiles(1.0)
        assertEquals(1852.0, dist.toMeters(), 0.001)
    }

    @Test
    fun formatsPressureUnits() {
        assertEquals("29.92 inHg", Pressure.ofInHg(29.92).format(PressureUnit.INHG))
        assertEquals("1013 hPa", Pressure.ofHpa(1013.25).format(PressureUnit.HPA))
    }

    @Test
    fun pressureValueClassConversionsAreExact() {
        val pressureFromInHg = Pressure.ofInHg(29.92)
        assertEquals(1013.207, pressureFromInHg.toHpa(), 0.001)

        val pressureFromHpa = Pressure.ofHpa(1013.25)
        assertEquals(29.921, pressureFromHpa.toInHg(), 0.001)
    }
}
