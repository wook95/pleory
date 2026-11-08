package ai.pleos.hello.debugpanel

import ai.pleos.hello.PleoryApp
import ai.pleos.hello.core.GeoPoint
import ai.pleos.hello.core.SessionEvent
import ai.pleos.hello.core.TrackMath
import ai.pleos.hello.platform.DestinationState
import ai.pleos.hello.platform.Gear
import ai.pleos.hello.platform.LocationFix
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
 * PRD §8 1~6단계를 앱 안에서 재현하는 디버그 시나리오(T8).
 *
 * 무장(arm) 상태에서 첫 위치 fix 가 오면 그 순간을 18:40 로 삼아 DemoClock 을 켜고 새 세션을 연다.
 * 그래서 호스트의 경로 재생(tools/play-route.sh)이 시작되는 시점과 앱의 시계가 어긋나지 않는다.
 * 이후 DemoClock 시각에 맞춰 한마디 2건을 실제 파이프라인(STT 또는 텍스트 → Gemini → Room)으로 넣고,
 * 19:31 에 가짜 기어 P 를 넣는다. 도착은 마지막 위치 fix 의 300m 반경 판정이 맡는다.
 */
object DemoScenario {
    const val TAG = "PleoryDemo"
    const val DEFAULT_SPEED = 30.0
    private val TEXTS = listOf("오늘 팀장 때문에 진짜 힘들었다", "내일 거래처 서류 다시 보내야 해 아 노을 예쁘다")

    /** 한마디 저장 시각 목표(시, 분, 초). 표시는 HH:mm 이라 분 안쪽 중간에 맞춘다. */
    private val UTTERANCE_AT = listOf(Triple(18, 57, 20), Triple(19, 8, 20))

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var job: Job? = null
    private var armed: ((LocationFix) -> Unit)? = null

    /** [viaText] 이면 STT 를 건너뛰고 텍스트를 인식 결과로 넣는다(Gemini 이후는 동일). */
    fun arm(app: PleoryApp, viaText: Boolean, speed: Double = DEFAULT_SPEED) {
        cancel(app)
        val hub = app.sdkHub
        lateinit var listener: (LocationFix) -> Unit
        listener = { fix ->
            // 이전 실행이 남긴 에뮬레이터의 마지막 위치(반복 송출됨)에는 반응하지 않는다. 경로 출발점 근처의 fix 만 받는다.
            if (TrackMath.distanceMeters(GeoPoint(fix.lat, fix.lng), ROUTE_START) <= START_RADIUS_M) {
                hub.location.removeListener(listener)
                armed = null
                job = scope.launch { run(app, viaText, speed) }
            }
        }
        armed = listener
        hub.location.addListener(listener)
        // 목적지 없음 + 기어 P 5초 규칙(D9)으로 대기 중에 카드가 뜨지 않도록, 무장하자마자 목적지를 가짜로 정해 둔다.
        hub.setNaviOverride(FAKE_HOME)
        app.startLocationIfPermitted()
        Log.i(TAG, "demo armed viaText=$viaText speed=${speed}x; waiting for first location fix")
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

    private suspend fun run(app: PleoryApp, viaText: Boolean, speed: Double) {
        val hub = app.sdkHub
        app.clock.setDemo(speed)
        Log.i(TAG, "demo start: DemoClock ${speed}x at ${app.clock.nowMillis()}")
        app.controller.post(SessionEvent.IgnitionOff)
        app.controller.post(SessionEvent.IgnitionOn)
        hub.setNaviOverride(FAKE_HOME)
        hub.setGearOverride(Gear.D)

        TEXTS.forEachIndexed { i, text ->
            val pcm = if (viaText) null else File(app.filesDir, "u${i + 1}.pcm").readBytes()
            // 저장 시각이 목표에 닿도록 앞당겨서 시작한다: PCM 은 입력 길이 + 인식 지연 약 0.25초(실측), 텍스트는 0.3초(주입 지연).
            val leadMs = (if (pcm != null) pcm.size / 32_000.0 + 0.25 else 0.3) * 1000 * speed
            val (h, m, s) = UTTERANCE_AT[i]
            waitUntil(app, h, m, s, -leadMs.toLong())
            if (pcm != null) app.speechInput.pendingPcm = pcm else app.speechInput.pendingText = text
            Log.i(TAG, "inject utterance ${i + 1} (${if (viaText) "text" else "pcm"}) at ${app.clock.nowMillis()}")
            app.controller.post(SessionEvent.TalkTapped)
        }

        waitUntil(app, 19, 31, 0, 0)
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

    /** tools/demo-route.gpx 의 시작점(회사). */
    private val ROUTE_START = GeoPoint(37.5133, 127.1)
    private const val START_RADIUS_M = 300.0

    /** tools/demo-route.gpx 의 끝점(집). */
    val FAKE_HOME = DestinationState.Known(37.54, 126.84, "집")
}
