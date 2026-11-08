package ai.pleos.hello.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackMathTest {
    // 위도 1도 ≈ 111.19km. 북쪽으로 m 미터 떨어진 점
    private fun p(sec: Long, northM: Double) = TrackSample(sec * 1000, 37.0 + northM / 111_195.0, 127.0)

    @Test fun haversineKnownDistance() {
        // 서울시청 ↔ 강남역 약 8.9km
        val d = TrackMath.haversineMeters(37.5663, 126.9779, 37.4979, 127.0276)
        assertEquals(8_900.0, d, 300.0)
    }

    @Test fun speedFromCoordsAndTime() {
        // 100m / 10s = 36km/h
        assertEquals(36.0, TrackMath.speedKmh(p(0, 0.0), p(10, 100.0)), 0.2)
    }

    @Test fun accumulatesDistance() {
        val s = TrackMath.summarize(listOf(p(0, 0.0), p(10, 100.0), p(20, 200.0)))
        assertEquals(0.2, s.distanceKm, 0.002)
        assertEquals(3, s.acceptedPoints)
    }

    @Test fun dropsDuplicateTimestamp() {
        val acc = TrackAccumulator()
        assertTrue(acc.add(p(0, 0.0)))
        assertFalse(acc.add(p(0, 50.0)))
    }

    @Test fun dropsReversedTimestamp() {
        val acc = TrackAccumulator()
        acc.add(p(100, 0.0)); acc.add(p(110, 100.0))
        assertFalse(acc.add(p(105, 50.0)))
        assertEquals(2, acc.summary().acceptedPoints)
    }

    @Test fun dropsGpsJumpOver200kmh() {
        val acc = TrackAccumulator()
        acc.add(p(0, 0.0))
        // 10초에 1km = 360km/h
        assertFalse(acc.add(p(10, 1_000.0)))
        // 점프 점을 버렸으니 다음 점은 원래 위치 기준으로 정상
        assertTrue(acc.add(p(20, 200.0)))
        assertEquals(0.2, acc.summary().distanceKm, 0.002)
    }

    @Test fun keeps190kmhButDrops210kmh() {
        // 10초: 190km/h = 527.8m, 210km/h = 583.3m
        assertTrue(TrackAccumulator().apply { add(p(0, 0.0)) }.add(p(10, 527.0)))
        assertFalse(TrackAccumulator().apply { add(p(0, 0.0)) }.add(p(10, 584.0)))
    }

    @Test fun congestionCountsSlowSegmentsOnly() {
        val pts = mutableListOf(p(0, 0.0))
        // 10분 동안 12km/h (3.33m/s) → 정체
        var t = 0L; var m = 0.0
        repeat(120) { t += 5; m += 16.667; pts += p(t, m) }
        // 이후 5분 72km/h (20m/s) → 정체 아님
        repeat(60) { t += 5; m += 100.0; pts += p(t, m) }
        val s = TrackMath.summarize(pts)
        assertEquals(10, s.congestionMin)
    }

    @Test fun threshold20kmh() {
        // 600초 동안 19km/h(3166m)는 정체 10분, 21km/h(3500m)는 정체 아님
        assertEquals(10, TrackMath.summarize(listOf(p(0, 0.0), p(600, 3_166.0))).congestionMin)
        assertEquals(0, TrackMath.summarize(listOf(p(0, 0.0), p(600, 3_500.0))).congestionMin)
    }

    @Test fun jumpedPointsDoNotAddCongestion() {
        val s = TrackMath.summarize(listOf(p(0, 0.0), p(600, 50_000.0), p(1200, 50.0)))
        // 점프 점 제거 → 0→1200초 동안 50m 이동(0.15km/h) 은 정체 20분
        assertEquals(20, s.congestionMin)
    }

    @Test fun emptyInput() {
        val s = TrackMath.summarize(emptyList())
        assertEquals(0.0, s.distanceKm, 0.0)
        assertEquals(0, s.congestionMin)
    }
}
