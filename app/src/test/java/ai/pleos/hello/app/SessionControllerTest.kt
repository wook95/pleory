package ai.pleos.hello.app

import ai.pleos.hello.core.CardModel
import ai.pleos.hello.core.CardModelBuilder
import ai.pleos.hello.core.Clock
import ai.pleos.hello.core.DigestItem
import ai.pleos.hello.core.Headlines
import ai.pleos.hello.core.Gear
import ai.pleos.hello.core.RuleStructurer
import ai.pleos.hello.core.SessionEvent
import ai.pleos.hello.core.StructuredItem
import ai.pleos.hello.core.Structurer
import ai.pleos.hello.core.StructurerResult
import ai.pleos.hello.data.Emotion
import ai.pleos.hello.data.Item
import ai.pleos.hello.data.ItemDao
import ai.pleos.hello.data.ItemType
import ai.pleos.hello.data.LlmStatus
import ai.pleos.hello.data.PleoryStore
import ai.pleos.hello.data.Segment
import ai.pleos.hello.data.Session
import ai.pleos.hello.data.SessionState
import ai.pleos.hello.data.Utterance
import ai.pleos.hello.platform.SpeechInput
import ai.pleos.hello.platform.SpeechInputListener
import ai.pleos.hello.platform.SpeechOutput
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    /** 저장소 쓰기 순서(항목 삭제 → 전체 삭제 대기 확인용) */
    val writes = CopyOnWriteArrayList<String>()
    val deletedItems = java.util.concurrent.ConcurrentHashMap.newKeySet<Long>()
    @Volatile var deleteItemDelayMs = 0L
    override suspend fun deleteItem(itemId: Long) {
        if (deleteItemDelayMs > 0) delay(deleteItemDelayMs)
        deletedItems += itemId
        writes += "deleteItem:$itemId"
    }
    override suspend fun deleteAll() {
        utterances.clear(); items.clear(); sessions.clear(); deletedItems.clear()
        writes += "deleteAll"
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
    @Volatile var replaceDelayMs = 0L
    val replaceStarted = AtomicInteger()
    override suspend fun replaceItems(utteranceId: Long, items: List<StructuredItem>, status: LlmStatus) {
        replaceStarted.incrementAndGet()
        if (replaceDelayMs > 0) delay(replaceDelayMs)
        this.items[utteranceId] = items // 삭제 후 삽입과 같은 결과
        utterances[utteranceId] = utterances.getValue(utteranceId).copy(llmStatus = status)
        replaceCalls.incrementAndGet()
    }
    override suspend fun setLlmStatus(utteranceId: Long, status: LlmStatus) {
        utterances[utteranceId] = utterances.getValue(utteranceId).copy(llmStatus = status)
    }

    /** 대표 문장 저장 기록(null = 비움). [cardEnabled] 면 loadCard 가 저장된 항목으로 카드를 만든다(기본은 다른 테스트처럼 null). */
    val headlines = CopyOnWriteArrayList<Headlines?>()
    @Volatile var cardEnabled = false
    override suspend fun setHeadlines(sessionId: Long, headlines: Headlines?) { this.headlines += headlines }
    override suspend fun sessionOfItem(itemId: Long): Long? = 1L
    override suspend fun loadCard(sessionId: Long): CardModel? = if (!cardEnabled) null else CardModelBuilder.build(
        Session(id = 1, profileId = "local", startedAt = 0, segment = Segment.OUTING),
        utterances.values.map { u ->
            u to items[u.id].orEmpty().mapIndexed { i, it -> Item(id = u.id * 10 + i, utteranceId = u.id, order = i, text = it.text, type = it.type, emotion = it.emotion, deleted = (u.id * 10 + i) in deletedItems) }
        },
        emptyList(),
    )
}

private class FakeIn : SpeechInput {
    override var listener: SpeechInputListener? = null
    override fun start(round: Int) = Unit
    override fun startFromPcm(bytes: ByteArray, round: Int) = Unit
    override fun cancel() = Unit
}

private class FakeOut : SpeechOutput {
    override var onDone: (() -> Unit)? = null
    override var onResumed: (() -> Unit)? = null
    val said = CopyOnWriteArrayList<String>()
    override fun say(text: String, resumable: Boolean) { said += text }
    override fun stop() = Unit
}

private open class CountingOutputs : SessionOutputs {
    val refreshes = AtomicInteger()
    override fun showS0() = Unit
    override fun showCard() = Unit
    override fun startSummary() = Unit
    override fun stopSummary() = Unit
    override fun refreshCard() { refreshes.incrementAndGet() }
}

