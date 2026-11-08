package ai.pleos.hello.app

import ai.pleos.hello.platform.SpeechInput
import ai.pleos.hello.platform.SpeechInputListener
import android.os.Handler
import android.os.Looper

/**
 * 디버그 주입용 래퍼. [pendingPcm] 이 있으면 다음 start() 에서 마이크 대신 그 PCM 을 STT 로 보낸다.
 * 컨트롤러 파이프라인(TalkTapped → StartStt)을 그대로 타고, 입력원만 바뀐다. 평소엔 null 이라 마이크.
 */
class PcmInjectableSpeechInput(private val real: SpeechInput) : SpeechInput {
    @Volatile
    var pendingPcm: ByteArray? = null

    /** 디버그 주입용. 있으면 다음 start() 가 STT 를 건너뛰고 이 문장을 인식 결과(final)로 돌려준다. */
    @Volatile
    var pendingText: String? = null

    /** [pendingText] 를 돌려주기까지 "듣고 있어요" 를 유지할 시간. 한 번 쓰면 기본값으로 돌아간다(데모가 대사 길이만큼 늘린다). */
    @Volatile
    var pendingTextDelayMs: Long = TEXT_DELAY_MS

    override var listener: SpeechInputListener?
        get() = real.listener
        set(value) { real.listener = value }

    override fun start(round: Int) {
        val pcm = pendingPcm
        val text = pendingText
        pendingPcm = null
        pendingText = null
        val delayMs = pendingTextDelayMs
        pendingTextDelayMs = TEXT_DELAY_MS
        if (text != null) {
            Handler(Looper.getMainLooper()).postDelayed({ listener?.onFinal(text, round) }, delayMs)
        } else if (pcm != null) real.startFromPcm(pcm, round) else real.start(round)
    }

    override fun startFromPcm(bytes: ByteArray, round: Int) = real.startFromPcm(bytes, round)
    override fun cancel() = real.cancel()

    private companion object {
        const val TEXT_DELAY_MS = 300L
    }
}
