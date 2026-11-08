package ai.pleos.hello.app

import ai.pleos.hello.core.Clock
import ai.pleos.hello.core.Gear
import ai.pleos.hello.core.RuleStructurer
import ai.pleos.hello.core.SessionEvent
import ai.pleos.hello.core.StructuredItem
import ai.pleos.hello.core.Structurer
import ai.pleos.hello.core.StructurerResult
import ai.pleos.hello.data.Emotion
import ai.pleos.hello.data.ItemDao
import ai.pleos.hello.data.ItemType
import ai.pleos.hello.data.LlmStatus
import ai.pleos.hello.data.PleoryStore
import ai.pleos.hello.data.Segment
import ai.pleos.hello.data.SessionState
import ai.pleos.hello.data.Utterance
import ai.pleos.hello.platform.SpeechInput
import ai.pleos.hello.platform.SpeechInputListener
import ai.pleos.hello.platform.SpeechOutput
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

private const val TEXT = "내일 거래처 서류 다시 보내야 해, 아 노을 예쁘다"

private class FakeStore : PleoryStore {
    val utterances = java.util.concurrent.ConcurrentHashMap<Long, Utterance>()
    val items = java.util.concurrent.ConcurrentHashMap<Long, List<StructuredItem>>()
    val replaceCalls = AtomicInteger()
    val closed = CopyOnWriteArrayList<Long>()
    private val seq = AtomicInteger()
    val sessions = java.util.concurrent.ConcurrentHashMap.newKeySet<Long>()
    val sessionCreates = AtomicInteger()

    override suspend fun createSession(profileId: String, segment: Segment, startedAt: Long): Long {
        sessions += 1L
        sessionCreates.incrementAndGet()
        return 1L
    }
    override suspend fun deleteAll() {
        utterances.clear(); items.clear(); sessions.clear()
    }
    override suspend fun setSessionState(sessionId: Long, state: SessionState) = Unit
    override suspend fun closeSession(sessionId: Long, endedAt: Long) { closed += sessionId }
    override suspend fun saveUtterance(utterance: Utterance): Long {
        check(utterance.sessionId in sessions) { "FK: 세션 없음" } // 실제 DB 의 외래 키 제약 대응
        val id = seq.incrementAndGet().toLong()
        utterances[id] = utterance.copy(id = id)
        return id
    }
    override suspend fun utterance(id: Long) = utterances[id]
    override suspend fun pendingUtterances() = utterances.values.filter { it.llmStatus == LlmStatus.PENDING }
    override suspend fun replaceItems(utteranceId: Long, items: List<StructuredItem>, status: LlmStatus) {
        this.items[utteranceId] = items // 삭제 후 삽입과 같은 결과
        utterances[utteranceId] = utterances.getValue(utteranceId).copy(llmStatus = status)
        replaceCalls.incrementAndGet()
    }
    override suspend fun setLlmStatus(utteranceId: Long, status: LlmStatus) {
        utterances[utteranceId] = utterances.getValue(utteranceId).copy(llmStatus = status)
    }
}

private class FakeIn : SpeechInput {
    override var listener: SpeechInputListener? = null
    override fun start(round: Int) = Unit
    override fun startFromPcm(bytes: ByteArray, round: Int) = Unit
    override fun cancel() = Unit
}

private class FakeOut : SpeechOutput {
    override var onDone: (() -> Unit)? = null
    val said = CopyOnWriteArrayList<String>()
    override fun say(text: String) { said += text }
    override fun stop() = Unit
}

private object NoOutputs : SessionOutputs {
    override fun showS0() = Unit
    override fun showCard() = Unit
    override fun startSummary() = Unit
    override fun stopSummary() = Unit
}

private class FixedContextProvider : ContextProvider {
    override fun profileId() = "local"
    override fun segment() = Segment.OUTING
    override fun snapshot() = ContextSnapshot()
}

private class FixedClock : Clock {
    override fun nowMillis() = 1_000L
}

class SessionControllerTest {
    private val executor = Executors.newSingleThreadExecutor()
    private val store = FakeStore()
    private val speechIn = FakeIn()
    private val speechOut = FakeOut()

    @After fun tearDown() = executor.shutdownNow().let { }

    private fun controller(structurer: Structurer, timeoutMs: Long = 8_000) = SessionController(
        store, structurer, speechIn, speechOut, NoOutputs, FixedContextProvider(), FixedClock(),
        executor.asCoroutineDispatcher(), structureTimeoutMs = timeoutMs,
    )

    private fun speak(c: SessionController, text: String) {
        c.post(SessionEvent.IgnitionOn)
        c.post(SessionEvent.TalkTapped)
        awaitUntil { speechIn.listener != null }
        c.post(SessionEvent.SttFinal(text, 1))
    }

    private fun awaitUntil(timeoutMs: Long = 4_000, cond: () -> Boolean) {
        val end = System.currentTimeMillis() + timeoutMs
        while (!cond()) {
            check(System.currentTimeMillis() < end) { "조건 대기 시간 초과" }
            Thread.sleep(10)
        }
    }

    @Test fun ruleStructurer_twoItems_todoNeutral_emotionUp() {
        val c = controller(RuleStructurer())
        speak(c, TEXT)
        awaitUntil { store.replaceCalls.get() == 1 }
        val items = store.items.getValue(1)
        assertEquals(2, items.size)
        assertEquals(ItemType.TODO to Emotion.NEUTRAL, items[0].type to items[0].emotion)
        assertEquals(ItemType.EMOTION to Emotion.UP, items[1].type to items[1].emotion)
        assertEquals(LlmStatus.DONE, store.utterances.getValue(1).llmStatus)
        assertEquals(listOf("적어뒀어"), speechOut.said.toList())
    }

