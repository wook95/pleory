package ai.pleos.hello.app

import ai.pleos.hello.core.CardModel
import ai.pleos.hello.core.Clock
import ai.pleos.hello.core.SessionCommand
import ai.pleos.hello.core.SessionEvent
import ai.pleos.hello.core.SessionState
import ai.pleos.hello.core.SessionEndCalc
import ai.pleos.hello.core.SessionStateMachine
import ai.pleos.hello.core.StructureContract
import ai.pleos.hello.core.StructureResult
import ai.pleos.hello.core.StructuredItem
import ai.pleos.hello.core.Structurer
import ai.pleos.hello.core.StructurerResult
import ai.pleos.hello.core.SummaryComposer
import ai.pleos.hello.core.SummaryItem
import ai.pleos.hello.data.LlmStatus
import ai.pleos.hello.data.PleoryStore
import ai.pleos.hello.data.Utterance
import ai.pleos.hello.platform.SpeechInput
import ai.pleos.hello.platform.SpeechInputListener
import ai.pleos.hello.platform.SpeechOutput
import ai.pleos.hello.BuildConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** 화면·요약 재생 쪽 부수효과. T6(UI)·요약 재생이 구현한다. 요약이 끝나면 [SessionController.post] 로 SummaryDone 을 보낸다. */
interface SessionOutputs {
    fun showS0()
    fun showCard()
    fun startSummary()
    fun stopSummary()

    /** 구조화 결과가 저장돼 카드를 다시 읽어야 할 때. */
    fun refreshCard() {}

    /** 요약 재생이 시작됐다. [text] 는 TTS 로 읽는 문장이자 자막. */
    fun summaryStarted(text: String) {}

    /** 요약 재생이 끝났다(끝까지·건너뛰기·15초 상한·기어 D 중단 모두). */
    fun summaryEnded() {}
}

/**
 * Application 범위 파이프라인(TRD D3). 모든 이벤트를 [dispatcher] 하나(단일 스레드여야 한다)에서
 * [SessionStateMachine] 에 넣고, 돌려받은 명령을 실행한다. 로직은 머신이 갖고 여기는 부수효과만 한다.
 * 머신의 utteranceId 는 이 컨트롤러가 DB id 로 대응시킨다(재처리는 -dbId 를 키로 쓴다).
 */
