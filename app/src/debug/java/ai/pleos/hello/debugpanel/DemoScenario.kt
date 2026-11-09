package ai.pleos.hello.debugpanel

import ai.pleos.hello.PleoryApp
import ai.pleos.hello.core.GeoPoint
import ai.pleos.hello.core.SessionEvent
import ai.pleos.hello.core.TrackMath
import ai.pleos.hello.platform.DestinationState
import ai.pleos.hello.platform.Gear
import ai.pleos.hello.platform.LocationFix
import android.content.Context
import android.util.Log
import java.io.File
import java.util.Calendar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * PRD §8 1~6단계를 앱 안에서 재현하는 디버그 시나리오(T8, v2 는 docs/pleory/video-script.md "파이프라인 요구사항").
 *
 * 무장(arm) 상태에서 출발점 근처의 첫 위치 fix 가 오면 그 순간을 출발 시각으로 삼아 DemoClock 을 켜고 새 세션을 연다.
 * 그래서 호스트의 경로 재생(tools/play-route.sh)이 시작되는 시점과 앱의 시계가 어긋나지 않는다.
 * - v1: 18:40 출발, 18:57·19:08 에 한마디 2건(듣는 중 0.3초), 19:31 가짜 기어 P.
 * - v3 1일차: 긴 혼잣말 한 번(TRD §8-11, 영상 대본 v3). 대사 길이만큼 "듣고 있어요" 를 유지하고 19:08 무렵 저장된다.
 * - v2 2일차: 1일차 날짜 + 1일 08:10 출발, 거꾸로 된 경로(집 → 가짜 목적지 "회사"), 08:30 Gleo 호출 신호(호스트가 넣는다), 09:01 기어 P.
 * 도착은 마지막 위치 fix 의 300m 반경 판정이 맡는다.
 */
object DemoScenario {
    const val TAG = "PleoryDemo"
    const val DEFAULT_SPEED = 30.0

    private class Line(val text: String, val hour: Int, val minute: Int, val second: Int, val skipLlm: Boolean = false)

    /** v1 한마디 저장 시각 목표. 표시는 HH:mm 이라 분 안쪽 중간에 맞춘다. */
    private val LINES = listOf(
        Line("오늘 팀장 때문에 진짜 힘들었다", 18, 57, 20),
        Line("내일 거래처 서류 다시 보내야 해 아 노을 예쁘다", 19, 8, 20),
    )

    /** v3 1일차 혼잣말(video-script.md v3 대사 1). 단어는 tools/gen-vo.sh 의 ramble-1 과 같다. */
    const val RAMBLE = "오늘 팀장 때문에 진짜 힘들었다 아 근데 노들섬 예쁘네 이번 주말에 여기 데이트 한번 올까 " +
        "아 맞다 내일 거래처 서류 다시 보내야 되고 회의 자료도 만들어야 되네 날씨는 왜 이렇게 흐려 기분까지 우울하다 " +
        "짬뽕이나 먹어야겠다 요즘 주식은 어떻게 되고 있으려나"
    private val LINES_V3 = listOf(Line(RAMBLE, 19, 8, 20))

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var job: Job? = null
    private var armed: ((LocationFix) -> Unit)? = null

    /**
     * [viaText] 이면 STT 를 건너뛰고 텍스트를 인식 결과로 넣는다(구조화 이후는 동일).
     * [day] 0 = v1, 1·2 = v2 의 1·2일차. [holdsMs] 는 v2 1일차 한마디별 듣는 중 유지 시간(없으면 대본 기본값).
     */
    fun arm(app: PleoryApp, viaText: Boolean, speed: Double = DEFAULT_SPEED, day: Int = 0, holdsMs: List<Long>? = null) {
        cancel(app)
        val hub = app.sdkHub
        val start = if (day == 2) HOME else WORK
        lateinit var listener: (LocationFix) -> Unit
        listener = { fix ->
            // 이전 실행이 남긴 에뮬레이터의 마지막 위치(반복 송출됨)에는 반응하지 않는다. 경로 출발점 근처의 fix 만 받는다.
            if (TrackMath.distanceMeters(GeoPoint(fix.lat, fix.lng), start) <= START_RADIUS_M) {
                hub.location.removeListener(listener)
                armed = null
                job = scope.launch { run(app, viaText, speed, day, holdsMs ?: DemoPlan.DEFAULT_HOLDS_MS) }
            }
        }
        armed = listener
        hub.location.addListener(listener)
        // 목적지 없음 + 기어 P 5초 규칙(D9)으로 대기 중에 카드가 뜨지 않도록, 무장하자마자 목적지를 가짜로 정해 둔다.
        hub.setNaviOverride(destination(day))
        app.startLocationIfPermitted()
        Log.i(TAG, "demo armed day=$day viaText=$viaText speed=${speed}x holds=$holdsMs; waiting for first location fix")
    }

    fun cancel(app: PleoryApp) {
        job?.cancel()
        armed?.let { app.sdkHub.location.removeListener(it) }
        armed = null
    }