    @Test fun slowerThanTimeout_rawAndFailed_lateResponseIgnored() {
        val slow = object : Structurer {
            override suspend fun structure(text: String): StructurerResult {
                delay(5_000)
                return RuleStructurer().structure(text)
            }
        }
        val c = controller(slow, timeoutMs = 100)
        speak(c, TEXT)
        awaitUntil { store.replaceCalls.get() == 1 }
        val items = store.items.getValue(1)
        assertEquals(1, items.size)
        assertEquals(ItemType.RAW, items[0].type)
        assertEquals(TEXT, items[0].text)
        assertEquals(LlmStatus.FAILED, store.utterances.getValue(1).llmStatus)
        // 늦게 도착한 성공(세대 1)은 버려진다
        c.post(SessionEvent.LlmDone(1, 1))
        Thread.sleep(200)
        assertEquals(1, store.replaceCalls.get())
        assertEquals(ItemType.RAW, store.items.getValue(1)[0].type)
    }

    @Test fun networkUnavailable_staysPending_noItems() {
        val calls = AtomicInteger()
        val offline = object : Structurer {
            override suspend fun structure(text: String): StructurerResult {
                calls.incrementAndGet()
                return StructurerResult.NetworkUnavailable
            }
        }
        val c = controller(offline)
        speak(c, TEXT)
        awaitUntil { calls.get() == 1 }
        Thread.sleep(200)
        assertEquals(LlmStatus.PENDING, store.utterances.getValue(1).llmStatus)
        assertEquals(0, store.replaceCalls.get())
    }

    @Test fun reprocessTwice_itemCountStable() {
        val c = controller(RuleStructurer())
        speak(c, TEXT)
        awaitUntil { store.replaceCalls.get() == 1 }
        c.reprocess(1, TEXT)
        awaitUntil { store.replaceCalls.get() == 2 }
        c.reprocess(1, TEXT)
        awaitUntil { store.replaceCalls.get() == 3 }
        assertEquals(2, store.items.getValue(1).size)
    }

    @Test fun invalidJson_rawAndFailed() {
        val bad = object : Structurer {
            override suspend fun structure(text: String) = StructurerResult.Ok("```json {} ```")
        }
        val c = controller(bad)
        speak(c, TEXT)
        awaitUntil { store.replaceCalls.get() == 1 }
        assertEquals(LlmStatus.FAILED, store.utterances.getValue(1).llmStatus)
    }

    @Test fun deleteAll_emptiesStore_andNextSaveRecreatesSession() {
        val c = controller(RuleStructurer())
        speak(c, TEXT)
        awaitUntil { store.replaceCalls.get() == 1 }
        val done = java.util.concurrent.CountDownLatch(1)
        c.deleteAllData { done.countDown() }
        assertTrue(done.await(4, java.util.concurrent.TimeUnit.SECONDS))
        assertTrue(store.utterances.isEmpty())
        assertTrue(store.items.isEmpty())
        assertTrue(store.sessions.isEmpty())
        // 삭제 뒤 새 발화가 외래 키 오류 없이 저장된다
        val before = store.sessionCreates.get()
        c.addNoteFromVoice("삭제 뒤 메모")
        awaitUntil { store.utterances.size == 1 }
        assertEquals(before + 1, store.sessionCreates.get())
    }

    @Test fun gleoStructuredNote_skipsStructurer_commitsGleoItems() {
        val calls = AtomicInteger()
        val counting = object : Structurer {
            override suspend fun structure(text: String): StructurerResult {
                calls.incrementAndGet()
                return RuleStructurer().structure(text)
            }
        }
        val c = controller(counting)
        c.post(SessionEvent.IgnitionOn)
        c.addStructuredNoteFromVoice(
            TEXT,
            """[{"text":"내일 거래처 서류 다시 보내야 해","type":"todo","emotion":"neutral","keywords":["거래처","서류"]},""" +
                """{"text":"아 노을 예쁘다","type":"thought","emotion":"up","keywords":["노을"]}]""",
        )
        awaitUntil { store.replaceCalls.get() == 1 }
        val items = store.items.getValue(1)
        assertEquals(listOf(ItemType.TODO, ItemType.THOUGHT), items.map { it.type })
        assertEquals(listOf("거래처", "서류"), items[0].keywords)
        assertEquals(TEXT, store.utterances.getValue(1).text)
        assertEquals(LlmStatus.DONE, store.utterances.getValue(1).llmStatus)
        assertEquals(0, calls.get())
        assertTrue(speechOut.said.isEmpty()) // Gleo 경로는 우리가 말하지 않는다
    }

    @Test fun gleoStructuredNote_invalidItems_fallsBackToStructurer() {
        val calls = AtomicInteger()
        val counting = object : Structurer {
            override suspend fun structure(text: String): StructurerResult {
                calls.incrementAndGet()
                return RuleStructurer().structure(text)
            }
        }
        val c = controller(counting)
        c.post(SessionEvent.IgnitionOn)
        c.addStructuredNoteFromVoice(TEXT, """[{"text":"x","type":"todo","emotion":"angry"}]""")
        awaitUntil { store.replaceCalls.get() == 1 }
        assertEquals(1, calls.get())
        assertEquals(LlmStatus.DONE, store.utterances.getValue(1).llmStatus)
    }

    @Test fun replaceItems_isAnnotatedTransaction() {
        val bytes = ItemDao::class.java.getResourceAsStream("ItemDao.class")!!.readBytes()
        val text = String(bytes, Charsets.ISO_8859_1)
        assertTrue(text.contains("replaceItems"))
        assertTrue("@Transaction 누락", text.contains("Landroidx/room/Transaction;"))
    }
}
