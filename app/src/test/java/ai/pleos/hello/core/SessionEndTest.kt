package ai.pleos.hello.core

import ai.pleos.hello.data.Segment
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SessionEndTest {
    private val tz = TimeZone.getTimeZone("Asia/Seoul")
    private val start = 1_794_130_800_000L // 2026-11-08 18:40:00 KST
    private val track = TrackSummary(distanceKm = 23.39, congestionMin = 24, acceptedPoints = 103)

    @Test
    fun `51분 반올림과 궤적 거리`() {
        val e = SessionEndCalc.compute(start, start + 51 * 60_000L + 18_000L, EndInfo(track = track, destLabel = "집"), timeZone = tz)
        assertEquals(51, e.durationMin)
        assertEquals(23.39, e.distanceKm, 1e-9)
        assertEquals(24, e.congestionMin)
        assertEquals(Segment.COMMUTE_OUT, e.segment)
    }

    @Test
    fun `30초 넘으면 올림`() {
        assertEquals(52, SessionEndCalc.compute(start, start + 51 * 60_000L + 31_000L, EndInfo(track = track), timeZone = tz).durationMin)
        assertEquals(0, SessionEndCalc.compute(start, start - 5_000L, EndInfo(track = track), timeZone = tz).durationMin)
    }

    @Test
    fun `Odometer 는 0 보다 클 때만 쓴다`() {
        assertEquals(30.0, SessionEndCalc.compute(start, start, EndInfo(track = track, odometerKm = 30.0), timeZone = tz).distanceKm, 1e-9)
        assertEquals(23.39, SessionEndCalc.compute(start, start, EndInfo(track = track, odometerKm = 0.0), timeZone = tz).distanceKm, 1e-9)
        assertEquals(23.39, SessionEndCalc.compute(start, start, EndInfo(track = track, odometerKm = null), timeZone = tz).distanceKm, 1e-9)
    }

    @Test
    fun `목적지명 없이 18시 출발이면 시각 규칙으로 퇴근`() {
        assertEquals(Segment.COMMUTE_OUT, SessionEndCalc.compute(start, start, EndInfo(track = track), timeZone = tz).segment)
    }

    @Test
    fun `시작과 끝 좌표는 첫 점과 마지막 점`() {
        val pts = listOf(TrackSample(0, 37.0, 127.0), TrackSample(10_000, 37.1, 127.1))
        val e = SessionEndCalc.compute(start, start, EndInfo(track = track, points = pts), timeZone = tz)
        assertEquals(37.0, e.startLat!!, 0.0)
        assertEquals(127.1, e.endLng!!, 0.0)
        assertNull(SessionEndCalc.compute(start, start, EndInfo(track = track), timeZone = tz).startLat)
    }

    @Test
    fun `궤적은 5초 간격으로 솎고 마지막 점은 남긴다`() {
        val pts = (0..12).map { TrackSample(it * 1_000L, 37.0, 127.0 + it * 0.001) }
        val kept = SessionEndCalc.downsample(pts).map { it.atMillis }
        assertEquals(listOf(0L, 5_000L, 10_000L, 12_000L), kept)
        assertEquals(emptyList<TrackSample>(), SessionEndCalc.downsample(emptyList()))
    }
}
