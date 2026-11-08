package ai.pleos.hello.platform

import android.media.AudioManager

/** TTS 오디오 포커스 판단. 안드로이드 상수만 비교하는 순수 로직이라 단위 테스트한다. */
object FocusPolicy {
    enum class Action { SPEAK, SKIP }
    enum class Change { STOP, KEEP, PAUSE, RESUME }

    /** 포커스를 못 얻으면(FAILED·DELAYED) 말하지 않고 건너뛴다. */
    fun onRequestResult(result: Int): Action =
        if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) Action.SPEAK else Action.SKIP

    /**
     * LOSS 는 중단. LOSS_TRANSIENT 는 [resumable](요약)이면 멈췄다가 GAIN 에서 다시 읽고, 아니면("적어뒀어") 중단해 생략한다(PRD v4 §1 예외).
     * CAN_DUCK 은 시스템이 볼륨을 낮추므로 계속 말한다.
     */
    fun onFocusChange(change: Int, resumable: Boolean = false, paused: Boolean = false): Change = when (change) {
        AudioManager.AUDIOFOCUS_LOSS -> Change.STOP
        AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> if (resumable) Change.PAUSE else Change.STOP
        AudioManager.AUDIOFOCUS_GAIN -> if (paused) Change.RESUME else Change.KEEP
        else -> Change.KEEP
    }
}

/**
 * 발화별 종료 이벤트 구분. TTS SDK 의 onDone/onStop/onError 는 발화 id 가 없어서, 우리가 먼저 끊은(stop·덮어쓰기) 발화의
 * 늦은 종료 이벤트가 새 발화의 포커스·요약을 끝내지 않게 "버려야 할 종료 이벤트 수"를 센다. 직렬 경로에서만 쓴다.
 */
class UtteranceTracker {
    private var active = false
    private var staleEnds = 0

    /** 새 발화를 시작한다. 이전 발화가 아직 재생 중이면 그 발화의 종료 이벤트가 따로 온다. */
    fun begin() {
        if (active) staleEnds++
        active = true
    }

    /** 우리가 재생을 끊었다(stop). 재생 중이던 발화의 종료 이벤트가 뒤따른다. */
    fun halt() {
        if (!active) return
        staleEnds++
        active = false
    }

    /** SDK 종료 이벤트. 현재 발화의 끝이면 true, 이미 끊은 발화의 늦은 이벤트면 false. */
    fun onEnd(): Boolean {
        if (staleEnds > 0) {
            staleEnds--
            return false
        }
        active = false
        return true
    }
}