class SessionController(
    private val store: PleoryStore,
    private val structurer: Structurer,
    private val speechIn: SpeechInput,
    private val speechOut: SpeechOutput,
    private val outputs: SessionOutputs,
    private val context: ContextProvider,
    private val clock: Clock,
    dispatcher: CoroutineDispatcher,
    private val structureTimeoutMs: Long = 8_000,
    private val sttTimeoutMs: Long = 15_000,
    /** 디버그 로그 훅. 이벤트·명령을 한 줄씩 넘긴다(JVM 테스트에서 android.util.Log 를 피하려고 주입). */
    private val trace: (String) -> Unit = {},
    /** 컨트롤러 코루틴의 처리되지 않은 예외 로그 훅(앱이 죽지 않게 삼키고 남긴다). */
    private val logError: (String, Throwable) -> Unit = { _, _ -> },
) {
    private class Entry(val text: String, val dbId: CompletableDeferred<Long>) {
        var generation = 0
        var items: List<StructuredItem> = emptyList()

        /** Gleo 가 이미 구조화해 넘긴 항목. 있으면 구조화기를 부르지 않는다. */
        var preset: List<StructuredItem>? = null
    }

    private val scope = CoroutineScope(
        SupervisorJob() + dispatcher + CoroutineExceptionHandler { _, t -> logError("controller coroutine failed", t) },
    )
    private val machine = SessionStateMachine()
    private val entries = mutableMapOf<Long, Entry>()
    private var sessionId = CompletableDeferred<Long>()
    private var sessionStartedAt = 0L
    private var cardJob: Job? = null
    private var sttTimeoutJob: Job? = null
    private var sttTimeoutRound = -1
    private var summarySeq = 0
    private var summaryJob: Job? = null
    private var summarySpeaking = false
    private var sessionDeleted = false
    private var presetItems: List<StructuredItem>? = null

    /** 저장소에 쓰는 진행 중 작업. 전체 삭제가 이들이 끝난 뒤에 지우도록 추적한다. */
    private val dbJobs = java.util.concurrent.CopyOnWriteArraySet<Job>()

    private fun launchDb(block: suspend CoroutineScope.() -> Unit): Job {
        val job = scope.launch(block = block)
        dbJobs += job
        job.invokeOnCompletion { dbJobs -= job }
        return job
    }

    val state: SessionState get() = machine.state

    /** 요약을 재생 중이면 그 문장(자막), 아니면 null. 직렬 경로에서만 읽는다. */
    var summaryText: String? = null
        private set
    private var summaryHeld = false

    /** 이벤트 처리 직후 현재 상태를 알린다(UI 갱신용). 직렬 경로에서 불린다. */
    var onStateChanged: ((SessionState) -> Unit)? = null

    init {
        speechIn.listener = object : SpeechInputListener {
            override fun onFinal(text: String, round: Int) = post(SessionEvent.SttFinal(text, round))
            override fun onError(message: String, round: Int) = post(SessionEvent.SttFailed(round))
        }
        speechOut.onDone = {
            scope.launch {
                trace("tts onDone")
                if (summarySpeaking) endSummary(stopSpeech = false, notifyDone = true)
            }
        }
    }

    /** 어느 스레드에서든 호출 가능. 직렬 경로로 넘긴다. */
    fun post(event: SessionEvent) {
        scope.launch { dispatch(event) }
    }

    private fun dispatch(event: SessionEvent) {
        trace("event ${redact(event)}")
        // 끝난 회차의 STT 상한 타이머가 남아 있지 않게 한다(다른 회차의 타이머는 건드리지 않는다).
        val endedRound = when (event) {
            is SessionEvent.SttFinal -> event.round
            is SessionEvent.SttFailed -> event.round
            else -> -1
        }
        if (endedRound == sttTimeoutRound) cancelSttTimeout()
        run(machine.handle(event))
        onStateChanged?.invoke(machine.state)
    }

    /**
     * Gleo 음성 액션으로 받은 메모를 STT 최종 결과와 같은 경로로 저장한다. 머신이 받지 않을 상태면 false
     * (호출자가 Gleo 에 실패로 회신한다). 어느 스레드에서든 부를 수 있다.
     */
    fun addNoteFromVoice(text: String): Boolean {
        if (!machine.acceptsExternalNote(text)) return false
        post(SessionEvent.ExternalNote(text))
        return true
    }

    /**
     * Gleo 가 구조화까지 해서 넘긴 메모(ADD_DRIVE_NOTES). [itemsJson] 은 PRD §4-1 의 items 배열 JSON 이다.
     * 계약에 맞으면 구조화기(Gemini)를 건너뛰고 그 항목으로 커밋하고, 안 맞으면 원문만 받은 것처럼 처리한다.
     */
    fun addStructuredNoteFromVoice(original: String, itemsJson: String): Boolean {
        val parsed = StructureContract.parse("{\"items\":$itemsJson}")
        if (parsed !is StructureResult.Ok) {
            // 거절 사유에 모델 값이 섞일 수 있어 릴리스 로그에는 남기지 않는다
            trace(if (BuildConfig.DEBUG) "gleo items rejected: ${(parsed as StructureResult.Failed).reason}" else "gleo items rejected")
            return addNoteFromVoice(original)
        }
        if (!machine.acceptsExternalNote(original)) return false
        scope.launch {
            presetItems = parsed.items
            dispatch(SessionEvent.ExternalNote(original))
            presetItems = null
        }
        return true
    }

    /** 이전에 PENDING 으로 남은 발화를 다시 구조화한다(다음 시동, D12). */
    fun reprocessPending() {
        scope.launch {
            store.pendingUtterances().forEach { reprocess(it.id, it.text) }
        }
    }

    /** 발화 [dbId] 를 다시 구조화한다. 결과는 기존 Item 을 지우고 대체한다. */
    fun reprocess(dbId: Long, text: String) {
        scope.launch {
            val key = -dbId
            entries[key] = Entry(text, CompletableDeferred(dbId))
            run(machine.handle(SessionEvent.RetryStructure(key)))
        }
    }

    /** 카드의 "건너뛰기". 재생 중일 때만 의미가 있다. */
    fun skipSummary() {
        scope.launch {
            if (summaryText != null) {
                trace("summary skipped")
                endSummary(stopSpeech = true, notifyDone = true)
            }
        }
    }

    /** 동의 화면이 카드를 가리는 동안 요약을 막는다. 재생 중이거나 시작 대기 중이면 끝낸다. 풀린 뒤 재생은 화면이 [replaySummary] 로 한다. */
    fun holdSummary(hold: Boolean) {
        scope.launch {
            summaryHeld = hold
            if (hold && summaryJob != null) {
                trace("summary held")
                endSummary(stopSpeech = true, notifyDone = true)
            }
        }
    }

    /** 카드의 "15초 요약 듣기". 카드 상태에서 재생 중이 아닐 때만 다시 읽는다. */
    fun replaySummary() {
        scope.launch { if (machine.state == SessionState.CARD && summaryText == null) playSummary() }
    }

    /**
     * 모든 기록을 지운다(F12). 진행 중인 세션 행도 함께 지워지므로 이어지는 저장이 FK 에 걸리지 않게
     * 대기 중인 발화 맵을 먼저 비워 새 커밋을 막고, 이미 저장소에 쓰는 중인 작업이 끝나길 기다린 뒤 지운다.
     * 다음 저장 때 같은 세션을 빈 행으로 다시 만든다. 끝나면 [onDone] 을 직렬 경로에서 부른다.
     * (구조화 호출은 저장소를 건드리지 않고 결과 커밋은 맵이 비어 무시되므로 기다리지 않는다.)
     */
    fun deleteAllData(onDone: () -> Unit = {}) {
        scope.launch {
            entries.clear()
            dbJobs.toList().joinAll()
            store.deleteAll()
            sessionDeleted = true
            trace("all data deleted")
            onDone()
        }
    }

    /** 카드 표시용 모델. 종료값 저장이 끝난 뒤에 읽는다. */
    suspend fun loadCard(): CardModel? {
        cardJob?.join()
        return store.loadCard(sessionId.await())
    }

    /**
     * Gleo SHOW_TODAY_CARD 로 연 카드. 도착 카드 상태이거나 지금 세션에 한마디가 있으면 지금 세션,
     * 아니면(방금 시작한 빈 세션) 마지막으로 끝난 세션의 카드. 끝난 세션도 없으면 지금 세션.
     */
    suspend fun loadTodayCard(): CardModel? {
        cardJob?.join()
        val sid = sessionId.await()
        val current = store.loadCard(sid)
        if (machine.state == SessionState.CARD || current?.utterances?.isNotEmpty() == true) return current
        return store.latestEndedSession(sid)?.let { store.loadCard(it) } ?: current
    }

    private fun run(commands: List<SessionCommand>) {
        commands.forEach(::execute)
    }

    /** 전체 삭제로 세션 행이 사라졌다면 현재 세션을 같은 시작 시각으로 다시 만든다(저장 직전에만 불린다). */
    private fun renewSessionIfDeleted() {
        if (!sessionDeleted) return
        sessionDeleted = false
        val d = CompletableDeferred<Long>()
        sessionId = d
        cardJob = null
        val startedAt = sessionStartedAt
        launchDb { d.complete(store.createSession(context.profileId(), context.segment(), startedAt)) }
    }

    private fun execute(c: SessionCommand) {
        trace("command ${redact(c)}")
        when (c) {
            SessionCommand.StopSpeech -> speechOut.stop()
            is SessionCommand.StartStt -> {
                speechIn.start(c.round)
                cancelSttTimeout()
                sttTimeoutRound = c.round
                sttTimeoutJob = scope.launch {
                    delay(sttTimeoutMs)
                    post(SessionEvent.SttTimeout(c.round))
                }
            }
            is SessionCommand.CancelStt -> {
                if (c.round == sttTimeoutRound) cancelSttTimeout()
                speechIn.cancel()
            }
            is SessionCommand.Say -> speechOut.say(c.text)
            is SessionCommand.SaveUtterance -> {
                renewSessionIfDeleted()
                val entry = Entry(c.text, CompletableDeferred())
                entry.preset = presetItems
                entries[c.utteranceId] = entry
                val session = sessionId
                val snap = context.snapshot()
                val at = clock.nowMillis()
                launchDb {
                    entry.dbId.complete(
                        store.saveUtterance(
                            Utterance(
                                sessionId = session.await(), at = at, text = c.text,
                                lat = snap.lat, lng = snap.lng, placeLabel = snap.placeLabel,
                                odometerKm = snap.odometerKm, speedKmh = snap.speedKmh, congested = snap.congested,
                            ),
                        ),
                    )
                }
            }
            is SessionCommand.StartStructure -> {
                val entry = entries[c.utteranceId] ?: return
                entry.generation = c.generation
                entry.preset?.let { preset ->
                    entry.preset = null
                    entry.items = preset
                    post(SessionEvent.LlmDone(c.utteranceId, c.generation))
                    return
                }
                scope.launch {
                    val result = withTimeoutOrNull(structureTimeoutMs) { structurer.structure(entry.text) }
                    val parsed = (result as? StructurerResult.Ok)?.let { StructureContract.parse(it.rawJsonText) }
                    if (parsed is StructureResult.Ok && entry.generation == c.generation) entry.items = parsed.items
                    post(
                        when {
                            result == null -> SessionEvent.LlmFailed(c.utteranceId, c.generation)
                            result is StructurerResult.NetworkUnavailable -> SessionEvent.LlmOffline(c.utteranceId, c.generation)
                            parsed is StructureResult.Ok -> SessionEvent.LlmDone(c.utteranceId, c.generation)
                            else -> SessionEvent.LlmFailed(c.utteranceId, c.generation)
                        },
                    )
                }
            }
            is SessionCommand.CommitStructure -> commit(c.utteranceId, LlmStatus.DONE)
            is SessionCommand.CommitRaw -> commit(c.utteranceId, LlmStatus.FAILED)
            is SessionCommand.KeepPending -> Unit // PENDING 은 저장 시점의 값이 그대로 유지된다
            SessionCommand.ShowS0 -> {
                endSummary(stopSpeech = true, notifyDone = false)
                outputs.showS0()
            }
            SessionCommand.ShowCard -> {
                renewSessionIfDeleted()
                val session = sessionId
                val endedAt = clock.nowMillis()
                val info = context.endInfo()
                val end = SessionEndCalc.compute(sessionStartedAt, endedAt, info)
                trace("session end $end")
                cardJob = launchDb {
                    store.finishSession(session.await(), endedAt, end, SessionEndCalc.downsample(info.points))
                }
                outputs.showCard()
            }
            SessionCommand.StartSummary -> {
                outputs.startSummary()
                playSummary()
            }
            SessionCommand.StopSummary -> {
                endSummary(stopSpeech = true, notifyDone = false)
                outputs.stopSummary()
            }
            SessionCommand.NewSession -> {
                val d = CompletableDeferred<Long>()
                sessionId = d
                cardJob = null
                val startedAt = clock.nowMillis()
                sessionStartedAt = startedAt
                context.onSessionStart()
                launchDb { d.complete(store.createSession(context.profileId(), context.segment(), startedAt)) }
            }
            SessionCommand.CloseSession -> {
                endSummary(stopSpeech = true, notifyDone = false)
                val session = sessionId
                val endedAt = clock.nowMillis()
                launchDb { store.closeSession(session.await(), endedAt) }
            }
        }
    }

    private fun commit(key: Long, status: LlmStatus) {
        val entry = entries[key] ?: return
        launchDb {
            val items = if (status == LlmStatus.DONE) entry.items else listOf(StructureContract.rawFallback(entry.text))
            store.replaceItems(entry.dbId.await(), items, status)
            trace("committed $status items=${items.size} dbId=${entry.dbId.await()}")
            outputs.refreshCard()
        }
    }

    private fun cancelSttTimeout() {
        sttTimeoutJob?.cancel()
        sttTimeoutJob = null
        sttTimeoutRound = -1
    }

    /** 요약 문장을 만들어 TTS 로 읽는다. 15초 상한을 넘으면 끊는다(PRD §4-2). */
    private fun playSummary() {
        val seq = ++summarySeq
        summaryJob?.cancel()
        val session = sessionId
        summaryJob = scope.launch {
            try {
                val sid = session.await()
                cardJob?.join()
                val items = store.loadCard(sid)?.items.orEmpty().map { SummaryItem(it.type, it.emotion, it.keywords) }
                val text = SummaryComposer.compose(items)
                if (seq != summarySeq) return@launch
                store.setSummaryText(sid, text)
                if (seq != summarySeq) return@launch
                if (summaryHeld) {
                    endSummary(stopSpeech = false, notifyDone = true)
                    return@launch
                }
                summaryText = text
                summarySpeaking = true
                if (BuildConfig.DEBUG) trace("summary speak: $text")
                outputs.summaryStarted(text)
                speechOut.say(text)
                delay(SUMMARY_MAX_MS)
                if (seq == summarySeq) {
                    trace("summary 15s hard stop")
                    endSummary(stopSpeech = true, notifyDone = true)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // 요약이 실패해도 상태 머신이 SummaryDone 을 기다리며 멈추지 않게 끝낸다
                logError("summary playback failed", e)
                if (seq == summarySeq) endSummary(stopSpeech = true, notifyDone = true)
            }
        }
    }

    /** 재생 중이던 요약을 정리한다. 대기 중인 시작 요청도 무효로 만든다. */
    private fun endSummary(stopSpeech: Boolean, notifyDone: Boolean) {
        summarySeq++
        summaryJob?.cancel()
        summaryJob = null
        val wasPlaying = summaryText != null
        summaryText = null
        summarySpeaking = false
        if (stopSpeech && wasPlaying) speechOut.stop()
        if (wasPlaying) outputs.summaryEnded()
        if (notifyDone) post(SessionEvent.SummaryDone)
    }

    /** 릴리스 로그에는 발화·요약 본문이 남지 않게 이벤트·명령 이름만 남긴다. */
    private fun redact(x: Any) = if (BuildConfig.DEBUG) x.toString() else x::class.simpleName ?: "?"

    private companion object {
        const val SUMMARY_MAX_MS = 15_000L
    }
}
