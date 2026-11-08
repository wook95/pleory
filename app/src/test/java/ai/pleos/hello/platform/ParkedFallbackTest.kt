package ai.pleos.hello.platform

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ParkedFallbackTest {
    @Test
    fun `첫 조회가 비어도 리스너가 살아 있으면 대체 판정을 켜지 않는다`() {
        org.junit.Assert.assertFalse(ParkedFallback.shouldStart(null, listenerSupported = true))
        org.junit.Assert.assertTrue(ParkedFallback.shouldStart(null, listenerSupported = false))
        org.junit.Assert.assertFalse(ParkedFallback.shouldStart(Gear.P, listenerSupported = false))
    }

    @Test
    fun parkedAfterHold() {
        val f = ParkedFallback(5_000)
        assertNull(f.onSpeed(0, 0.0))
        assertNull(f.onSpeed(4_000, 0.5))
        assertEquals(Gear.P, f.onSpeed(5_000, 0.0))
        assertNull(f.onSpeed(6_000, 0.0)) // 같은 값은 다시 내지 않는다
    }

    @Test
    fun movingEmitsDriveOnce() {
        val f = ParkedFallback()
        assertEquals(Gear.D, f.onSpeed(0, 30.0))
        assertNull(f.onSpeed(1_000, 40.0))
    }

    @Test
    fun slowCreepResetsHold() {
        val f = ParkedFallback(5_000)
        f.onSpeed(0, 0.0)
        assertNull(f.onSpeed(3_000, 3.0)) // 2~5km/h 사이: 주차도 주행도 아님
        assertNull(f.onSpeed(7_000, 0.0)) // 다시 0 부터 센다
        assertEquals(Gear.P, f.onSpeed(12_000, 0.0))
    }

    @Test
    fun uxRestrictionBlocksPark() {
        val f = ParkedFallback(5_000)
        f.onUx(true)
        assertNull(f.onSpeed(0, 0.0))
        assertNull(f.onSpeed(10_000, 0.0))
        assertNull(f.onUx(false)) // 여기서부터 센다
        assertEquals(Gear.P, f.onSpeed(15_000, 0.0))
    }

    @Test
    fun driveAgainAfterPark() {
        val f = ParkedFallback(1_000)
        f.onSpeed(0, 0.0)
        assertEquals(Gear.P, f.onSpeed(1_000, 0.0))
        assertEquals(Gear.D, f.onSpeed(2_000, 20.0))
    }
}
