package moe.youmu.avgps

import org.junit.Assert.assertEquals
import org.junit.Test

class CoordinateFormatterTest {
    @Test
    fun formatsTimeSinceCorrectly() {
        assertEquals("0s ago", CoordinateFormatter.formatTimeSince(-5))
        assertEquals("42s ago", CoordinateFormatter.formatTimeSince(42))
        assertEquals("3m ago", CoordinateFormatter.formatTimeSince(185))
        assertEquals("2h ago", CoordinateFormatter.formatTimeSince(7200))
    }
}
