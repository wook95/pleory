package ai.pleos.hello.core

import org.junit.Assert.assertEquals
import org.junit.Test

class DemoClockTest {
    @Test fun startsAtBase() {
        val wall = longArrayOf(5_000)
        val c = DemoClock(1_000_000, 10.0) { wall[0] }
        assertEquals(1_000_000, c.nowMillis())
    }

    @Test fun advancesAtSpeed() {
        val wall = longArrayOf(5_000)
        val c = DemoClock(1_000_000, 10.0) { wall[0] }
        wall[0] += 3_000
        assertEquals(1_030_000, c.nowMillis())
    }

    @Test fun speedOneIsRealTime() {
        val wall = longArrayOf(0)
        val c = DemoClock(100, 1.0) { wall[0] }
        wall[0] = 250
        assertEquals(350, c.nowMillis())
    }
}
