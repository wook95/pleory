package ai.pleos.hello

import ai.pleos.hello.app.LiveContextProvider
import ai.pleos.hello.app.PcmInjectableSpeechInput
import ai.pleos.hello.app.SessionController
import ai.pleos.hello.app.SessionOutputs
import ai.pleos.hello.core.Clock
import ai.pleos.hello.core.DemoClock
import ai.pleos.hello.core.SessionEvent
import ai.pleos.hello.core.SystemClock
import ai.pleos.hello.data.PleoryDb
import ai.pleos.hello.data.RoomPleoryStore
import ai.pleos.hello.data.Segment
import ai.pleos.hello.data.Session
import ai.pleos.hello.platform.GeminiStructurer
import ai.pleos.hello.platform.SdkHub
import ai.pleos.hello.platform.Gear
import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
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
    lateinit var speechInput: PcmInjectableSpeechInput
        private set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        db = PleoryDb.get(this)
        sdkHub = SdkHub(this).also { it.start() }
        assemble()
        appScope.launch { dbSelfCheck() }
    }

    private fun assemble() {
        speechInput = PcmInjectableSpeechInput(sdkHub.speechInput)
        controller = SessionController(
            store = RoomPleoryStore(db),
            // 공모전은 Gemini. 플랫폼 LLM(Gleo AI)이 서드파티에 열리면 PleosLlmStructurer로 전환 (TRD D6)
            structurer = GeminiStructurer(),
            speechIn = speechInput,
            speechOut = sdkHub.speechOutput,
            outputs = LoggingOutputs(),
            context = LiveContextProvider(sdkHub.location, clock),
            clock = clock,
            dispatcher = sdkHub.handler.asCoroutineDispatcher(), // SdkHub 직렬 경로(메인 루퍼)와 같은 스레드
            trace = { Log.i(FLOW_TAG, it) },
        )
        sdkHub.speechOutput.onDone = { Log.i(FLOW_TAG, "tts onDone") }
        sdkHub.gear.addListener { g -> gearEvent(g) }
        sdkHub.gear.current { g -> if (g != null) gearEvent(g) }
        // 시동 = 앱 세션 시작(D11). 이전 PENDING 발화는 새 세션 뒤에 재처리한다.
        controller.post(SessionEvent.IgnitionOn)
        controller.reprocessPending()
        startLocationIfPermitted()
    }

    private fun gearEvent(g: Gear) {
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

    /** T8 에서 S1 카드·요약 재생으로 교체한다. 지금은 로그만 남기고 요약은 즉시 끝난 것으로 본다. */
    private inner class LoggingOutputs : SessionOutputs {
        override fun showS0() { Log.i(FLOW_TAG, "output showS0") }
        override fun showCard() { Log.i(FLOW_TAG, "output showCard") }
        override fun startSummary() {
            Log.i(FLOW_TAG, "output startSummary")
            controller.post(SessionEvent.SummaryDone)
        }
        override fun stopSummary() { Log.i(FLOW_TAG, "output stopSummary") }
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
        val LOCATION_PERMISSIONS = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            "pleos.car.permission.FUSED_LOCATION",
        )
    }
}
