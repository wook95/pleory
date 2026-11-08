package ai.pleos.hello.debugpanel

import ai.pleos.hello.PleoryApp
import ai.pleos.hello.platform.DestinationState
import ai.pleos.hello.platform.Gear
import ai.pleos.hello.platform.LocationFix
import ai.pleos.hello.platform.NaviListener
import ai.pleos.hello.core.SessionEvent
import android.app.Activity
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.io.File

/**
 * platform/ 소스 확인용 디버그 패널.
 * adb shell am start -n ai.pleos.hello/.debugpanel.DebugPanelActivity ; adb logcat -s PleoryPlatform:I
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
            "TTS 적어뒀어" to { hub.speechOutput.say("적어뒀어"); log("tts say") },
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

        hub.gear.addListener(gearListener)
        hub.uxRestrictions.addListener(uxListener)
        hub.location.addListener(locationListener)
        hub.navi.addListener(naviListener)
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
    }
}
