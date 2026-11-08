package ai.pleos.hello.app

import ai.pleos.hello.core.Clock
import ai.pleos.hello.core.DestinationState
import ai.pleos.hello.core.Gear
import ai.pleos.hello.core.ReminderCandidate
import ai.pleos.hello.core.RuleStructurer
import ai.pleos.hello.core.SessionEvent
import ai.pleos.hello.core.SessionState
import ai.pleos.hello.core.StructuredItem
import ai.pleos.hello.data.LlmStatus
import ai.pleos.hello.data.PleoryStore
import ai.pleos.hello.data.Segment
import ai.pleos.hello.data.Utterance
import ai.pleos.hello.platform.SpeechInput
import ai.pleos.hello.platform.SpeechInputListener
import ai.pleos.hello.platform.SpeechOutput
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.TimeZone
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong

private const val DAY = 24 * 60 * 60 * 1000L

/** 2026-11-08 08:30 KST */
private const val NOW = 1_794_094_200_000L

/** 리마인드 경로만 보는 저장소. 후보는 테스트가 넣고, 표시(remindedAt·done)는 실제 DB 처럼 반영해 다음 조회에서 빠진다. */
private class RemindStore : PleoryStore {
    private val seq = AtomicLong()
    val candidates = CopyOnWriteArrayList<ReminderCandidate>()
    val remindedCalls = CopyOnWriteArrayList<List<Long>>()
    val doneIds = CopyOnWriteArrayList<Long>()
    @Volatile var lookupDelayMs = 0L

    override suspend fun createSession(profileId: String, segment: Segment, startedAt: Long) = 100 + seq.incrementAndGet()
    override suspend fun setSessionState(sessionId: Long, state: ai.pleos.hello.data.SessionState) = Unit
    override suspend fun closeSession(sessionId: Long, endedAt: Long) = Unit
    override suspend fun saveUtterance(utterance: Utterance) = 1L
    override suspend fun utterance(id: Long): Utterance? = null
    override suspend fun pendingUtterances() = emptyList<Utterance>()
    override suspend fun replaceItems(utteranceId: Long, items: List<StructuredItem>, status: LlmStatus) = Unit
    override suspend fun setLlmStatus(utteranceId: Long, status: LlmStatus) = Unit
    override suspend fun deleteItem(itemId: Long) = Unit

    override suspend fun reminderCandidates(profileId: String, since: Long): List<ReminderCandidate> {
        if (lookupDelayMs > 0) delay(lookupDelayMs)
        return candidates.filter { it.spokenAt >= since }
    }
    override suspend fun markReminded(itemIds: List<Long>, at: Long) {
        remindedCalls += itemIds
        candidates.replaceAll { if (it.itemId in itemIds) it.copy(remindedAt = at) else it }
    }
    override suspend fun markItemDone(itemId: Long) {
        doneIds += itemId
        candidates.replaceAll { if (it.itemId == itemId) it.copy(done = true) else it }
    }
}

private class SayOut : SpeechOutput {
    override var onDone: (() -> Unit)? = null
    override var onResumed: (() -> Unit)? = null
    val said = CopyOnWriteArrayList<String>()
    override fun say(text: String, resumable: Boolean) { said += text }
    override fun stop() = Unit
}

private class NoIn : SpeechInput {
    override var listener: SpeechInputListener? = null
    override fun start(round: Int) = Unit
    override fun startFromPcm(bytes: ByteArray, round: Int) = Unit
    override fun cancel() = Unit
}

private object Quiet : SessionOutputs {
    override fun showS0() = Unit
    override fun showCard() = Unit
    override fun startSummary() = Unit
    override fun stopSummary() = Unit
}

private object HomeContext : ContextProvider {
    override fun profileId() = "local"
    override fun segment() = Segment.COMMUTE_IN
    override fun snapshot() = ContextSnapshot()
}

class ReminderControllerTest {
    private val executor = Executors.newSingleThreadExecutor()
    private val store = RemindStore()
    private val out = SayOut()
    private val traces = CopyOnWriteArrayList<String>()
    private val defaultTz = TimeZone.getDefault()

