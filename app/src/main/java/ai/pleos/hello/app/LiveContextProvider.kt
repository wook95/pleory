package ai.pleos.hello.app

import ai.pleos.hello.core.Clock
import ai.pleos.hello.core.EndInfo
import ai.pleos.hello.core.GeoPoint
import ai.pleos.hello.core.SegmentRule
import ai.pleos.hello.core.TrackAccumulator
import ai.pleos.hello.core.TrackMath
import ai.pleos.hello.core.TrackSample
import ai.pleos.hello.data.Segment
import ai.pleos.hello.platform.LocationFix
import ai.pleos.hello.platform.DestinationState
import ai.pleos.hello.platform.LocationSource
import ai.pleos.hello.platform.NaviSource
import java.util.Calendar

/**
 * 실제 소스에서 맥락을 만든다. 위치 콜백과 snapshot() 은 같은 직렬 경로(메인 루퍼)에서 불린다.
 * 궤적 시각은 [clock](DemoClock 일 수 있음)으로 찍는다 — GPX 를 같은 배속으로 재생하기 때문(D7·D8).
 */
class LiveContextProvider(
    private val location: LocationSource,
    private val clock: Clock,
    private val navi: NaviSource? = null,
    private val profileId: String = "local",
) : ContextProvider {

    private var track = TrackAccumulator()
    private val samples = ArrayList<TrackSample>()
    private var last: LocationFix? = null

    /** S0 상태 텍스트용. 세션이 시작될 때 한 번 정해지고 주행 중 바뀌지 않는다. */
    @Volatile
    var sessionSegment: Segment = segment()
        private set

    private val listener: (LocationFix) -> Unit = { fix ->
        val sample = TrackSample(clock.nowMillis(), fix.lat, fix.lng)
        if (track.add(sample)) samples += sample
        last = fix
    }

    init {
        location.addListener(listener)
    }

    override fun profileId() = profileId

    /** 세션별로 비운다. 마지막으로 안 위치가 있으면 새 세션의 출발점으로 넣는다. */
    override fun onSessionStart() {
        sessionSegment = segment()
        track = TrackAccumulator()
        samples.clear()
        last?.let {
            val sample = TrackSample(clock.nowMillis(), it.lat, it.lng)
            track.add(sample)
            samples += sample
        }
    }

    override fun endInfo(): EndInfo {
        val first = samples.firstOrNull()?.let { GeoPoint(it.lat, it.lng) }
        val end = samples.lastOrNull()?.let { GeoPoint(it.lat, it.lng) }
        val dest = navi?.destination as? DestinationState.Known
        return EndInfo(
            track = track.summary(),
            points = samples.toList(),
            originLabel = first?.let(PlaceFixture::label),
            destLabel = dest?.name ?: end?.let(PlaceFixture::label),
            destPoint = dest?.let { GeoPoint(it.lat, it.lng) } ?: end,
            weather = WEATHER_FIXTURE,
        )
    }

    override fun segment(): Segment {
        val hour = Calendar.getInstance().apply { timeInMillis = clock.nowMillis() }.get(Calendar.HOUR_OF_DAY)
        return SegmentRule.decide(null, null, null, null, hour)
    }

    override fun snapshot(): ContextSnapshot {
        val fix = last ?: return ContextSnapshot()
        val speed = track.lastSpeedKmh
        return ContextSnapshot(
            lat = fix.lat,
            lng = fix.lng,
            placeLabel = PlaceFixture.label(GeoPoint(fix.lat, fix.lng)),
            odometerKm = track.summary().distanceKm,
            speedKmh = speed.toInt(),
            congested = track.summary().acceptedPoints > 1 && speed < TrackMath.CONGESTION_KMH,
        )
    }
}

/** 2일 데모는 날씨 API 대신 고정값(PRD §7). */
private const val WEATHER_FIXTURE = "비"

/** PLACEHOLDER(E1 임시): tools/demo-route.gpx 의 합성 경로용 좌표→라벨 표. 기획자가 실제 fixture 를 주면 교체한다. */
object PlaceFixture {
    private const val RADIUS_M = 1_500.0
    private val anchors = listOf(
        GeoPoint(37.5133, 127.1000) to "회사",
        GeoPoint(37.5210, 127.0300) to "올림픽대로",
        GeoPoint(37.5190, 127.0030) to "올림픽대로",
        GeoPoint(37.5170, 126.9760) to "올림픽대로",
        GeoPoint(37.5165, 126.9590) to "한강대교",
        GeoPoint(37.5400, 126.8400) to "집",
    )

    fun label(p: GeoPoint): String? =
        anchors.minByOrNull { TrackMath.distanceMeters(p, it.first) }
            ?.takeIf { TrackMath.distanceMeters(p, it.first) <= RADIUS_M }?.second
}
