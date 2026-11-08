package ai.pleos.hello.gleo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
    fun otherActionsRejected() {
        assertNull(GleoActionReceiver.functionOf(null))
        assertNull(GleoActionReceiver.functionOf("XADD_DRIVE_NOTE"))
        assertNull(GleoActionReceiver.functionOf("ai.pleos.hello.DELETE_DRIVE_NOTE"))
    }
}
