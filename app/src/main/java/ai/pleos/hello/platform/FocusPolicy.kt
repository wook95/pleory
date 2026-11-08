package ai.pleos.hello.platform

import android.media.AudioManager

/** TTS 오디오 포커스 판단. 안드로이드 상수만 비교하는 순수 로직이라 단위 테스트한다. */
object FocusPolicy {
    enum class Action { SPEAK, SKIP }
    enum class Change { STOP, KEEP }

    /** 포커스를 못 얻으면(FAILED·DELAYED) 말하지 않고 건너뛴다. */
    fun onRequestResult(result: Int): Action =
        if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) Action.SPEAK else Action.SKIP

    /** LOSS·LOSS_TRANSIENT 는 중단, CAN_DUCK 은 시스템이 볼륨을 낮추므로 계속 말한다. */
    fun onFocusChange(change: Int): Change = when (change) {
        AudioManager.AUDIOFOCUS_LOSS,
        AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> Change.STOP
        else -> Change.KEEP
    }
}
