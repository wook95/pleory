package ai.pleos.hello.debugpanel

import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DemoPlanTest {
    private val seoul = TimeZone.getTimeZone("Asia/Seoul")

    // 2026-11-08 18:40:00 KST = 2026-11-08T09:40:00Z (앱 데모 시계 실측값과 같다)
    private val d1 = 1_794_130_800_000L

    @Test fun d1BaseIsTheSameDayAt1840() = assertEquals(d1, DemoPlan.at(d1 + 3 * 3_600_000L, 18, 40, seoul))

    @Test fun day2StartsNextDayAt0810() =
        // 2026-11-09 08:10 KST = 2026-11-08T23:10:00Z
        assertEquals(1_794_179_400_000L, DemoPlan.day2Base(d1, seoul))

    @Test fun day2WindowStartsAtNextMidnight() =
        // 2026-11-09 00:00 KST = 2026-11-08T15:00:00Z
        assertEquals(1_794_150_000_000L, DemoPlan.day2Start(d1, seoul))

    @Test fun leadIsHoldTimesSpeed() = assertEquals(252_000L, DemoPlan.leadMs(8_400, 30.0)) // 8.4초 × 30 = 앱 4분 12초

    @Test fun parseHoldsSeconds() = assertEquals(listOf(3_600L, 8_400L, 2_200L), DemoPlan.parseHolds("3.6, 8.4,2.2"))

    @Test fun parseHoldsRejectsGarbage() {
        assertNull(DemoPlan.parseHolds(null))
        assertNull(DemoPlan.parseHolds("3.6,x"))
    }
}
