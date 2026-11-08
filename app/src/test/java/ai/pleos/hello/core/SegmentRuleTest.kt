package ai.pleos.hello.core

import ai.pleos.hello.data.Segment
import org.junit.Assert.assertEquals
import org.junit.Test

class SegmentRuleTest {
    private val home = GeoPoint(37.5000, 127.0000)
    private val work = GeoPoint(37.5700, 127.0500)
    private fun near(p: GeoPoint, northM: Double) = GeoPoint(p.lat + northM / 111_000.0, p.lng)

    @Test fun nearHomeIsCommuteOut() =
        assertEquals(Segment.COMMUTE_OUT, SegmentRule.decide(home, work, near(home, 100.0), null, 8))

    @Test fun nearWorkIsCommuteIn() =
        assertEquals(Segment.COMMUTE_IN, SegmentRule.decide(home, work, near(work, 100.0), null, 19))

    @Test fun outsideRadiusIsOuting() =
        assertEquals(Segment.OUTING, SegmentRule.decide(home, work, near(home, 500.0), "집", 8))

    @Test fun onlyHomeKnownOtherDestIsOuting() =
        assertEquals(Segment.OUTING, SegmentRule.decide(home, null, GeoPoint(37.6, 127.1), null, 8))

    @Test fun placesKnownButNoDestinationFallsToLabel() =
        assertEquals(Segment.COMMUTE_OUT, SegmentRule.decide(home, work, null, "집", 8))

    @Test fun labelHome() = assertEquals(Segment.COMMUTE_OUT, SegmentRule.decide(null, null, null, "집", 8))
    @Test fun labelWork() = assertEquals(Segment.COMMUTE_IN, SegmentRule.decide(null, null, null, "회사", 19))

    @Test fun hourRanges() {
        fun h(x: Int) = SegmentRule.decide(null, null, null, "마트", x)
        assertEquals(Segment.OUTING, h(5))
        assertEquals(Segment.COMMUTE_IN, h(6))
        assertEquals(Segment.COMMUTE_IN, h(9))
        assertEquals(Segment.OUTING, h(10))
        assertEquals(Segment.OUTING, h(15))
        assertEquals(Segment.COMMUTE_OUT, h(16))
        assertEquals(Segment.COMMUTE_OUT, h(20))
        assertEquals(Segment.OUTING, h(21))
    }

    @Test fun noLabelUsesHour() = assertEquals(Segment.COMMUTE_OUT, SegmentRule.decide(null, null, null, null, 18))
}
