package ai.pleos.hello.platform

import android.media.AudioManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FocusPolicyTest {
    @Test
    fun `포커스 요청은 GRANTED 일 때만 말한다`() {
        assertEquals(FocusPolicy.Action.SPEAK, FocusPolicy.onRequestResult(AudioManager.AUDIOFOCUS_REQUEST_GRANTED))
        assertEquals(FocusPolicy.Action.SKIP, FocusPolicy.onRequestResult(AudioManager.AUDIOFOCUS_REQUEST_FAILED))
        assertEquals(FocusPolicy.Action.SKIP, FocusPolicy.onRequestResult(AudioManager.AUDIOFOCUS_REQUEST_DELAYED))
    }

    @Test
    fun `끊은 발화의 늦은 종료 이벤트는 새 발화를 끝내지 않는다`() {
        val u = UtteranceTracker()
        u.begin()
        u.halt() // stop()
        u.begin() // 곧바로 새 발화
        assertFalse(u.onEnd()) // 이전 발화의 늦은 onStop
        assertTrue(u.onEnd()) // 새 발화의 onDone
    }

    @Test
    fun `재생 중에 덮어쓰면 이전 발화의 종료 이벤트 하나를 버린다`() {
        val u = UtteranceTracker()
        u.begin()
        u.begin()
        assertFalse(u.onEnd())
        assertTrue(u.onEnd())
    }

    @Test
    fun `재생 중이 아닐 때 halt 는 아무것도 버리지 않는다`() {
        val u = UtteranceTracker()
        u.halt()
        u.begin()
        assertTrue(u.onEnd())
        u.halt() // 이미 끝남
        u.begin()
        assertTrue(u.onEnd())
    }

    @Test
    fun `LOSS 와 LOSS_TRANSIENT 는 중단, CAN_DUCK 과 GAIN 은 유지`() {
        assertEquals(FocusPolicy.Change.STOP, FocusPolicy.onFocusChange(AudioManager.AUDIOFOCUS_LOSS))
        assertEquals(FocusPolicy.Change.STOP, FocusPolicy.onFocusChange(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT))
        assertEquals(FocusPolicy.Change.KEEP, FocusPolicy.onFocusChange(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK))
        assertEquals(FocusPolicy.Change.KEEP, FocusPolicy.onFocusChange(AudioManager.AUDIOFOCUS_GAIN))
    }
}
