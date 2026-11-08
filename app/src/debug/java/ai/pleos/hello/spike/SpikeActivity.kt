package ai.pleos.hello.spike

import ai.pleos.playground.llm.LLM
import ai.pleos.playground.navi.data.DestinationArrivedInfo
import ai.pleos.playground.navi.data.DestinationInfo
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import ai.pleos.playground.navi.constants.NaviErrorCode
import ai.pleos.playground.stt.SpeechToText
import ai.pleos.playground.stt.constant.ErrorType as SttError
import ai.pleos.playground.stt.listener.OnInitializeListener as SttInit
import ai.pleos.playground.stt.listener.ResultListener
import ai.pleos.playground.tts.TextToSpeech
import ai.pleos.playground.tts.constant.ErrorType as TtsError
import ai.pleos.playground.tts.listener.EventListener
import ai.pleos.playground.tts.listener.OnInitializeListener as TtsInit
import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.constant.control.VehicleGear
import ai.pleos.playground.vehicle.listener.GearSelectionListener
import android.app.Activity
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.util.Locale
import ai.pleos.playground.llm.constant.Mode as LlmMode
import ai.pleos.playground.llm.listener.ResultListener as LlmResult
import ai.pleos.playground.stt.constant.Mode as SttMode
import ai.pleos.playground.tts.constant.Mode as TtsMode

/**
 * 1일차 SDK 스파이크 (debug 전용). 기획 명세 §7 의 "(확인)" 항목을 에뮬레이터에서 직접 본다.
 * adb shell am start -n ai.pleos.hello/.spike.SpikeActivity ; adb logcat -s PleorySpike:I
 */
class SpikeActivity : Activity() {

    private lateinit var out: TextView
    private val vehicle by lazy { Vehicle(this) }
    private val navi by lazy { NaviHelper(this) }
    private var stt: SpeechToText? = null
    private var tts: TextToSpeech? = null
    private var llm: LLM? = null

    private val gearListener = object : GearSelectionListener {
        override fun onSelected(gear: VehicleGear?) = log("gear onSelected $gear")
        override fun onFailed(e: Exception) = log("gear listener fail $e")
    }

