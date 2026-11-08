package ai.pleos.hello.core

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FallbackStructurerTest {
    private val ok = """{"items":[{"text":"노을이 예뻤다","type":"emotion","emotion":"up","keywords":["노을"]}]}"""

    private class Fake(private val block: suspend () -> StructurerResult) : Structurer {
        var calls = 0
        override suspend fun structure(text: String): StructurerResult { calls++; return block() }
    }

    private fun chain(first: Structurer, second: Structurer, log: MutableList<String>, firstTimeoutMs: Long? = null, firstAvailable: Boolean = true) =
        FallbackStructurer(
            listOf(
                FallbackStructurer.Step("claude", first, firstTimeoutMs, available = { firstAvailable }),
                FallbackStructurer.Step("gemini", second),
            ),
            trace = { log += it },
        )

    @Test fun firstOk_secondNotCalled() = runBlocking {
        val second = Fake { StructurerResult.Ok(ok) }
        val log = mutableListOf<String>()
        assertEquals(StructurerResult.Ok(ok), chain(Fake { StructurerResult.Ok(ok) }, second, log).structure("x"))
        assertEquals(0, second.calls)
        assertEquals(listOf("llm ok: claude"), log)
    }

    @Test fun httpErrorFallsBack_andLogsCategory() = runBlocking {
        val log = mutableListOf<String>()
        val r = chain(Fake { StructurerResult.Error("claude http 429") }, Fake { StructurerResult.Ok(ok) }, log).structure("x")
        assertEquals(StructurerResult.Ok(ok), r)
        assertEquals(listOf("llm claude failed: claude http 429", "llm ok: gemini"), log)
    }

    @Test fun contractViolationFallsBack() = runBlocking {
        val log = mutableListOf<String>()
        val r = chain(Fake { StructurerResult.Ok("""{"items":[]}""") }, Fake { StructurerResult.Ok(ok) }, log).structure("x")
        assertEquals(StructurerResult.Ok(ok), r)
        assertEquals("llm claude failed: contract violation", log.first())
    }

    @Test fun slowFirstIsCutAtItsBudget() = runBlocking {
        val log = mutableListOf<String>()
        val r = chain(Fake { awaitCancellation() }, Fake { StructurerResult.Ok(ok) }, log, firstTimeoutMs = 50).structure("x")
        assertEquals(StructurerResult.Ok(ok), r)
        assertEquals("llm claude failed: claude timeout", log.first())
    }

    @Test fun unavailableFirstIsSkipped() = runBlocking {
        val first = Fake { StructurerResult.Ok(ok) }
        val log = mutableListOf<String>()
        assertEquals(StructurerResult.Ok(ok), chain(first, Fake { StructurerResult.Ok(ok) }, log, firstAvailable = false).structure("x"))
        assertEquals(0, first.calls)
        assertEquals("llm claude skipped: unavailable", log.first())
    }

    @Test fun allFail_returnsLastResult() = runBlocking {
        val log = mutableListOf<String>()
        val r = chain(Fake { StructurerResult.Error("claude refusal") }, Fake { StructurerResult.NetworkUnavailable }, log).structure("x")
        assertEquals(StructurerResult.NetworkUnavailable, r)
    }

    @Test fun outerCancellationStopsChain_noLateResult() = runBlocking {
        val secondStarted = CompletableDeferred<Unit>()
        val second = Fake { secondStarted.complete(Unit); awaitCancellation() }
        val log = mutableListOf<String>()
        val c = chain(Fake { StructurerResult.Error("claude http 500") }, second, log)
        val r = withTimeoutOrNull(100) { c.structure("x") }
        assertNull(r)
        assertTrue(secondStarted.isCompleted)
        // 취소 뒤에 성공 로그가 남지 않는다
        delay(20)
        assertTrue(log.none { it.startsWith("llm ok") })
    }
}
