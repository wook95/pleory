package ai.pleos.hello.platform

import android.media.AudioManager
import org.junit.Assert.assertEquals
import org.junit.Test

class FocusPolicyTest {
    @Test
    fun `포커스 요청은 GRANTED 일 때만 말한다`() {
        assertEquals(FocusPolicy.Action.SPEAK, FocusPolicy.onRequestResult(AudioManager.AUDIOFOCUS_REQUEST_GRANTED))
        assertEquals(FocusPolicy.Action.SKIP, FocusPolicy.onRequestResult(AudioManager.AUDIOFOCUS_REQUEST_FAILED))
        assertEquals(FocusPolicy.Action.SKIP, FocusPolicy.onRequestResult(AudioManager.AUDIOFOCUS_REQUEST_DELAYED))
    }

    @Test
    fun `LOSS 와 LOSS_TRANSIENT 는 중단, CAN_DUCK 과 GAIN 은 유지`() {
        assertEquals(FocusPolicy.Change.STOP, FocusPolicy.onFocusChange(AudioManager.AUDIOFOCUS_LOSS))
        assertEquals(FocusPolicy.Change.STOP, FocusPolicy.onFocusChange(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT))
        assertEquals(FocusPolicy.Change.KEEP, FocusPolicy.onFocusChange(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK))
        assertEquals(FocusPolicy.Change.KEEP, FocusPolicy.onFocusChange(AudioManager.AUDIOFOCUS_GAIN))
    }
}