    init { TimeZone.setDefault(TimeZone.getTimeZone("Asia/Seoul")) }

    @After fun tearDown() {
        executor.shutdownNow()
        TimeZone.setDefault(defaultTz)
    }

    private fun controller(gate: () -> Boolean = { true }) = SessionController(
        store, RuleStructurer(), NoIn(), out, Quiet, HomeContext,
        object : Clock { override fun nowMillis() = NOW },
        executor.asCoroutineDispatcher(), trace = { traces += it }, gateAllowed = gate,
    )

    private fun todo(id: Long, session: Long = 7, text: String = "거래처 서류 다시 보내기", at: Long = NOW - DAY) =
        ReminderCandidate(itemId = id, text = text, sessionId = session, segment = Segment.COMMUTE_OUT, spokenAt = at)

    private fun arrive(c: SessionController) {
        c.post(SessionEvent.IgnitionOn)
        c.post(SessionEvent.GearChanged(Gear.D))
        c.post(SessionEvent.GearChanged(Gear.P))
        c.post(SessionEvent.Arrived)
    }

    private fun awaitUntil(timeoutMs: Long = 4_000, cond: () -> Boolean) {
        val end = System.currentTimeMillis() + timeoutMs
        while (!cond()) {
            check(System.currentTimeMillis() < end) { "조건 대기 시간 초과" }
            Thread.sleep(10)
        }
    }

    @Test fun arrival_speaksReminderBeforeSummary_once_andMarksReminded() {
        store.candidates += todo(1)
        val c = controller()
        arrive(c)
        awaitUntil { out.said.isNotEmpty() }
        val first = out.said.single()
        assertTrue(first, first.startsWith("어제 퇴근길에 할 일 하나 말했어. 거래처 서류 다시 보내기. "))
        assertTrue(first, first.endsWith("오늘은 말이 없었네. 그래도 여기까지 왔어.")) // 요약이 뒤에 이어진다
        awaitUntil { store.remindedCalls.isNotEmpty() }
        assertEquals(listOf(listOf(1L)), store.remindedCalls.toList())
        assertTrue(traces.contains("remind speak n=1"))

        // 다시 듣기에는 리마인드를 붙이지 않는다
        c.skipSummary()
        awaitUntil { c.summaryText == null }
        c.replaySummary()
        awaitUntil { out.said.size == 2 }
        assertEquals("오늘은 말이 없었네. 그래도 여기까지 왔어.", out.said[1])
        assertEquals(1, store.remindedCalls.size)
        // 띠는 이번 카드 동안 남는다
        assertEquals(listOf(1L), runBlocking { c.loadReminders() }.map { it.itemId })
    }

    @Test fun currentSessionTodos_notReminded() {
        store.candidates += todo(1, session = 101) // 지금 세션(가짜 저장소의 첫 세션 id)
        val c = controller()
        arrive(c)
        awaitUntil { out.said.isNotEmpty() }
        assertEquals("오늘은 말이 없었네. 그래도 여기까지 왔어.", out.said.single())
        assertTrue(store.remindedCalls.isEmpty())
    }

    @Test fun gearDBeforeSpeaking_suppressed_thenSpokenOnNextParkedArrival() {
        store.candidates += todo(1)
        store.lookupDelayMs = 300
        val c = controller()
        arrive(c)
        c.post(SessionEvent.GearChanged(Gear.D)) // 조회가 끝나기 전에 다시 출발
        Thread.sleep(600)
        assertTrue("주행 중에는 말하지 않는다", out.said.isEmpty())
        assertTrue(store.remindedCalls.isEmpty())
        assertEquals(SessionState.SESSION_OPEN, c.state)

        c.post(SessionEvent.GearChanged(Gear.P))
        c.post(SessionEvent.Arrived)
        awaitUntil { out.said.isNotEmpty() }
        assertTrue(out.said.single().startsWith("어제 퇴근길에 할 일 하나 말했어."))
        awaitUntil { store.remindedCalls.size == 1 }
    }

