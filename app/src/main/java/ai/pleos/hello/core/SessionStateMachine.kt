package ai.pleos.hello.core

enum class SessionState { OFF, SESSION_OPEN, LISTENING, CARD, CLOSED }

sealed interface SessionEvent {
    data object IgnitionOn : SessionEvent
    data object IgnitionOff : SessionEvent
    data class GearChanged(val gear: Gear) : SessionEvent
    data object TalkTapped : SessionEvent
    data class SttFinal(val text: String, val round: Int) : SessionEvent
    data class SttFailed(val round: Int) : SessionEvent
    data class SttTimeout(val round: Int) : SessionEvent
    data class DestinationChanged(val state: DestinationState) : SessionEvent
    data object Arrived : SessionEvent
    data object LocationNearDest : SessionEvent
    data object ParkHeld5s : SessionEvent
    data object CardClosed : SessionEvent
    data class LlmDone(val utteranceId: Long, val generation: Int) : SessionEvent
    /** 응답 불량·8초 타임아웃(D12: FAILED + RAW). */
    data class LlmFailed(val utteranceId: Long, val generation: Int) : SessionEvent
    /** 네트워크 없음(D12: PENDING 유지). */
    data class LlmOffline(val utteranceId: Long, val generation: Int) : SessionEvent
    /** 다음 시동 재처리 등. 같은 발화의 이전 세대는 무효가 된다. */
    data class RetryStructure(val utteranceId: Long) : SessionEvent
    data object SummaryDone : SessionEvent
}

sealed interface SessionCommand {
    data object StopSpeech : SessionCommand
    data class StartStt(val round: Int) : SessionCommand
    data class CancelStt(val round: Int) : SessionCommand
    data class SaveUtterance(val utteranceId: Long, val text: String, val round: Int) : SessionCommand
    data class Say(val text: String) : SessionCommand
    data class StartStructure(val utteranceId: Long, val generation: Int) : SessionCommand
    /** Item n개 저장 + DONE (기존 Item 삭제와 한 트랜잭션). */
    data class CommitStructure(val utteranceId: Long) : SessionCommand
    /** RAW 1개 + FAILED. */
    data class CommitRaw(val utteranceId: Long) : SessionCommand
    /** PENDING 유지. */
    data class KeepPending(val utteranceId: Long) : SessionCommand
    data object ShowS0 : SessionCommand
    data object ShowCard : SessionCommand
    data object StartSummary : SessionCommand
    data object StopSummary : SessionCommand
    data object NewSession : SessionCommand
    data object CloseSession : SessionCommand
}

/**
 * PRD §1 상태 머신. 호출자가 이벤트를 직렬화한다(TRD D3). 순수 Kotlin, 스레드 안전하지 않음.
 * 회차(round)·세대(generation) 카운터를 안에서 관리하고, 오래된 결과는 버린다.
 */
class SessionStateMachine {
    var state: SessionState = SessionState.OFF
        private set

    private val trigger = CardTrigger()
    private var ignitionOn = false
    private var roundCounter = 0
    private var activeRound: Int? = null
    private var generationCounter = 0
    private var utteranceCounter = 0L
    private val inFlight = mutableMapOf<Long, Int>() // utteranceId -> 유효한 generation
    private var summaryWaiting = false
    private var summaryPlaying = false

    fun handle(event: SessionEvent): List<SessionCommand> {
        val out = mutableListOf<SessionCommand>()
        when (event) {
            SessionEvent.IgnitionOn -> {
                ignitionOn = true
                if (state == SessionState.OFF || state == SessionState.CLOSED) newSession(out)
            }
            SessionEvent.IgnitionOff -> {
                ignitionOn = false
                if (state != SessionState.OFF && state != SessionState.CLOSED) {
                    cancelListening(out)
                    stopSummary(out)
                    out += SessionCommand.CloseSession
                    state = if (state == SessionState.CARD) SessionState.CLOSED else SessionState.OFF
                }
            }
            is SessionEvent.GearChanged -> onGear(event.gear, out)
            SessionEvent.TalkTapped -> onTalk(out)
            is SessionEvent.SttFinal -> onSttFinal(event, out)
            is SessionEvent.SttFailed -> if (isActive(event.round)) endListening()
            is SessionEvent.SttTimeout -> if (isActive(event.round)) {
                out += SessionCommand.CancelStt(event.round)
                endListening()
            }
            is SessionEvent.DestinationChanged -> {
                trigger.onDestination(event.state)
                evaluate(out)
            }
            SessionEvent.Arrived -> { trigger.onArrived(); evaluate(out) }
            SessionEvent.LocationNearDest -> { trigger.onLocationNearDest(); evaluate(out) }
            SessionEvent.ParkHeld5s -> { trigger.onParkHeld5s(); evaluate(out) }
            SessionEvent.CardClosed -> if (state == SessionState.CARD) {
                stopSummary(out)
                out += SessionCommand.CloseSession
                state = SessionState.CLOSED
            }
            is SessionEvent.LlmDone -> onLlm(event.utteranceId, event.generation, SessionCommand.CommitStructure(event.utteranceId), out)
            is SessionEvent.LlmFailed -> onLlm(event.utteranceId, event.generation, SessionCommand.CommitRaw(event.utteranceId), out)
            is SessionEvent.LlmOffline -> onLlm(event.utteranceId, event.generation, SessionCommand.KeepPending(event.utteranceId), out)
            is SessionEvent.RetryStructure -> startStructure(event.utteranceId, out)
            SessionEvent.SummaryDone -> summaryPlaying = false
        }
        return out
    }

