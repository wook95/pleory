package ai.pleos.hello.platform

import ai.pleos.playground.stt.SpeechToText
import ai.pleos.playground.stt.constant.ErrorType as SttError
import ai.pleos.playground.stt.constant.Mode as SttMode
import ai.pleos.playground.stt.listener.OnInitializeListener as SttInit
import ai.pleos.playground.stt.listener.ResultListener
import ai.pleos.playground.tts.TextToSpeech
import ai.pleos.playground.tts.constant.ErrorType as TtsError
import ai.pleos.playground.tts.constant.Mode as TtsMode
import ai.pleos.playground.tts.listener.EventListener
import ai.pleos.playground.tts.listener.OnInitializeListener as TtsInit
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.util.Locale

interface SpeechInputListener {
    fun onPartial(text: String, round: Int) {}
    fun onFinal(text: String, round: Int) {}
    fun onEnded(round: Int) {}
    fun onError(message: String, round: Int) {}
}

interface SpeechInput {
    var listener: SpeechInputListener?
    fun start(round: Int)

    /** 디버그 주입용. 16kHz·16bit·mono PCM. */
    fun startFromPcm(bytes: ByteArray, round: Int)
    fun cancel()
}

interface SpeechOutput {
    var onDone: (() -> Unit)?
    fun say(text: String)
    fun stop()
}

/** STT 는 ON_DEVICE 고정(D5). 마이크 음성이 차 밖으로 나가지 않게 한다. */
class PleosSpeechInput(context: Context, private val post: Post) : SpeechInput {
    private val stt = SpeechToText(context, SttMode.ON_DEVICE)
    private var ready = false
    /**
     * 진행 중인 STT 회차. 바인더 스레드 콜백이 읽으므로 volatile. final 전달·cancel·오류 뒤에는 -1 이라 늦게 온 결과는 버린다.
     * SDK 는 onEndedRecognition 을 final 보다 먼저 보낸다(에뮬레이터 실측) — 그래서 Ended 에서는 회차를 끄지 않는다.
     */
    @Volatile
    private var activeRound = -1
    private var pcmThread: Thread? = null

    override var listener: SpeechInputListener? = null

    private val sdkListener = object : ResultListener {
        override fun onUpdated(text: String, isFinal: Boolean) {
            val r = activeRound
            if (r < 0) return
            if (isFinal) activeRound = -1
            post { if (isFinal) listener?.onFinal(text, r) else listener?.onPartial(text, r) }
        }

        override fun onUpdatedEpdData(a: Long, b: Long) = Unit
        override fun onStartedRecognition() = Unit
        override fun onEndedRecognition() {
            val r = activeRound
            if (r < 0) return
            post { listener?.onEnded(r) }
        }

        override fun onError(errorType: SttError) {
            val r = activeRound
            if (r < 0) return
            activeRound = -1
            post { listener?.onError(errorType.toString(), r) }
        }

        override fun onReady() = Unit
        override fun onUpdatedRms(rms: Double) = Unit
    }

    fun init(onState: (SdkState) -> Unit) {
        stt.addListener(sdkListener)
        stt.initialize(object : SttInit {
            override fun onSuccess() {
                stt.setLocale(Locale.KOREA)
                post { ready = true; onState(SdkState.READY) }
            }

            override fun onFailure(errorType: SttError) {
                Log.w(TAG, "stt init failed: $errorType")
                post { onState(SdkState.FAILED) }
            }
        })
    }

    fun release() {
        cancel()
        ready = false
        runCatching { stt.removeListener(sdkListener) }
        runCatching { stt.release() }
    }

    override fun start(round: Int) {
        if (!notReady(round)) {
            activeRound = round
            stt.request()
        }
    }

    override fun startFromPcm(bytes: ByteArray, round: Int) {
        if (notReady(round)) return
        activeRound = round
        pcmThread?.interrupt()
        pcmThread = Thread {
            try {
                bytes.asList().chunked(PCM_CHUNK).forEach {
                    stt.sendAudio(it.toByteArray())
                    Thread.sleep(100)
                }
                stt.completeAudioSend()
            } catch (_: InterruptedException) {
            }
        }.also { it.start() }
    }