    private val naviListener = object : NaviHelperEventListener {
        override fun onDestinationInfo(destinationInfo: DestinationInfo) = log("navi destination $destinationInfo")
        override fun onDestinationArrived(destinationArrivedInfo: DestinationArrivedInfo) = log("navi ARRIVED $destinationArrivedInfo")
        override fun onDestinationChanged() = log("navi destinationChanged")
        override fun onRouteEnded() = log("navi routeEnded")
        override fun onError(errorCode: NaviErrorCode) = log("navi error $errorCode")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        out = TextView(this).apply { textSize = 18f }
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 24, 24, 24) }
        listOf(
            "STT ON_DEVICE" to { startStt(SttMode.ON_DEVICE) },
            "STT HYBRID" to { startStt(SttMode.HYBRID) },
            "STT SERVER (등록)" to { startStt(SttMode.SERVER) },
            "STT SERVER 파일(u.pcm)" to { startStt(SttMode.SERVER, fromFile = true) },
            "STT ON_DEVICE 파일(u.pcm)" to { startStt(SttMode.ON_DEVICE, fromFile = true) },
            "TTS ON_DEVICE 적어뒀어" to { speak(TtsMode.ON_DEVICE) },
            "TTS HYBRID 적어뒀어" to { speak(TtsMode.HYBRID) },
            "LLM ON_DEVICE" to { askLlm(LlmMode.ON_DEVICE) },
            "LLM SERVER" to { askLlm(LlmMode.SERVER) },
            "Navi 목적지 조회" to { navi.getDestinationInfo() },
            "기어·주행거리 조회" to { readVehicle() },
        ).forEach { (label, action) ->
            col.addView(Button(this).apply { text = label; textSize = 18f; setOnClickListener { runCatching { action() }.onFailure { log("$label THREW $it") } } })
        }
        col.addView(out)
        setContentView(ScrollView(this).apply { addView(col) })

        vehicle.initialize()
        vehicle.getGear().registerGearSelection(gearListener)
        navi.initialize({ log("navi init ok"); navi.addListener(naviListener) }, { log("navi init fail $it") })
        readVehicle()
    }

    private fun readVehicle() {
        vehicle.getGear().getCurrentGearSelectionState({ log("gear now $it") }, { log("gear fail $it") })
        vehicle.getOdometer().getCurrentDistanceDriven({ log("odometer $it") }, { log("odometer fail $it") })
        vehicle.getOdometer().getTripCurrentDrivingDistance({ log("trip distance $it") }, { log("trip distance fail $it") })
    }

    private fun startStt(mode: SttMode, fromFile: Boolean = false) {
        stt?.release()
        val s = SpeechToText(this, mode).also { stt = it }
        s.addListener(object : ResultListener {
            override fun onUpdated(text: String, isFinal: Boolean) = log("stt[$mode] ${if (isFinal) "FINAL" else "partial"} '$text'")
            override fun onUpdatedEpdData(a: Long, b: Long) = Unit
            override fun onStartedRecognition() = log("stt[$mode] started")
            override fun onEndedRecognition() = log("stt[$mode] ended")
            override fun onError(errorType: SttError) = log("stt[$mode] error $errorType")
            override fun onReady() = log("stt[$mode] ready")
            override fun onUpdatedRms(rms: Double) = Unit
        })
        s.initialize(object : SttInit {
            override fun onSuccess() {
                log("stt[$mode] init ok")
                s.setLocale(Locale.KOREA)
                if (mode != SttMode.ON_DEVICE) {
                    s.registerApp(ai.pleos.hello.BuildConfig.PLEOS_CLIENT_ID, ai.pleos.hello.BuildConfig.PLEOS_CLIENT_SECRET,
                        object : ai.pleos.playground.stt.listener.OnServerConnectionListener {
                            override fun onConnected() { log("stt[$mode] registerApp connected"); feed(s, mode, fromFile) }
                            override fun onFailed(errorType: SttError) = log("stt[$mode] registerApp FAIL $errorType")
                        })
                    return
                }
                feed(s, mode, fromFile)
            }
            override fun onFailure(errorType: SttError) = log("stt[$mode] init fail $errorType")
        })
    }

    private fun feed(s: SpeechToText, mode: SttMode, fromFile: Boolean) {
        if (!fromFile) { s.request(); return }
        // 마이크 대신 PCM(16kHz·16bit·mono). 문서상 sendAudio 는 ON_DEVICE 전용.
        // adb push u.pcm /data/local/tmp/ && adb shell run-as ai.pleos.hello --user 10 cp /data/local/tmp/u.pcm files/
        val pcm = java.io.File(filesDir, "u.pcm").readBytes()
        Thread {
            pcm.asList().chunked(3_200).forEach { s.sendAudio(it.toByteArray()); Thread.sleep(100) }
            s.completeAudioSend()
            log("stt[$mode] file sent ${pcm.size}B")
        }.start()
    }

    private fun speak(mode: TtsMode) {
        tts?.release()
        val t = TextToSpeech(this, mode).also { tts = it }
        t.initialize(object : TtsInit {
            override fun onSuccess() {
                log("tts[$mode] init ok")
                t.addEventListener(object : EventListener {
                    override fun onStart() = log("tts[$mode] start")
                    override fun onReady() = log("tts[$mode] ready")
                    override fun onDone() = log("tts[$mode] done")
                    override fun onError(errorType: TtsError) = log("tts[$mode] error $errorType")
                    override fun onStop() = log("tts[$mode] stop")
                    override fun onUpdatedRms(rms: Double) = Unit
                })
                t.setLocale(Locale.KOREA)
                t.speak("적어뒀어", true)
            }
            override fun onFailure(errorType: TtsError) = log("tts[$mode] init fail $errorType")
        })
    }

    private fun askLlm(mode: LlmMode) {
        llm?.release()
        val l = LLM(this, mode).also { llm = it }
        l.initialize()
        val ask = {
            val t0 = System.currentTimeMillis()
            log("llm[$mode] request")
            l.generateContent(PROMPT + "\n\nuser: \"내일 거래처 서류 다시 보내야 해, 아 노을 예쁘다\"", object : LlmResult {
                override fun onResponse(text: String, isDone: Boolean) =
                    log("llm[$mode] ${if (isDone) "DONE" else "chunk"} +${System.currentTimeMillis() - t0}ms $text")
                override fun onError(message: String) = log("llm[$mode] error $message")
            })
        }
        if (mode == LlmMode.ON_DEVICE) { out.postDelayed(ask, 1500); return }
        out.postDelayed({
            l.registerApp(ai.pleos.hello.BuildConfig.PLEOS_CLIENT_ID, ai.pleos.hello.BuildConfig.PLEOS_CLIENT_SECRET,
                object : ai.pleos.playground.llm.listener.OnServerConnectionListener {
                    override fun onConnected() { log("llm[$mode] registerApp connected"); ask() }
                    override fun onFailed(message: String) = log("llm[$mode] registerApp FAIL $message")
                })
        }, 1500)
    }

    private fun log(msg: String) {
        Log.i(TAG, msg)
        runOnUiThread { out.append("$msg\n") }
    }

    override fun onDestroy() {
        vehicle.getGear().unregisterGearSelection(gearListener)
        vehicle.release()
        navi.removeListener(naviListener)
        navi.release()
        stt?.release(); tts?.release(); llm?.release()
        super.onDestroy()
    }

    private companion object {
        const val TAG = "PleorySpike"
        // PRD §4-1 구조화 계약 그대로
        const val PROMPT = """당신은 운전자가 운전 중에 떠오르는 대로 말한 한마디를 정리한다.
규칙:
1. 여러 생각이 섞여 있으면 항목으로 나눈다. 한 항목은 한 문장. 최대 5개.
2. 각 항목의 type은 thought(생각) / todo(할 일) / emotion(감정) / person(사람) 중 하나.
3. emotion은 down / neutral / up 중 하나. 할 일은 보통 neutral.
4. 말투는 다듬되 뜻을 바꾸지 않는다. 없는 내용을 추가하지 않는다.
5. 정리할 수 없으면 원문을 그대로 1개 항목(type=raw, emotion=neutral, keywords=[])으로 돌려준다.
JSON만 출력한다. 코드펜스·설명 금지. 형식: {"items":[{"text":"","type":"","emotion":"","keywords":[]}]}"""
    }
}
