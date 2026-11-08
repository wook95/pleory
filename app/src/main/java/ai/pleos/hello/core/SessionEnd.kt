package ai.pleos.hello.core

import ai.pleos.hello.data.Segment
import java.util.Calendar
import java.util.TimeZone
import kotlin.math.max

/** 세션이 끝나는 순간(카드 표시 시점)에 컨텍스트가 넘기는 원재료. */
data class EndInfo(
    val track: TrackSummary = TrackSummary(0.0, 0, 0),
    val points: List<TrackSample> = emptyList(),
    val originLabel: String? = null,
    val destLabel: String? = null,
    val destPoint: GeoPoint? = null,
    val weather: String? = null,
    /** 세션 동안의 Odometer 증가분. 0 이하이거나 모르면 null (D8: 0 이면 쓰지 않는다) */
    val odometerKm: Double? = null,
)

/** Session 행에 저장하는 종료값(PRD §3). */
data class SessionEnd(
    val durationMin: Int,
    val distanceKm: Double,
    val congestionMin: Int,
    val segment: Segment,
    val originLabel: String?,
    val destLabel: String?,
    val weather: String?,
    val startLat: Double?,
    val startLng: Double?,
    val endLat: Double?,
    val endLng: Double?,
)

object SessionEndCalc {
    const val TRACK_GAP_MS = 5_000L

    /** 주행 시간은 분 단위 반올림. 거리는 Odometer(> 0 일 때만), 없으면 궤적 적분(D8). */
    fun compute(
        startedAt: Long,
        endedAt: Long,
        info: EndInfo,
        home: GeoPoint? = null,
        work: GeoPoint? = null,
        timeZone: TimeZone = TimeZone.getDefault(),
    ): SessionEnd {
        val odo = info.odometerKm
        val startHour = Calendar.getInstance(timeZone).apply { timeInMillis = startedAt }.get(Calendar.HOUR_OF_DAY)
        return SessionEnd(
            durationMin = max(0, Math.round((endedAt - startedAt) / 60_000.0).toInt()),
            distanceKm = if (odo != null && odo > 0.0) odo else info.track.distanceKm,
            congestionMin = info.track.congestionMin,
            segment = SegmentRule.decide(home, work, info.destPoint ?: info.points.lastOrNull()?.let { GeoPoint(it.lat, it.lng) }, info.destLabel, startHour),
            originLabel = info.originLabel,
            destLabel = info.destLabel,
            weather = info.weather,
            startLat = info.points.firstOrNull()?.lat,
            startLng = info.points.firstOrNull()?.lng,
            endLat = info.points.lastOrNull()?.lat,
            endLng = info.points.lastOrNull()?.lng,
        )
    }

    /** DB 에 쌓을 궤적을 5초 간격으로 솎는다(PRD §3). 마지막 점은 항상 남긴다. */
    fun downsample(points: List<TrackSample>, gapMs: Long = TRACK_GAP_MS): List<TrackSample> {
        if (points.isEmpty()) return points
        val out = ArrayList<TrackSample>()
        var lastKept = Long.MIN_VALUE
        for (p in points) {
            if (out.isEmpty() || p.atMillis - lastKept >= gapMs) {
                out += p
                lastKept = p.atMillis
            }
        }
        if (out.last() != points.last()) out += points.last()
        return out
    }
}
