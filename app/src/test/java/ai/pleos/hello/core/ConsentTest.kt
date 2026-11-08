package ai.pleos.hello.core

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConsentTest {
    private val v = ConsentPolicy.VERSION

    @Test fun onlyExplicitGrantAllowsLlm() {
        assertTrue(ConsentPolicy.llmAllowed(ConsentState.GRANTED, v))
        listOf(ConsentState.UNSET, ConsentState.DECLINED, ConsentState.WITHDRAWN).forEach {
            assertFalse("$it", ConsentPolicy.llmAllowed(it, v))
        }
    }

    @Test fun oldVersionGrantCountsAsUnset_andPromptsAgain() {
        assertEquals(ConsentState.UNSET, ConsentPolicy.effective(ConsentState.GRANTED, v - 1))
        assertFalse(ConsentPolicy.llmAllowed(ConsentState.GRANTED, v - 1))
        assertTrue(ConsentPolicy.needsPrompt(ConsentState.GRANTED, v - 1))
    }

    @Test fun promptOnlyWhenUnset() {
        assertTrue(ConsentPolicy.needsPrompt(ConsentState.UNSET, 0))
        assertFalse(ConsentPolicy.needsPrompt(ConsentState.DECLINED, v))
        assertFalse(ConsentPolicy.needsPrompt(ConsentState.WITHDRAWN, v))
        assertFalse(ConsentPolicy.needsPrompt(ConsentState.GRANTED, v))
    }

    private class CountingLlm : Structurer {
        var calls = 0
        override suspend fun structure(text: String): StructurerResult {
            calls++
            return StructurerResult.Ok("{}")
        }
    }

    @Test fun noConsent_neverCallsLlm_returnsError() = runBlocking {
        val llm = CountingLlm()
        val s = ConsentGatedStructurer({ false }, llm)
        assertTrue(s.structure("텍스트") is StructurerResult.Error)
        assertEquals(0, llm.calls)
    }

    @Test fun consentChecked_atCallTime_withdrawalAppliesImmediately() = runBlocking {
        val llm = CountingLlm()
        var allowed = true
        val s = ConsentGatedStructurer({ allowed }, llm)
        assertTrue(s.structure("a") is StructurerResult.Ok)
        allowed = false
        assertTrue(s.structure("b") is StructurerResult.Error)
        assertEquals(1, llm.calls)
    }
}
