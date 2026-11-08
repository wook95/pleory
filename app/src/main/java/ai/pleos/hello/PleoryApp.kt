package ai.pleos.hello

import ai.pleos.hello.app.LiveContextProvider
import ai.pleos.hello.app.PcmInjectableSpeechInput
import ai.pleos.hello.app.SessionController
import ai.pleos.hello.app.SessionOutputs
import ai.pleos.hello.core.Clock
import ai.pleos.hello.core.DemoClock
import ai.pleos.hello.core.DestinationState as CoreDestination
import ai.pleos.hello.core.GeoPoint
import ai.pleos.hello.core.SessionEvent
import ai.pleos.hello.core.SessionState
import ai.pleos.hello.core.SystemClock
import ai.pleos.hello.core.TrackMath
import ai.pleos.hello.data.PleoryDb
import ai.pleos.hello.data.RoomPleoryStore
import ai.pleos.hello.data.Segment
import ai.pleos.hello.data.Session
import ai.pleos.hello.platform.GeminiStructurer
import ai.pleos.hello.platform.DestinationState
import ai.pleos.hello.platform.Gear
import ai.pleos.hello.platform.NaviListener
import ai.pleos.hello.platform.SdkHub
import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
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
    val clock = AppClock()
    val outputs = AppOutputs()
    lateinit var speechInput: PcmInjectableSpeechInput
        private set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        db = PleoryDb.get(this)
        sdkHub = SdkHub(this).also { it.start() }
        assemble()
        if (BuildConfig.DEBUG) appScope.launch { dbSelfCheck() }
    }

    private fun assemble() {
        speechInput = PcmInjectableSpeechInput(sdkHub.speechInput)
        controller = SessionController(
            store = RoomPleoryStore(db),
            // 공모전은 Gemini. 플랫폼 LLM(Gleo AI)이 서드파티에 열리면 PleosLlmStructurer로 전환 (TRD D6)
            structurer = GeminiStructurer(),
            speechIn = speechInput,
            speechOut = sdkHub.speechOutput,
            outputs = outputs,
            context = LiveContextProvider(sdkHub.location, clock, sdkHub.navi),
            clock = clock,
            dispatcher = sdkHub.handler.asCoroutineDispatcher(), // SdkHub 직렬 경로(메인 루퍼)와 같은 스레드
            trace = { Log.i(FLOW_TAG, it) },
            logError = { msg, t -> Log.e(FLOW_TAG, msg, t) },
        )
        sdkHub.gear.addListener { g -> gearEvent(g) }
        sdkHub.gear.current { g -> if (g != null) gearEvent(g) }
        // 시동 = 앱 세션 시작(D11). 이전 PENDING 발화는 새 세션 뒤에 재처리한다.
        controller.post(SessionEvent.IgnitionOn)
        wireArrival()
        controller.reprocessPending()
        startLocationIfPermitted()
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
            }

            override fun onArrived() {
                Log.i(FLOW_TAG, "navi arrived")
                controller.post(SessionEvent.Arrived)
            }
        })
        controller.post(SessionEvent.DestinationChanged(sdkHub.navi.destination.toCore()))
        sdkHub.location.addListener { fix ->
            if (controller.state == SessionState.CARD) return@addListener
            val dest = sdkHub.navi.destination as? DestinationState.Known ?: return@addListener
            if (TrackMath.distanceMeters(GeoPoint(fix.lat, fix.lng), GeoPoint(dest.lat, dest.lng)) <= ARRIVAL_RADIUS_M) {
                controller.post(SessionEvent.LocationNearDest)
            }
        }
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
            Gear.OTHER -> return
        }
        controller.post(SessionEvent.GearChanged(core))
    }

    /** 위치 권한이 있으면 위치 구독을 시작한다. 권한 응답 뒤에도 호출한다. */
    fun startLocationIfPermitted() {
        val ok = LOCATION_PERMISSIONS.all { checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }
        if (ok) sdkHub.handler.post { sdkHub.location.start() }
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
        val LOCATION_PERMISSIONS = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            "pleos.car.permission.FUSED_LOCATION",
        )
    }
}
