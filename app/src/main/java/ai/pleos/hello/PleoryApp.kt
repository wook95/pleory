package ai.pleos.hello

import ai.pleos.hello.app.LiveContextProvider
import ai.pleos.hello.app.PcmInjectableSpeechInput
import ai.pleos.hello.app.SessionController
import ai.pleos.hello.app.SessionOutputs
import ai.pleos.hello.core.Clock
import ai.pleos.hello.core.ConsentGatedStructurer
import ai.pleos.hello.core.FallbackStructurer
import ai.pleos.hello.core.ScreenGate
import ai.pleos.hello.core.DemoClock
import ai.pleos.hello.core.DestinationState as CoreDestination
import ai.pleos.hello.core.GeoPoint
import ai.pleos.hello.core.SessionEvent
import ai.pleos.hello.core.SessionState
import ai.pleos.hello.core.SystemClock
import ai.pleos.hello.core.TrackMath
import ai.pleos.hello.core.TrackSample
import ai.pleos.hello.data.ConsentPrefs
import ai.pleos.hello.data.PleoryDb
import ai.pleos.hello.data.RoomPleoryStore
import ai.pleos.hello.data.Segment
import ai.pleos.hello.data.Session
import ai.pleos.hello.platform.ClaudeStructurer
import ai.pleos.hello.platform.GeminiStructurer
import ai.pleos.hello.platform.DestinationState
import ai.pleos.hello.platform.Gear
import ai.pleos.hello.platform.NaviListener
import ai.pleos.hello.platform.ParkedFallback
import ai.pleos.hello.platform.SdkHub
import android.Manifest
import android.app.Activity
import android.app.Application
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.util.Calendar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.android.asCoroutineDispatcher
import kotlinx.coroutines.launch

/** 앱 전체가 읽는 시계. 평소엔 시스템 시계, 디버그 패널에서 DemoClock 으로 갈아 끼운다(D7). */
class AppClock : Clock {
    @Volatile
    private var delegate: Clock = SystemClock()

    @Volatile
    var demoSpeed: Double? = null
        private set

    override fun nowMillis() = delegate.nowMillis()

    /** [speed] 배속 DemoClock 을 오늘 18:40(로컬)에서 출발시킨다. null 이면 시스템 시계로 복귀. */
    fun setDemo(speed: Double?) {
        demoSpeed = speed
        delegate = if (speed == null) SystemClock() else DemoClock(todayAt(18, 40), speed)
    }

    private fun todayAt(hour: Int, minute: Int) = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, hour); set(Calendar.MINUTE, minute); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis
}

/** 수동 DI 보관소. SdkHub·DB·파이프라인을 앱 범위로 조립한다. */
class PleoryApp : Application() {

    lateinit var sdkHub: SdkHub
        private set
    lateinit var db: PleoryDb
        private set

    lateinit var controller: SessionController
        private set
    lateinit var consent: ConsentPrefs
        private set
    private lateinit var liveContext: LiveContextProvider

    /** 이번 세션의 구간(출근길·퇴근길·외출). S0 상태 텍스트가 쓴다. */
    val sessionSegment: Segment get() = liveContext.sessionSegment

    /** 마지막으로 받은 기어. 모르면(OTHER·미수신) null — 화면 게이트는 null 을 닫힘으로 본다. */
    @Volatile
    var gear: ai.pleos.hello.core.Gear? = null
        private set

    /** 기어·UX 제한이 바뀔 때 메인 스레드에서 불린다(화면 게이트 재평가용). */
    @Volatile
    var onGateChanged: (() -> Unit)? = null
    val clock = AppClock()
    val outputs = AppOutputs()
    lateinit var speechInput: PcmInjectableSpeechInput
        private set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** 보이는(started) Activity 수. 0 이면 백그라운드다. 메인 스레드에서만 바뀐다. */
    @Volatile
    private var startedActivities = 0

