package ai.pleos.hello.core

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/** 궤적 한 점. [atMillis] 는 Clock 에서 온 값. */
data class TrackSample(val atMillis: Long, val lat: Double, val lng: Double)

/** 거리·속도·정체 계산(TRD D8). Location.speed 는 쓰지 않고 좌표와 시각으로 직접 계산한다. */
object TrackMath {
    const val MAX_SPEED_KMH = 200.0
    const val CONGESTION_KMH = 20.0
    private const val EARTH_RADIUS_M = 6_371_000.0

    fun distanceMeters(a: GeoPoint, b: GeoPoint): Double = haversineMeters(a.lat, a.lng, b.lat, b.lng)

    fun haversineMeters(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val p1 = Math.toRadians(lat1)
        val p2 = Math.toRadians(lat2)
        val dp = Math.toRadians(lat2 - lat1)
        val dl = Math.toRadians(lng2 - lng1)
        val h = sin(dp / 2) * sin(dp / 2) + cos(p1) * cos(p2) * sin(dl / 2) * sin(dl / 2)
        return 2 * EARTH_RADIUS_M * atan2(sqrt(h), sqrt(1 - h))
    }

    fun speedKmh(from: TrackSample, to: TrackSample): Double {
        val dtSec = (to.atMillis - from.atMillis) / 1000.0
        if (dtSec <= 0) return 0.0
        return haversineMeters(from.lat, from.lng, to.lat, to.lng) / dtSec * 3.6
    }

    fun summarize(points: List<TrackSample>): TrackSummary =
        TrackAccumulator().also { acc -> points.forEach { acc.add(it) } }.summary()
}

data class TrackSummary(val distanceKm: Double, val congestionMin: Int, val acceptedPoints: Int)

/**
 * 점을 하나씩 받아 누적한다(주행 중 실시간 사용).
 * 버리는 점: 직전 채택 점보다 시각이 같거나 과거(중복·역순·오래된 좌표), 직전 점 대비 속도가 200km/h 초과(GPS 점프).
 * 정체 시간: 채택된 구간의 속도가 20km/h 미만인 구간의 시간 합. 분 단위는 반올림.
 */
class TrackAccumulator {
    private var last: TrackSample? = null
    private var meters = 0.0
    private var congestedMillis = 0L
    private var accepted = 0

    /** 마지막 채택 구간의 속도(km/h). 첫 점이면 0. */
    var lastSpeedKmh = 0.0
        private set

    /** @return 점이 채택되면 true */
    fun add(p: TrackSample): Boolean {
        val prev = last
        if (prev == null) {
            last = p
            accepted++
            return true
        }
        if (p.atMillis <= prev.atMillis) return false
        val speed = TrackMath.speedKmh(prev, p)
        if (speed > TrackMath.MAX_SPEED_KMH) return false
        meters += TrackMath.haversineMeters(prev.lat, prev.lng, p.lat, p.lng)
        if (speed < TrackMath.CONGESTION_KMH) congestedMillis += p.atMillis - prev.atMillis
        lastSpeedKmh = speed
        last = p
        accepted++
        return true
    }

    fun summary() = TrackSummary(
        distanceKm = meters / 1000.0,
        congestionMin = max(0, Math.round(congestedMillis / 60_000.0).toInt()),
        acceptedPoints = accepted,
    )
}

/**
 * TrackPoint 적재 간격(PRD v4 §3·§12): 화면이 보이면 5초, 백그라운드면 30초. 위치 콜백 주기는 SDK 가 정하므로 앱이 거른다.
 * 첫 점은 항상 받는다. 시각은 궤적과 같은 시계(DemoClock 일 수 있음)로 잰다.
 */
class TrackSampler {
    private var lastAt: Long? = null

    fun due(atMillis: Long, foreground: Boolean): Boolean {
        val last = lastAt ?: return true
        return atMillis - last >= if (foreground) FOREGROUND_MS else BACKGROUND_MS
    }

    fun taken(atMillis: Long) {
        lastAt = atMillis
    }

    companion object {
        const val FOREGROUND_MS = 5_000L
        const val BACKGROUND_MS = 30_000L
    }
}
