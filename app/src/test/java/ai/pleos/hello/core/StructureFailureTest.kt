package ai.pleos.hello.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StructureFailureTest {
    @Test
    fun reasonByCategory() {
        assertEquals("timeout", StructureFailure.reason(null, null))
        assertEquals("network unavailable", StructureFailure.reason(StructurerResult.NetworkUnavailable, null))
        assertEquals("gemini http 429", StructureFailure.reason(StructurerResult.Error("gemini http 429"), null))
        assertEquals("contract violation", StructureFailure.reason(StructurerResult.Ok("{}"), StructureResult.Failed("items 없음: 모델 값")))
    }

    @Test
    fun successHasNoReason() {
        assertNull(StructureFailure.reason(StructurerResult.Ok("{}"), StructureResult.Ok(emptyList())))
    }
}