    @Test fun nextSession_doesNotRepeatReminded() {
        store.candidates += todo(1)
        val c = controller()
        arrive(c)
        awaitUntil { store.remindedCalls.size == 1 }
        c.post(SessionEvent.CardClosed)
        c.post(SessionEvent.GearChanged(Gear.D)) // 같은 시동에서 새 세션
        c.post(SessionEvent.GearChanged(Gear.P))
        c.post(SessionEvent.Arrived)
        awaitUntil { out.said.size == 2 }
        assertEquals("오늘은 말이 없었네. 그래도 여기까지 왔어.", out.said[1])
        assertEquals(1, store.remindedCalls.size)
        assertTrue(runBlocking { c.loadReminders() }.isEmpty())
    }

    @Test fun markDone_removesFromStrip_andStores() {
        store.candidates += listOf(todo(1), todo(2, text = "우유 사기"))
        val c = controller()
        arrive(c)
        awaitUntil { out.said.isNotEmpty() }
        assertTrue(out.said.single().startsWith("어제 퇴근길에 할 일 둘 말했어. 거래처 서류 다시 보내기 외 1개."))
        c.markReminderDone(1)
        awaitUntil { store.doneIds.isNotEmpty() }
        assertEquals(listOf(2L), runBlocking { c.loadReminders() }.map { it.itemId })
        assertEquals(listOf(1L), store.doneIds.toList())
    }

    @Test fun parkFallbackCard_noDestination_noReminder() {
        store.candidates += todo(1)
        val c = controller()
        c.post(SessionEvent.DestinationChanged(DestinationState.None))
        c.post(SessionEvent.IgnitionOn)
        c.post(SessionEvent.GearChanged(Gear.D))
        c.post(SessionEvent.GearChanged(Gear.P))
        c.post(SessionEvent.ParkHeld5s) // D9 대체 판정: 목적지 없음 + P 5초
        awaitUntil { out.said.isNotEmpty() }
        assertEquals(SessionState.CARD, c.state)
        assertEquals("오늘은 말이 없었네. 그래도 여기까지 왔어.", out.said.single())
        assertTrue(store.remindedCalls.isEmpty())
        assertTrue(runBlocking { c.loadReminders() }.isEmpty())
    }

    @Test fun gateClosedInCard_noReminderSpoken_notMarked() {
        store.candidates += todo(1)
        val c = controller(gate = { false })
        arrive(c)
        awaitUntil { out.said.isNotEmpty() }
        assertEquals(SessionState.CARD, c.state)
        assertEquals("오늘은 말이 없었네. 그래도 여기까지 왔어.", out.said.single())
        Thread.sleep(200)
        assertTrue(store.remindedCalls.isEmpty())
    }

    @Test fun cardHidden_summaryAndReminderHeld_thenSpokenOnceOnReturn() {
        store.candidates += todo(1)
        val c = controller()
        c.holdSummary(true) // 지난 기록·설정이 카드를 가린 채 도착(늦은 커밋으로 요약이 시작되는 경우와 같다)
        arrive(c)
        awaitUntil { traces.contains("summary owed") }
        Thread.sleep(200)
        assertTrue("카드가 안 보이면 말하지 않는다", out.said.isEmpty())
        assertTrue(store.remindedCalls.isEmpty())
        assertEquals(null, c.summaryText)

        c.holdSummary(false) // 카드로 돌아옴
        awaitUntil { out.said.isNotEmpty() }
        assertTrue(out.said.single().startsWith("어제 퇴근길에 할 일 하나 말했어."))
        awaitUntil { store.remindedCalls.size == 1 }
        c.holdSummary(false) // 다시 그려도 한 번만
        Thread.sleep(200)
        assertEquals(1, out.said.size)
    }

    @Test fun holdWhileSpeaking_stops_andDoesNotReplayOnReturn() {
        val c = controller()
        arrive(c)
        awaitUntil { c.summaryText != null }
        c.holdSummary(true)
        awaitUntil { c.summaryText == null }
        c.holdSummary(false)
        Thread.sleep(200)
        assertEquals(1, out.said.size)
    }
}