    private fun onGear(gear: Gear, out: MutableList<SessionCommand>) {
        trigger.onGear(gear)
        if (gear == Gear.P) {
            if (state == SessionState.LISTENING) {
                cancelListening(out)
                state = SessionState.SESSION_OPEN
            }
            evaluate(out)
            return
        }
        when (state) {
            SessionState.CARD -> {
                stopSummary(out)
                out += SessionCommand.ShowS0
                state = SessionState.SESSION_OPEN
            }
            SessionState.CLOSED -> if (ignitionOn) newSession(out)
            else -> Unit
        }
    }

    private fun onTalk(out: MutableList<SessionCommand>) {
        when (state) {
            SessionState.SESSION_OPEN -> {
                val round = ++roundCounter
                activeRound = round
                out += SessionCommand.StopSpeech
                out += SessionCommand.StartStt(round)
                state = SessionState.LISTENING
            }
            SessionState.LISTENING -> {
                cancelListening(out)
                state = SessionState.SESSION_OPEN
            }
            else -> Unit
        }
    }

    private fun onSttFinal(e: SessionEvent.SttFinal, out: MutableList<SessionCommand>) {
        if (!isActive(e.round)) return
        endListening()
        if (e.text.isBlank()) return
        val id = ++utteranceCounter
        out += SessionCommand.SaveUtterance(id, e.text, e.round)
        out += SessionCommand.Say("적어뒀어")
        startStructure(id, out)
    }

    private fun startStructure(id: Long, out: MutableList<SessionCommand>) {
        val gen = ++generationCounter
        inFlight[id] = gen
        out += SessionCommand.StartStructure(id, gen)
    }

    private fun onLlm(id: Long, gen: Int, commit: SessionCommand, out: MutableList<SessionCommand>) {
        if (inFlight[id] != gen) return // 만료·중복·재처리로 대체된 세대
        inFlight.remove(id)
        out += commit
        if (summaryWaiting && inFlight.isEmpty()) startSummary(out)
    }

    private fun evaluate(out: MutableList<SessionCommand>) {
        if (state != SessionState.SESSION_OPEN || !trigger.fired()) return
        state = SessionState.CARD
        out += SessionCommand.ShowCard
        if (inFlight.isEmpty()) startSummary(out) else summaryWaiting = true
    }

    private fun startSummary(out: MutableList<SessionCommand>) {
        summaryWaiting = false
        summaryPlaying = true
        out += SessionCommand.StartSummary
    }

    private fun stopSummary(out: MutableList<SessionCommand>) {
        if (summaryPlaying) out += SessionCommand.StopSummary
        summaryWaiting = false
        summaryPlaying = false
    }

    private fun cancelListening(out: MutableList<SessionCommand>) {
        val r = activeRound ?: return
        out += SessionCommand.CancelStt(r)
        endListening()
    }

    private fun endListening() {
        activeRound = null
        if (state == SessionState.LISTENING) state = SessionState.SESSION_OPEN
    }

    private fun isActive(round: Int) = state == SessionState.LISTENING && activeRound == round

    private fun newSession(out: MutableList<SessionCommand>) {
        trigger.resetFlags()
        activeRound = null
        summaryWaiting = false
        summaryPlaying = false
        state = SessionState.SESSION_OPEN
        out += SessionCommand.NewSession
        out += SessionCommand.ShowS0
    }
}
