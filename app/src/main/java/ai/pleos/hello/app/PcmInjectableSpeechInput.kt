package ai.pleos.hello.app

import ai.pleos.hello.platform.SpeechInput
import ai.pleos.hello.platform.SpeechInputListener

/**
 * 디버그 주입용 래퍼. [pendingPcm] 이 있으면 다음 start() 에서 마이크 대신 그 PCM 을 STT 로 보낸다.
 * 컨트롤러 파이프라인(TalkTapped → StartStt)을 그대로 타고, 입력원만 바뀐다. 평소엔 null 이라 마이크.
 */
class PcmInjectableSpeechInput(private val real: SpeechInput) : SpeechInput {
    @Volatile
    var pendingPcm: ByteArray? = null

    override var listener: SpeechInputListener?
        get() = real.listener
        set(value) { real.listener = value }

    override fun start(round: Int) {
        val pcm = pendingPcm
        pendingPcm = null
        if (pcm != null) real.startFromPcm(pcm, round) else real.start(round)
    }

    override fun startFromPcm(bytes: ByteArray, round: Int) = real.startFromPcm(bytes, round)
    override fun cancel() = real.cancel()
}
