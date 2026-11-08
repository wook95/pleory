package ai.pleos.hello.debugpanel

import ai.pleos.hello.PleoryApp
import ai.pleos.hello.platform.DestinationState
import ai.pleos.hello.platform.Gear
import ai.pleos.hello.platform.LocationFix
import ai.pleos.hello.platform.NaviListener
import ai.pleos.hello.core.SessionEvent
import ai.pleos.hello.MainActivity
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * platform/ 소스 확인용 디버그 패널.
 * adb shell am start -n com.pplab.pleory/ai.pleos.hello.debugpanel.DebugPanelActivity ; adb logcat -s PleoryPlatform:I
 */
class DebugPanelActivity : Activity() {

    private lateinit var out: TextView
    private val app get() = application as PleoryApp
    private val hub get() = app.sdkHub
    private val points = ArrayDeque<LocationFix>()

    private val gearListener: (Gear) -> Unit = { log("gear event $it") }
    private val uxListener: (Boolean) -> Unit = { log("ux restrictions required=$it") }
    private val locationListener: (LocationFix) -> Unit = {
        points.addLast(it)
        if (points.size > MAX_POINTS) points.removeFirst()
        log("location #${points.size} lat=${it.lat} lng=${it.lng} t=${it.timeMillis}")
    }
    private val naviListener = object : NaviListener {
        override fun onDestination(state: DestinationState) = log("navi destination $state")
        override fun onArrived() = log("navi ARRIVED")
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        out = TextView(this).apply { textSize = 16f }
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 24, 24, 24) }
        listOf(
            "SDK 상태" to ::sdkStates,
            "기어 현재" to { hub.gear.current { log("gear current $it") } },
            "가짜 기어 P" to { hub.setGearOverride(Gear.P) },
            "가짜 기어 D" to { hub.setGearOverride(Gear.D) },
            "가짜 기어 해제" to { hub.setGearOverride(null) },
            "내비 refresh" to { hub.navi.refresh(); log("navi destination now ${hub.navi.destination}") },
            "위치 start" to { hub.location.start(); log("location start") },
            "위치 stop" to { hub.location.stop(); log("location stop, last=${points.toList()}") },
            "탭(TalkTapped, 마이크)" to { app.controller.post(SessionEvent.TalkTapped); log("TalkTapped") },
            "STT PCM 주입(파이프라인)" to ::sttFromPcm,
            "DemoClock ON ${DEMO_SPEED.toInt()}x (18:40 시작)" to { app.clock.setDemo(DEMO_SPEED); log("DemoClock ON ${DEMO_SPEED}x now=${app.clock.nowMillis()}") },
            "DemoClock OFF" to { app.clock.setDemo(null); log("DemoClock OFF now=${app.clock.nowMillis()}") },
            "목적지 집 설정(가짜 Known)" to { hub.setNaviOverride(DemoScenario.FAKE_HOME); log("fake destination ${DemoScenario.FAKE_HOME}") },
            "도착 강제(Arrived)" to { hub.emitFakeArrived(); log("fake Arrived") },
            "텍스트 발화 주입 1" to { textUtterance(0) },
            "텍스트 발화 주입 2" to { textUtterance(1) },
            "데모 시나리오 무장(PCM, 경로 재생 대기)" to { DemoScenario.arm(app, viaText = false); log("demo armed pcm") },
            "데모 시나리오 무장(텍스트, STT 우회)" to { DemoScenario.arm(app, viaText = true); log("demo armed text") },
            "데모 초기화(가짜 입력·DemoClock 해제)" to { DemoScenario.reset(app); log("demo reset") },
            "지난 기록 시드(지난 세션 6건, S2 확인용)" to { seedHistory() },
            "TTS 적어뒀어" to { hub.speechOutput.say("적어뒀어"); log("tts say") },
            "UX 제한 강제 ON (디버그 오버라이드)" to { hub.setUxOverride(true); log("ux override ON") },
            "UX 제한 강제 OFF (디버그 오버라이드)" to { hub.setUxOverride(false); log("ux override OFF") },
            "UX 제한 오버라이드 해제" to { hub.setUxOverride(null); log("ux override cleared") },
            "UX 제한 상태" to { log("ux required=${hub.uxRestrictions.isDistractionOptimizationRequired}") },
        ).forEach { (label, action) ->
            col.addView(Button(this).apply {
                text = label
                textSize = 16f
                setOnClickListener { runCatching { action() }.onFailure { log("$label THREW $it") } }
            })
        }
        col.addView(out)
        setContentView(ScrollView(this).apply { addView(col) })

        handleIntent()
        hub.gear.addListener(gearListener)
        hub.uxRestrictions.addListener(uxListener)
        hub.location.addListener(locationListener)
        hub.navi.addListener(naviListener)
    }

    private fun textUtterance(i: Int) {
        app.speechInput.pendingText = TEXTS[i]
        app.controller.post(SessionEvent.TalkTapped)
        log("text utterance ${i + 1} queued, TalkTapped")
    }

    /** Room 쿼리는 메인 스레드에서 못 돌리므로 IO 에서. */
    private fun seedHistory(set: String = "test", onDone: () -> Unit = {}) {
        CoroutineScope(Dispatchers.IO).launch {
            runCatching { HistorySeed.seed(app, set) }.onFailure { Log.e(TAG, "history seed failed", it) }
            runOnUiThread(onDone)
        }
        log("history seed queued")
    }

    private fun sdkStates() = log(
        "sdk vehicle=${hub.vehicleState.value} ux=${hub.uxRestrictionsState.value} navi=${hub.naviState.value} " +
            "location=${hub.locationState.value} stt=${hub.sttState.value} tts=${hub.ttsState.value}",
    )

    /** 다음 StartStt 가 마이크 대신 PCM 을 쓰게 예약하고, 일반 탭과 같은 경로(TalkTapped)로 시작한다. */
    private fun sttFromPcm() {
        val f = File(filesDir, "u.pcm")
        app.speechInput.pendingPcm = f.readBytes()
        app.controller.post(SessionEvent.TalkTapped)
        log("stt pcm queued ${f.length()}B, TalkTapped")
    }

    /** tools/run-demo.sh 용: `--es action demo [--es mode text] [--ef speed 30]` 이면 시나리오를 무장하고 메인 화면으로 넘어간다. */
    private fun handleIntent() {
        if (intent.getStringExtra("action") == "gear") { // adb 용 가짜 기어: --es action gear --es gear P|D|none
            hub.setGearOverride(when (intent.getStringExtra("gear")) { "P" -> Gear.P; "D" -> Gear.D; else -> null })
            finish()
            return
        }
        if (intent.getStringExtra("action") == "ux") { // adb 용 UX 제한 강제: --es action ux --es ux on|off|none
            hub.setUxOverride(when (intent.getStringExtra("ux")) { "on" -> true; "off" -> false; else -> null })
            finish()
            return
        }
        if (intent.getStringExtra("action") == "seed-history") { // adb 용: --es action seed-history [--es set film]
            seedHistory(intent.getStringExtra("set") ?: "test") { finish() } // 다 넣은 뒤 닫는다 — 먼저 닫으면 태스크와 함께 프로세스가 내려가 중간에 끊긴다
            return
        }
        if (intent.getStringExtra("action") != "demo") return
        val viaText = intent.getStringExtra("mode") == "text"
        val speed = intent.getFloatExtra("speed", DemoScenario.DEFAULT_SPEED.toFloat()).toDouble()
        DemoScenario.arm(app, viaText, speed)
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    private fun log(msg: String) {
        Log.i(TAG, msg)
        runOnUiThread { out.append("$msg\n") }
    }

    override fun onDestroy() {
        hub.gear.removeListener(gearListener)
        hub.uxRestrictions.removeListener(uxListener)
        hub.location.removeListener(locationListener)
        hub.navi.removeListener(naviListener)
        super.onDestroy()
    }

    private companion object {
        const val TAG = "PleoryPlatform"
        const val MAX_POINTS = 5
        const val DEMO_SPEED = 25.0
        val TEXTS = listOf("오늘 팀장 때문에 진짜 힘들었다", "내일 거래처 서류 다시 보내야 해 아 노을 예쁘다")
    }
}
