package ai.pleos.hello.gleo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private const val PKG = "com.pplab.pleory"

class GleoActionReceiverTest {
    @Test
    fun packagePrefixedActionsThatGleoSends() {
        assertEquals("ADD_DRIVE_NOTE", GleoActionReceiver.functionOf("com.pplab.pleory.ADD_DRIVE_NOTE", PKG))
        assertEquals("ADD_DRIVE_NOTES", GleoActionReceiver.functionOf("com.pplab.pleory.ADD_DRIVE_NOTES", PKG))
    }

    @Test
    fun documentedBareAction() {
        assertEquals("ADD_DRIVE_NOTE", GleoActionReceiver.functionOf("ADD_DRIVE_NOTE", PKG))
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
        assertNull(GleoActionReceiver.functionOf(null, PKG))
        assertNull(GleoActionReceiver.functionOf("XADD_DRIVE_NOTE", PKG))
        assertNull(GleoActionReceiver.functionOf("com.pplab.pleory.DELETE_DRIVE_NOTE", PKG))
    }

    @Test
    fun showTodayCardActionNames() {
        assertTrue(GleoActionReceiver.isShowTodayCard("com.pplab.pleory.SHOW_TODAY_CARD", PKG))
        assertTrue(GleoActionReceiver.isShowTodayCard("SHOW_TODAY_CARD", PKG))
        assertFalse(GleoActionReceiver.isShowTodayCard("XSHOW_TODAY_CARD", PKG))
        assertFalse(GleoActionReceiver.isShowTodayCard(null, PKG))
        // 메모 receiver 는 카드 액션을 받지 않는다
        assertNull(GleoActionReceiver.functionOf("com.pplab.pleory.SHOW_TODAY_CARD", PKG))
    }

    @Test
    fun showTodayCardOpensOnlyWhileParked() {
        assertEquals("success", GleoActionReceiver.showTodayCardReply(parked = true).first)
        assertEquals("fail" to "The card is available only while parked.", GleoActionReceiver.showTodayCardReply(parked = false))
    }

    @Test
    fun otherPackagePrefixRejected() {
        // 이전 applicationId 나 다른 앱 접두는 받지 않는다
        assertNull(GleoActionReceiver.functionOf("ai.pleos.hello.ADD_DRIVE_NOTE", PKG))
        assertFalse(GleoActionReceiver.isShowTodayCard("ai.pleos.hello.SHOW_TODAY_CARD", PKG))
    }
}