    override fun onCreate() {
        super.onCreate()
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityStarted(activity: Activity) { startedActivities++ }
            override fun onActivityStopped(activity: Activity) { startedActivities-- }
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
            override fun onActivityResumed(activity: Activity) = Unit
            override fun onActivityPaused(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        })
        db = PleoryDb.get(this)
        consent = ConsentPrefs(this)
        sdkHub = SdkHub(this).also { it.start() }
        assemble()
        if (BuildConfig.DEBUG) appScope.launch { dbSelfCheck() }
    }

    private fun assemble() {
        liveContext = LiveContextProvider(sdkHub.location, clock, sdkHub.navi, foreground = { startedActivities > 0 })
        speechInput = PcmInjectableSpeechInput(sdkHub.speechInput)
        controller = SessionController(
            store = RoomPleoryStore(db),
            // 공모전은 Claude(claude-haiku-5-5) → 실패하면 Gemini → 그래도 실패하면 RAW, 전부 컨트롤러 8초 안(TRD D6).
            // 플랫폼 LLM(Gleo AI)이 서드파티에 열리면 PleosLlmStructurer로 전환
            // 동의가 없거나 철회됐으면 외부 LLM 을 부르지 않고 RAW 로 남긴다(TRD §8-1 1번)
            structurer = ConsentGatedStructurer({ consent.llmAllowed }, llmChain(), { Log.i(FLOW_TAG, it) }),
            speechIn = speechInput,
            speechOut = sdkHub.speechOutput,
            outputs = outputs,
            context = liveContext,
            clock = clock,
            dispatcher = sdkHub.handler.asCoroutineDispatcher(), // SdkHub 직렬 경로(메인 루퍼)와 같은 스레드
            trace = { Log.i(FLOW_TAG, it) },
            logError = { msg, t -> Log.e(FLOW_TAG, msg, t) },
        )
        sdkHub.uxRestrictions.addListener { required ->
            Log.i(FLOW_TAG, "ux required=$required active=${sdkHub.uxRestrictions.activeRestrictions} gate=${gateAllowed()}")
            onGateChanged?.invoke()
        }
        // 진짜 기어 이벤트가 오면 대체 판정은 끈다(둘이 섞여 P/D 가 뒤바뀌지 않게)
        sdkHub.gear.addListener { g -> stopGearFallback(); gearEvent(g) }
        sdkHub.gear.current { g ->
            if (g != null) gearEvent(g)
            else if (ParkedFallback.shouldStart(g, sdkHub.gear.supported)) startGearFallback()
        }
        // 시동 = 앱 세션 시작(D11). 이전 PENDING 발화는 새 세션 뒤에 재처리한다.
        controller.post(SessionEvent.IgnitionOn)
        wireArrival()
        controller.reprocessPending()
        startLocationIfPermitted()
    }

    /** Claude 키가 있으면 Claude 4.5초 → Gemini 나머지(약 3.4초), 없으면 Gemini 단독으로 8초. */
    private fun llmChain(): FallbackStructurer {
        val claude = ClaudeStructurer(timeoutMs = CLAUDE_BUDGET_MS)
        val gemini = GeminiStructurer(budgetMs = if (claude.available) GEMINI_AFTER_CLAUDE_MS else 8_000)
        return FallbackStructurer(
            listOf(
                FallbackStructurer.Step("claude", claude, timeoutMs = CLAUDE_BUDGET_MS, available = { claude.available }),
                FallbackStructurer.Step("gemini", gemini),
            ),
            trace = { Log.i(FLOW_TAG, it) },
        )
    }

    /**
     * 도착 판정 입력(TRD D9)을 상태 머신에 잇는다: 목적지 변경, 내비 도착 이벤트,
     * 알려진 목적지 300m 이내의 최신 위치, 기어 P 5초 유지는 gearEvent 에서.
     */
    private fun wireArrival() {
        sdkHub.navi.addListener(object : NaviListener {
            override fun onDestination(state: DestinationState) {
                Log.i(FLOW_TAG, "destination $state")
                controller.post(SessionEvent.DestinationChanged(state.toCore()))
                watchProximity(state)
            }

            override fun onArrived() {
                Log.i(FLOW_TAG, "navi arrived")
                controller.post(SessionEvent.Arrived)
            }
        })
        controller.post(SessionEvent.DestinationChanged(sdkHub.navi.destination.toCore()))
        watchProximity(sdkHub.navi.destination)
        sdkHub.location.addListener { fix ->
            if (controller.state == SessionState.CARD) return@addListener
            val dest = sdkHub.navi.destination as? DestinationState.Known ?: return@addListener
            if (TrackMath.distanceMeters(GeoPoint(fix.lat, fix.lng), GeoPoint(dest.lat, dest.lng)) <= ARRIVAL_RADIUS_M) {
                controller.post(SessionEvent.LocationNearDest)
            }
        }
    }

    /** 알려진 목적지 반경 진입을 FusedLocation 근접 알림으로도 받는다(자체 거리 계산과 병행, 머신은 멱등). */
    private fun watchProximity(state: DestinationState) {
        if (state is DestinationState.Known) {
            val dest = state.lat to state.lng
            if (dest == proximityDest) return // 같은 좌표면 알림을 다시 등록하지 않는다
            proximityDest = dest
            sdkHub.location.watchProximity(state.lat, state.lng, ARRIVAL_RADIUS_M.toFloat()) {
                if (controller.state != SessionState.CARD) {
                    Log.i(FLOW_TAG, "proximity enter")
                    controller.post(SessionEvent.LocationNearDest)
                }
            }
        } else {
            proximityDest = null
            sdkHub.location.clearProximity()
        }
    }

    private var proximityDest: Pair<Double, Double>? = null
    private var gearFallback: ParkedFallback? = null
    private var gearFallbackOn = false

    private fun stopGearFallback() {
        if (!gearFallbackOn) return
        gearFallbackOn = false
        Log.i(FLOW_TAG, "real gear event: fallback off")
    }

    /** 기어를 못 읽는 차량: 위치 속도와 UX 제한으로 P/D 를 만들어 같은 경로(gearEvent)로 보낸다. */
    private fun startGearFallback() {
        gearFallbackOn = true
        if (gearFallback != null) return
        val f = ParkedFallback().also { gearFallback = it }
        Log.w(FLOW_TAG, "gear unsupported: speed/UX fallback on")
        var prev: TrackSample? = null
        sdkHub.location.addListener { fix ->
            if (!gearFallbackOn) return@addListener
            val cur = TrackSample(fix.timeMillis, fix.lat, fix.lng)
            prev?.let { p -> f.onSpeed(cur.atMillis, TrackMath.speedKmh(p, cur))?.let(::gearEvent) }
            prev = cur
        }
        sdkHub.uxRestrictions.addListener { required -> if (gearFallbackOn) f.onUx(required)?.let(::gearEvent) }
        f.onUx(sdkHub.uxRestrictions.isDistractionOptimizationRequired)
    }

    private fun DestinationState.toCore(): CoreDestination = when (this) {
        DestinationState.Unknown -> CoreDestination.Unknown
        DestinationState.None -> CoreDestination.None
        is DestinationState.Known -> CoreDestination.Known
    }

    private val parkHandler = Handler(Looper.getMainLooper())
    private val parkHeld = Runnable {
        Log.i(FLOW_TAG, "gear P held 5s")
        controller.post(SessionEvent.ParkHeld5s)
    }
    private var parkScheduled = false

    /** 카드·설정·동의 등 S0 밖 화면을 보여도 되는가: 기어 P, DO 제한 없음, 설정·키보드·문자열 길이 제한 비활성. */
    fun gateAllowed() = ScreenGate.allowed(gear, sdkHub.uxRestrictions.isDistractionOptimizationRequired, sdkHub.uxRestrictions.activeRestrictions)

    private fun gearEvent(g: Gear) {
        if (g == Gear.P) {
            if (!parkScheduled) {
                parkScheduled = true
                parkHandler.postDelayed(parkHeld, PARK_HOLD_MS)
            }
        } else {
            parkScheduled = false
            parkHandler.removeCallbacks(parkHeld)
        }
        val core = when (g) {
            Gear.P -> ai.pleos.hello.core.Gear.P
            Gear.D -> ai.pleos.hello.core.Gear.D
            Gear.R -> ai.pleos.hello.core.Gear.R
            Gear.N -> ai.pleos.hello.core.Gear.N
            Gear.OTHER -> { this.gear = null; onGateChanged?.invoke(); return }
        }
        this.gear = core
        onGateChanged?.invoke()
        controller.post(SessionEvent.GearChanged(core))
    }

    /** 위치 권한이 있으면 위치 구독을 시작한다. 권한 응답 뒤에도 호출한다. */
    fun startLocationIfPermitted() {
        val ok = LOCATION_PERMISSIONS.all { checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }
        if (ok) sdkHub.handler.post { sdkHub.initLocation() }
    }

    /** 화면 쪽 알림. 화면 전환은 컨트롤러 상태 변화로 하고, 카드 갱신과 요약 자막만 여기서 전한다. */
    class AppOutputs : SessionOutputs {
        interface Ui {
            fun refreshCard()
            fun summaryStarted(text: String)
            fun summaryEnded()
        }

        @Volatile
        var ui: Ui? = null

        override fun showS0() { Log.i(FLOW_TAG, "output showS0") }
        override fun showCard() { Log.i(FLOW_TAG, "output showCard") }
        override fun startSummary() { Log.i(FLOW_TAG, "output startSummary") }
        override fun stopSummary() { Log.i(FLOW_TAG, "output stopSummary") }
        override fun refreshCard() { ui?.refreshCard() }
        override fun summaryStarted(text: String) { ui?.summaryStarted(text) }
        override fun summaryEnded() { ui?.summaryEnded() }
    }

    /** Session 한 줄을 넣고 다시 읽은 뒤 지운다. */
    private suspend fun dbSelfCheck() {
        try {
            val dao = db.sessions()
            val id = dao.insert(Session(profileId = "selfcheck", startedAt = 0L, segment = Segment.OUTING))
            check(dao.get(id)?.profileId == "selfcheck") { "readback mismatch" }
            dao.delete(id)
            Log.i("PleoryDb", "PleoryDb self-check ok id=$id")
        } catch (e: Exception) {
            Log.e("PleoryDb", "PleoryDb self-check failed: $e")
        }
    }

    companion object {
        const val FLOW_TAG = "PleoryFlow"
        private const val ARRIVAL_RADIUS_M = 300.0
        private const val PARK_HOLD_MS = 5_000L
        private const val CLAUDE_BUDGET_MS = 4_500L
        private const val GEMINI_AFTER_CLAUDE_MS = 3_400
        val LOCATION_PERMISSIONS = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            "pleos.car.permission.FUSED_LOCATION",
        )
    }
}