private object NoOutputs : CountingOutputs()

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

    private fun controller(
        structurer: Structurer,
        timeoutMs: Long = 8_000,
        outputs: SessionOutputs = NoOutputs,
        digest: suspend (List<DigestItem>) -> Headlines? = { null },
    ) = SessionController(
        store, structurer, speechIn, speechOut, outputs, FixedContextProvider(), FixedClock(),
        executor.asCoroutineDispatcher(), structureTimeoutMs = timeoutMs, digest = digest,
    )

    @Test fun deleteItem_clearsHeadlines_thenRedigestsRemainingItems() {
        store.cardEnabled = true
        val seen = CopyOnWriteArrayList<List<String>>()
        val c = controller(RuleStructurer(), digest = { items -> seen += items.map { it.text }; Headlines(work = "서류 보내기") })
        speak(c, TEXT)
        awaitUntil { store.replaceCalls.get() == 1 }
        c.deleteItem(11L) // 두 번째 항목(노을)
        awaitUntil { store.headlines.lastOrNull() == Headlines(work = "서류 보내기") }
        // 지우기 저장 안에서 먼저 비운다(지운 글이 대표 문장에 남지 않게), 그 뒤 다시 만든 문장
        assertEquals(null, store.headlines.first())
        assertEquals(listOf(listOf("내일 거래처 서류 다시 보내야 해")), seen.toList())
    }

    @Test fun digestFailure_leavesHeadlinesEmpty_forRuleFallback() {
        store.cardEnabled = true
        val c = controller(RuleStructurer(), digest = { null })
        speak(c, TEXT)
        awaitUntil { store.replaceCalls.get() == 1 }
        c.deleteItem(11L)
        awaitUntil { store.headlines.size >= 2 }
        Thread.sleep(100)
        assertTrue(store.headlines.all { it == null })
    }

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

    @Test fun deleteItem_softDeletesInStore_thenRefreshesCard() {
        val out = CountingOutputs()
        val c = controller(RuleStructurer(), outputs = out)
        c.deleteItem(7L)
        awaitUntil { out.refreshes.get() >= 1 }
        assertEquals(setOf(7L), store.deletedItems.toSet())
    }

    @Test fun deleteAll_waitsForInFlightItemDelete() {
        val c = controller(RuleStructurer())
        store.deleteItemDelayMs = 200
        c.deleteItem(3L)
        var done = false
        c.deleteAllData { done = true }
        awaitUntil { done }
        assertEquals(listOf("deleteItem:3", "deleteAll"), store.writes.toList())
        assertTrue("전체 삭제는 소프트 삭제 표시도 지운다", store.deletedItems.isEmpty())
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

    @Test fun deleteAll_waitsForInFlightCommit() {
        store.replaceDelayMs = 400
        val c = controller(RuleStructurer())
        speak(c, TEXT)
        awaitUntil { store.replaceStarted.get() == 1 } // 커밋이 저장소에 쓰는 중
        val done = java.util.concurrent.CountDownLatch(1)
        c.deleteAllData { done.countDown() }
        assertTrue(done.await(4, java.util.concurrent.TimeUnit.SECONDS))
        Thread.sleep(600)
        assertTrue("삭제 뒤에 커밋이 되살아남", store.utterances.isEmpty() && store.items.isEmpty())
    }

    @Test fun addNote_rejectedWhenNoSession_acceptedAfterIgnition() {
        val c = controller(RuleStructurer())
        val one = "[{\"text\":\"x\",\"type\":\"todo\",\"emotion\":\"neutral\",\"keywords\":[]}]"
        assertFalse(c.addNoteFromVoice("시동 전 메모"))
        assertFalse(c.addStructuredNoteFromVoice("시동 전 메모", one))
        c.post(SessionEvent.IgnitionOn)
        awaitUntil { c.state == ai.pleos.hello.core.SessionState.SESSION_OPEN }
        assertFalse(c.addNoteFromVoice("  "))
        assertTrue(c.addNoteFromVoice("시동 뒤 메모"))
        awaitUntil { store.replaceCalls.get() == 1 }
        assertEquals(1, store.utterances.size)
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
        awaitUntil { c.state == ai.pleos.hello.core.SessionState.SESSION_OPEN }
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
        awaitUntil { c.state == ai.pleos.hello.core.SessionState.SESSION_OPEN }
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
