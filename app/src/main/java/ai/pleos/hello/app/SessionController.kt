package ai.pleos.hello.app

import ai.pleos.hello.core.Clock
import ai.pleos.hello.core.SessionCommand
import ai.pleos.hello.core.SessionEvent
import ai.pleos.hello.core.SessionState
import ai.pleos.hello.core.SessionStateMachine
import ai.pleos.hello.core.StructureContract
import ai.pleos.hello.core.StructureResult
import ai.pleos.hello.core.StructuredItem
import ai.pleos.hello.core.Structurer
import ai.pleos.hello.core.StructurerResult
import ai.pleos.hello.data.LlmStatus
import ai.pleos.hello.data.PleoryStore
import ai.pleos.hello.data.SessionState as DbSessionState
import ai.pleos.hello.data.Utterance
import ai.pleos.hello.platform.SpeechInput
import ai.pleos.hello.platform.SpeechInputListener
import ai.pleos.hello.platform.SpeechOutput
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** 화면·요약 재생 쪽 부수효과. T6(UI)·요약 재생이 구현한다. 요약이 끝나면 [SessionController.post] 로 SummaryDone 을 보낸다. */
interface SessionOutputs {
    fun showS0()
    fun showCard()
    fun startSummary()
    fun stopSummary()
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
) {
    private class Entry(val text: String, val dbId: CompletableDeferred<Long>) {
        var generation = 0
        var items: List<StructuredItem> = emptyList()
    }

    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val machine = SessionStateMachine()
    private val entries = mutableMapOf<Long, Entry>()
    private var sessionId = CompletableDeferred<Long>()

    val state: SessionState get() = machine.state

    init {
        speechIn.listener = object : SpeechInputListener {
            override fun onFinal(text: String, round: Int) = post(SessionEvent.SttFinal(text, round))
            override fun onError(message: String, round: Int) = post(SessionEvent.SttFailed(round))
        }
    }

    /** 어느 스레드에서든 호출 가능. 직렬 경로로 넘긴다. */
    fun post(event: SessionEvent) {
        scope.launch { run(machine.handle(event)) }
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

    fun shutdown() {
        scope.coroutineContext[Job]?.cancel()
    }

    private fun run(commands: List<SessionCommand>) {
        commands.forEach(::execute)
    }

    private fun execute(c: SessionCommand) {
        when (c) {
            SessionCommand.StopSpeech -> speechOut.stop()
            is SessionCommand.StartStt -> {
                speechIn.start(c.round)
                scope.launch {
                    delay(sttTimeoutMs)
                    post(SessionEvent.SttTimeout(c.round))
                }
            }
            is SessionCommand.CancelStt -> speechIn.cancel()
            is SessionCommand.Say -> speechOut.say(c.text)
            is SessionCommand.SaveUtterance -> {
                val entry = Entry(c.text, CompletableDeferred())
                entries[c.utteranceId] = entry
                val session = sessionId
                val snap = context.snapshot()
                val at = clock.nowMillis()
                scope.launch {
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
            SessionCommand.ShowS0 -> outputs.showS0()
            SessionCommand.ShowCard -> {
                outputs.showCard()
                val session = sessionId
                scope.launch { store.setSessionState(session.await(), DbSessionState.CARD) }
            }
            SessionCommand.StartSummary -> outputs.startSummary()
            SessionCommand.StopSummary -> outputs.stopSummary()
            SessionCommand.NewSession -> {
                val d = CompletableDeferred<Long>()
                sessionId = d
                val startedAt = clock.nowMillis()
                scope.launch { d.complete(store.createSession(context.profileId(), context.segment(), startedAt)) }
            }
            SessionCommand.CloseSession -> {
                val session = sessionId
                val endedAt = clock.nowMillis()
                scope.launch { store.closeSession(session.await(), endedAt) }
            }
        }
    }

    private fun commit(key: Long, status: LlmStatus) {
        val entry = entries[key] ?: return
        scope.launch {
            val items = if (status == LlmStatus.DONE) entry.items else listOf(StructureContract.rawFallback(entry.text))
            store.replaceItems(entry.dbId.await(), items, status)
        }
    }
}