    override fun cancel() {
        activeRound = -1
        pcmThread?.interrupt()
        pcmThread = null
        if (ready) runCatching { stt.stop() }
    }

    private fun notReady(round: Int): Boolean {
        if (ready) return false
        post { listener?.onError("stt not ready", round) }
        return true
    }

    private companion object {
        const val PCM_CHUNK = 3_200
    }
}

/** TTS 도 ON_DEVICE 고정(D5). 이벤트 리스너는 initialize 성공 뒤에 등록한다(D10). */
class PleosSpeechOutput(context: Context, private val post: Post) : SpeechOutput {
    private val tts = TextToSpeech(context, TtsMode.ON_DEVICE)
    private val audioManager = context.getSystemService(AudioManager::class.java)
    private val attrs = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANT)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()
    private var ready = false

    /** 발화 중 잡고 있는 포커스 요청. 직렬 경로에서만 읽고 쓴다. */
    private var focusRequest: AudioFocusRequest? = null

    /** 포커스 요청마다 올리는 번호. 이미 놓은 요청의 늦은 포커스 변경 콜백을 거른다. */
    private var focusSeq = 0
    private val utterance = UtteranceTracker()

    override var onDone: (() -> Unit)? = null

    private val sdkListener = object : EventListener {
        override fun onStart() = Unit
        override fun onReady() = Unit
        override fun onDone() {
            post { if (utterance.onEnd()) { abandonFocus(); onDone?.invoke() } }
        }

        override fun onError(errorType: TtsError) {
            Log.w(TAG, "tts error $errorType")
            post { if (utterance.onEnd()) abandonFocus() }
        }

        override fun onStop() {
            post { if (utterance.onEnd()) abandonFocus() }
        }

        override fun onUpdatedRms(rms: Double) = Unit
    }

    fun init(onState: (SdkState) -> Unit) {
        tts.initialize(object : TtsInit {
            override fun onSuccess() {
                tts.addEventListener(sdkListener)
                tts.setLocale(Locale.KOREA)
                post { ready = true; onState(SdkState.READY) }
            }

            override fun onFailure(errorType: TtsError) {
                Log.w(TAG, "tts init failed: $errorType")
                post { onState(SdkState.FAILED) }
            }
        })
    }

    fun release() {
        ready = false
        abandonFocus()
        runCatching { tts.removeEventListener(sdkListener) }
        runCatching { tts.release() }
    }

    override fun say(text: String) {
        if (!ready) {
            Log.w(TAG, "tts not ready, drop len=${text.length}")
            return
        }
        abandonFocus()
        val seq = ++focusSeq
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(attrs)
            .setOnAudioFocusChangeListener({ change -> post { onFocusChange(seq, change) } }, Handler(Looper.getMainLooper()))
            .build()
        if (FocusPolicy.onRequestResult(audioManager.requestAudioFocus(request)) == FocusPolicy.Action.SKIP) {
            Log.w(TAG, "tts focus denied, skip len=${text.length}") // 요약에 발화 내용이 있어 본문은 남기지 않는다
            post { onDone?.invoke() }
            return
        }
        focusRequest = request
        utterance.begin()
        tts.setAudioAttributes(attrs)
        tts.speak(text, true)
    }

    override fun stop() {
        if (ready) {
            runCatching { tts.stop() }
            utterance.halt()
        }
        abandonFocus()
    }

    private fun onFocusChange(seq: Int, change: Int) {
        if (seq != focusSeq || focusRequest == null || FocusPolicy.onFocusChange(change) == FocusPolicy.Change.KEEP) return
        Log.i(TAG, "tts focus lost $change")
        runCatching { tts.stop() }
        utterance.halt()
        abandonFocus()
        onDone?.invoke()
    }

    private fun abandonFocus() {
        val r = focusRequest ?: return
        focusRequest = null
        audioManager.abandonAudioFocusRequest(r)
    }
}

private const val TAG = "PleoryPlatform"
