package ai.pleos.hello.gleo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GleoActionReceiverTest {
    @Test
    fun packagePrefixedActionsThatGleoSends() {
        assertEquals("ADD_DRIVE_NOTE", GleoActionReceiver.functionOf("ai.pleos.hello.ADD_DRIVE_NOTE"))
        assertEquals("ADD_DRIVE_NOTES", GleoActionReceiver.functionOf("ai.pleos.hello.ADD_DRIVE_NOTES"))
    }

    @Test
    fun documentedBareAction() {
        assertEquals("ADD_DRIVE_NOTE", GleoActionReceiver.functionOf("ADD_DRIVE_NOTE"))
    }

    @Test
    fun inputLimits() {
        assertNull(GleoActionReceiver.inputProblem("a".repeat(GleoActionReceiver.MAX_NOTE_CHARS), 100))
        assertEquals("blank note", GleoActionReceiver.inputProblem(" ", 0)?.first)
        assertEquals("too long", GleoActionReceiver.inputProblem("a".repeat(GleoActionReceiver.MAX_NOTE_CHARS + 1), 0)?.first)
        assertEquals("too long", GleoActionReceiver.inputProblem("ok", GleoActionReceiver.MAX_PARAM_CHARS + 1)?.first)
    }

    @Test
    fun otherActionsRejected() {
        assertNull(GleoActionReceiver.functionOf(null))
        assertNull(GleoActionReceiver.functionOf("XADD_DRIVE_NOTE"))
        assertNull(GleoActionReceiver.functionOf("ai.pleos.hello.DELETE_DRIVE_NOTE"))
    }

    @Test
    fun showTodayCardActionNames() {
        assertTrue(GleoActionReceiver.isShowTodayCard("ai.pleos.hello.SHOW_TODAY_CARD"))
        assertTrue(GleoActionReceiver.isShowTodayCard("SHOW_TODAY_CARD"))
        assertFalse(GleoActionReceiver.isShowTodayCard("XSHOW_TODAY_CARD"))
        assertFalse(GleoActionReceiver.isShowTodayCard(null))
        // 메모 receiver 는 카드 액션을 받지 않는다
        assertNull(GleoActionReceiver.functionOf("ai.pleos.hello.SHOW_TODAY_CARD"))
    }

    @Test
    fun showTodayCardOpensOnlyWhileParked() {
        assertEquals("success", GleoActionReceiver.showTodayCardReply(parked = true).first)
        assertEquals("fail" to "The card is available only while parked.", GleoActionReceiver.showTodayCardReply(parked = false))
    }
}