    /** 가짜 입력을 모두 걷어내고 시스템 시계로 돌아간다. */
    fun reset(app: PleoryApp) {
        cancel(app)
        app.clock.setDemo(null)
        app.sdkHub.setGearOverride(null)
        app.sdkHub.setNaviOverride(null)
    }

    private fun destination(day: Int) = if (day == 2) FAKE_WORK else FAKE_HOME

    private suspend fun run(app: PleoryApp, viaText: Boolean, speed: Double, day: Int, holdsMs: List<Long>) {
        val hub = app.sdkHub
        val base = when (day) {
            2 -> DemoPlan.day2Base(d1Base(app) ?: DemoPlan.at(System.currentTimeMillis(), 18, 40))
            else -> DemoPlan.at(System.currentTimeMillis(), 18, 40).also { if (day == 1) saveD1Base(app, it) }
        }
        app.clock.setDemo(speed, base)
        Log.i(TAG, "demo start day=$day: DemoClock ${speed}x at ${app.clock.nowMillis()}")
        app.controller.post(SessionEvent.IgnitionOff)
        app.controller.post(SessionEvent.IgnitionOn)
        hub.setNaviOverride(destination(day))
        hub.setGearOverride(Gear.D)

        if (day == 2) {
            waitUntil(app, 8, 30, 0, 0)
            Log.i(TAG, "cue gleo at ${app.clock.nowMillis()}") // 호스트(run-demo.sh)가 이 줄을 보고 Gleo 호출을 넣는다
            waitUntil(app, 9, 1, 0, 0)
        } else {
            val lines = if (day == 1) LINES_V3 else LINES
            lines.forEachIndexed { i, line ->
                val pcm = if (viaText) null else File(app.filesDir, "u${i + 1}.pcm").readBytes()
                // 저장 시각이 목표에 닿도록 앞당겨서 시작한다: PCM 은 입력 길이 + 인식 지연 약 0.25초(실측), 텍스트는 듣는 중 유지 시간.
                val holdMs = if (day == 1) holdsMs.getOrElse(i) { DemoPlan.DEFAULT_HOLDS_MS.last() } else app.speechInput.pendingTextDelayMs
                val leadMs = if (pcm != null) ((pcm.size / 32_000.0 + 0.25) * 1000 * speed).toLong() else DemoPlan.leadMs(holdMs, speed)
                waitUntil(app, line.hour, line.minute, line.second, -leadMs)
                if (pcm != null) {
                    app.speechInput.pendingPcm = pcm
                } else {
                    app.speechInput.pendingText = line.text
                    app.speechInput.pendingTextDelayMs = holdMs
                }
                if (line.skipLlm && day == 1) app.skipNextLlm = true
                Log.i(TAG, "inject utterance ${i + 1} (${if (viaText) "text" else "pcm"}) hold=${holdMs}ms skipLlm=${line.skipLlm && day == 1} at ${app.clock.nowMillis()}")
                app.controller.post(SessionEvent.TalkTapped)
            }
            waitUntil(app, 19, 31, 0, 0)
        }
        Log.i(TAG, "fake gear P at ${app.clock.nowMillis()}")
        hub.setGearOverride(Gear.P)
    }

    private suspend fun waitUntil(app: PleoryApp, hour: Int, minute: Int, second: Int, offsetMs: Long) {
        val target = Calendar.getInstance().apply {
            timeInMillis = app.clock.nowMillis()
            set(Calendar.HOUR_OF_DAY, hour); set(Calendar.MINUTE, minute); set(Calendar.SECOND, second); set(Calendar.MILLISECOND, 0)
        }.timeInMillis + offsetMs
        while (app.clock.nowMillis() < target) delay(100)
    }

    private fun prefs(app: PleoryApp) = app.getSharedPreferences("pleory_demo", Context.MODE_PRIVATE)

    /** 1일차 출발 시각. 2일차 시계(+1일 08:10)와 2일차 되감기가 쓴다. `pm clear` 로 함께 지워진다. */
    fun d1Base(app: PleoryApp): Long? = prefs(app).getLong(KEY_D1_BASE, 0L).takeIf { it > 0 }

    private fun saveD1Base(app: PleoryApp, base: Long) = prefs(app).edit().putLong(KEY_D1_BASE, base).apply()

    private const val KEY_D1_BASE = "d1Base"

    /** tools/demo-route.gpx 의 시작점(회사) = 2일차 끝점. */
    private val WORK = GeoPoint(37.5133, 127.1)

    /** tools/demo-route.gpx 의 끝점(집) = 2일차(demo-route-day2.gpx) 시작점. */
    private val HOME = GeoPoint(37.54, 126.84)
    private const val START_RADIUS_M = 300.0

    val FAKE_HOME = DestinationState.Known(HOME.lat, HOME.lng, "집")
    private val FAKE_WORK = DestinationState.Known(WORK.lat, WORK.lng, "회사")
}
