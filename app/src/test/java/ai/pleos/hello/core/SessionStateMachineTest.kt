package ai.pleos.hello.core

import ai.pleos.hello.core.SessionCommand.*
import ai.pleos.hello.core.SessionEvent.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionStateMachineTest {
    private val m = SessionStateMachine()

    private fun send(vararg e: SessionEvent): List<SessionCommand> = e.flatMap { m.handle(it) }

    private fun open() = send(IgnitionOn, GearChanged(Gear.D))

    private fun startListening(): Int {
        val cmds = send(TalkTapped)
        return (cmds.filterIsInstance<StartStt>().single()).round
    }

    private fun arriveAtKnownDest() = send(
        DestinationChanged(DestinationState.Known), GearChanged(Gear.P), Arrived,
    )

    // --- 기본 흐름

    @Test fun 시동_ON은_새_세션과_S0() {
        val c = send(IgnitionOn)
        assertEquals(SessionState.SESSION_OPEN, m.state)
        assertEquals(listOf(NewSession, ShowS0), c)
    }

    @Test fun 탭하면_TTS를_멈추고_STT_시작() {
        open()
        val c = send(TalkTapped)
        assertEquals(listOf(StopSpeech, StartStt(1)), c)
        assertEquals(SessionState.LISTENING, m.state)
    }

    @Test fun 유효한_final은_저장_적어뒀어_구조화_시작() {
        open()
        val r = startListening()
        val c = send(SttFinal("우유 사기", r))
        assertEquals(listOf(SaveUtterance(1, "우유 사기", r), Say("적어뒀어"), StartStructure(1, 1)), c)
        assertEquals(SessionState.SESSION_OPEN, m.state)
    }

    @Test fun 외부_메모는_STT_없이_저장_구조화하고_말하지_않음() {
        open()
        val c = send(ExternalNote("우유 사기"))
        assertEquals(listOf(SaveUtterance(1, "우유 사기", 0), StartStructure(1, 1)), c)
        assertEquals(SessionState.SESSION_OPEN, m.state)
    }

    @Test fun 외부_메모는_듣는_중이면_말하지_않고_듣기를_유지() {
        open()
        startListening()
        val c = send(ExternalNote("우유 사기"))
        assertEquals(listOf(SaveUtterance(1, "우유 사기", 0), StartStructure(1, 1)), c)
        assertEquals(SessionState.LISTENING, m.state)
    }

    @Test fun 외부_메모는_세션이_없거나_빈_문자열이면_무시() {
        assertTrue(send(ExternalNote("메모")).isEmpty())
        open()
        assertTrue(send(ExternalNote("  ")).isEmpty())
    }

    @Test fun 외부_메모는_카드_표시_중에도_현재_카드_세션에_붙인다() {
        open()
        arriveAtKnownDest()
        assertEquals(SessionState.CARD, m.state)
        assertTrue(m.acceptsExternalNote("우유 사기"))
        val c = send(ExternalNote("우유 사기"))
        assertEquals(listOf(SaveUtterance(1, "우유 사기", 0), StartStructure(1, 1)), c)
        assertEquals(SessionState.CARD, m.state)
    }

    @Test fun 외부_메모는_카드를_닫은_뒤_시동이_켜져_있으면_새_세션을_열고_받는다() {
        open()
        arriveAtKnownDest()
        send(CardClosed)
        assertEquals(SessionState.CLOSED, m.state)
        assertTrue(m.acceptsExternalNote("우유 사기"))
        val c = send(ExternalNote("우유 사기"))
        assertEquals(listOf(NewSession, ShowS0, SaveUtterance(1, "우유 사기", 0), StartStructure(1, 1)), c)
        assertEquals(SessionState.SESSION_OPEN, m.state)
    }

    @Test fun 외부_메모는_시동이_꺼졌으면_받지_않는다() {
        assertFalse(m.acceptsExternalNote("메모")) // OFF
        open()
        arriveAtKnownDest()
        send(IgnitionOff)
        assertEquals(SessionState.CLOSED, m.state)
        assertFalse(m.acceptsExternalNote("메모"))
        assertTrue(send(ExternalNote("메모")).isEmpty())
        assertFalse(m.acceptsExternalNote(" "))
    }

    // --- §2-4 빠뜨리기 쉬운 전이

    @Test fun LISTENING_중_시동_OFF는_취소하고_저장_없음() {
        open()
        val r = startListening()
        val c = send(IgnitionOff)
        assertTrue(CancelStt(r) in c)
        assertEquals(SessionState.OFF, m.state)
        assertTrue(send(SttFinal("늦은", r)).isEmpty())
    }

    @Test fun LISTENING_중_기어_P는_취소하고_저장_없음() {
        open()
        val r = startListening()
        val c = send(GearChanged(Gear.P))
        assertEquals(listOf(CancelStt(r)), c)
        assertEquals(SessionState.SESSION_OPEN, m.state)
        assertTrue(send(SttFinal("늦은", r)).isEmpty())
    }

    @Test fun LISTENING_중_재탭은_취소() {
        open()
        val r = startListening()
        val c = send(TalkTapped)
        assertEquals(listOf(CancelStt(r)), c)
        assertEquals(SessionState.SESSION_OPEN, m.state)
    }

    @Test fun 취소_직후_final이_와도_저장_TTS_없음() {
        open()
        val r = startListening()
        send(TalkTapped)
        assertTrue(send(SttFinal("뒤늦게", r)).isEmpty())
    }

    @Test fun 취소한_회차의_final은_다음_회차를_방해하지_않음() {
        open()
        val r1 = startListening()
        send(TalkTapped)
        val r2 = startListening()
        assertTrue(send(SttFinal("옛날", r1)).isEmpty())
        assertEquals(SessionState.LISTENING, m.state)
        assertTrue(send(SttFinal("지금", r2)).any { it is SaveUtterance })
    }

    @Test fun 빈_STT는_저장하지_않음() {
        open()
        val r = startListening()
        assertTrue(send(SttFinal("   ", r)).isEmpty())
        assertEquals(SessionState.SESSION_OPEN, m.state)
    }

    @Test fun STT_실패와_타임아웃은_저장_없이_복귀() {
        open()
        val r1 = startListening()
        assertTrue(send(SttFailed(r1)).isEmpty())
        assertEquals(SessionState.SESSION_OPEN, m.state)
        val r2 = startListening()
        assertEquals(listOf(CancelStt(r2)), send(SttTimeout(r2)))
        assertEquals(SessionState.SESSION_OPEN, m.state)
        assertTrue(send(SttFinal("늦은", r2)).isEmpty())
    }

    @Test fun final이_두_번_오면_1건만_저장() {
        open()
        val r = startListening()
        val first = send(SttFinal("하나", r))
        val second = send(SttFinal("하나", r))
        assertEquals(1, first.filterIsInstance<SaveUtterance>().size)
        assertTrue(second.isEmpty())
    }

    @Test fun CARD_중_기어_D는_S0로_같은_세션() {
        open()
        arriveAtKnownDest()
        assertEquals(SessionState.CARD, m.state)
        val c = send(GearChanged(Gear.D))
        assertTrue(ShowS0 in c)
        assertTrue(NewSession !in c)
        assertTrue(CloseSession !in c)
        assertEquals(SessionState.SESSION_OPEN, m.state)
    }

    @Test fun CLOSED_후_재출발은_새_세션() {
        open()
        arriveAtKnownDest()
        send(CardClosed)
        assertEquals(SessionState.CLOSED, m.state)
        val c = send(GearChanged(Gear.D))
        assertEquals(listOf(NewSession, ShowS0), c)
        assertEquals(SessionState.SESSION_OPEN, m.state)
    }

    @Test fun 시동_OFF_후_ON도_새_세션이고_OFF에서_기어는_세션을_열지_않음() {
        open()
        arriveAtKnownDest()
        send(IgnitionOff)
        assertEquals(SessionState.CLOSED, m.state)
        assertTrue(send(GearChanged(Gear.D)).isEmpty())
        assertEquals(listOf(NewSession, ShowS0), send(IgnitionOn))
    }

    @Test fun 중간_정차는_SESSION_OPEN_유지() {
        open()
        send(DestinationChanged(DestinationState.Known))
        val c = send(GearChanged(Gear.P), ParkHeld5s)
        assertTrue(c.isEmpty())
        assertEquals(SessionState.SESSION_OPEN, m.state)
    }

    @Test fun 목적지_미확인의_P_5초는_카드_없음() {
        open()
        val c = send(DestinationChanged(DestinationState.Unknown), GearChanged(Gear.P), ParkHeld5s)
        assertTrue(c.isEmpty())
        assertEquals(SessionState.SESSION_OPEN, m.state)
    }

    @Test fun 목적지_없음_확정의_P_5초는_카드() {
        open()
        val c = send(DestinationChanged(DestinationState.None), GearChanged(Gear.P), ParkHeld5s)
        assertEquals(listOf(ShowCard, StartSummary), c)
        assertEquals(SessionState.CARD, m.state)
    }

    @Test fun 도착_후_기어_P_순서여도_카드() {
        open()
        send(DestinationChanged(DestinationState.Known), Arrived)
        assertEquals(SessionState.SESSION_OPEN, m.state)
        assertTrue(ShowCard in send(GearChanged(Gear.P)))
    }

    @Test fun 근접_300m_신호로_카드() {
        open()
        val c = send(DestinationChanged(DestinationState.Known), GearChanged(Gear.P), LocationNearDest)
        assertTrue(ShowCard in c)
    }

    @Test fun 카드_닫기는_CLOSED() {
        open()
        arriveAtKnownDest()
        val c = send(CardClosed)
        assertTrue(CloseSession in c)
        assertEquals(SessionState.CLOSED, m.state)
    }

    // --- LLM 세대 규칙

    private fun utterance(text: String = "메모"): Long {
        val r = startListening()
        return send(SttFinal(text, r)).filterIsInstance<StartStructure>().single().utteranceId
    }

    @Test fun LLM_성공은_구조화_커밋() {
        open()
        val id = utterance()
        assertEquals(listOf(CommitStructure(id)), send(LlmDone(id, 1)))
    }

    @Test fun 타임아웃_폴백_후_늦은_성공은_폐기() {
        open()
        val id = utterance()
        assertEquals(listOf(CommitRaw(id)), send(LlmFailed(id, 1)))
        assertTrue(send(LlmDone(id, 1)).isEmpty())
    }

    @Test fun 성공_후_늦은_실패는_폐기() {
        open()
        val id = utterance()
        send(LlmDone(id, 1))
        assertTrue(send(LlmFailed(id, 1)).isEmpty())
    }

    @Test fun 같은_결과가_두_번_와도_한_번만_커밋() {
        open()
        val id = utterance()
        assertEquals(1, send(LlmDone(id, 1), LlmDone(id, 1)).size)
    }

    @Test fun 오프라인은_PENDING_유지() {
        open()
        val id = utterance()
        assertEquals(listOf(KeepPending(id)), send(LlmOffline(id, 1)))
    }

    @Test fun 재처리를_두_번_해도_마지막_세대만_커밋() {
        open()
        val id = utterance()
        send(LlmOffline(id, 1))
        assertEquals(listOf(StartStructure(id, 2)), send(RetryStructure(id)))
        assertEquals(listOf(StartStructure(id, 3)), send(RetryStructure(id)))
        assertTrue(send(LlmDone(id, 2)).isEmpty())
        assertEquals(listOf(CommitStructure(id)), send(LlmDone(id, 3)))
        assertTrue(send(LlmDone(id, 3)).isEmpty())
    }

    @Test fun 알수없는_발화나_세대의_결과는_무시() {
        open()
        val id = utterance()
        assertTrue(send(LlmDone(id + 1, 1), LlmDone(id, 99)).isEmpty())
    }

    @Test fun 다른_발화의_진행중_요청은_서로_영향_없음() {
        open()
        val a = utterance("a")
        val b = utterance("b")
        assertEquals(listOf(CommitStructure(b)), send(LlmDone(b, 2)))
        assertEquals(listOf(CommitStructure(a)), send(LlmDone(a, 1)))
    }

    // --- §2-2 도착 직후 처리

    @Test fun 도착_시_구조화가_없으면_카드와_요약_즉시() {
        open()
        val c = arriveAtKnownDest()
        assertEquals(listOf(ShowCard, StartSummary), c)
    }

    @Test fun 도착_시_구조화_진행중이면_카드_먼저_요약은_완료_후() {
        open()
        val id = utterance()
        val c = arriveAtKnownDest()
        assertEquals(listOf(ShowCard), c)
        assertEquals(listOf(CommitStructure(id), StartSummary), send(LlmDone(id, 1)))
    }

    @Test fun 도착_대기_중_8초_폴백도_요약_시작() {
        open()
        val id = utterance()
        arriveAtKnownDest()
        assertEquals(listOf(CommitRaw(id), StartSummary), send(LlmFailed(id, 1)))
    }

    @Test fun 도착_대기_중_오프라인도_요약_시작() {
        open()
        val id = utterance()
        arriveAtKnownDest()
        assertEquals(listOf(KeepPending(id), StartSummary), send(LlmOffline(id, 1)))
    }

    @Test fun 요약_대기_중_기어_D는_요약을_취소하고_S0() {
        open()
        val id = utterance()
        arriveAtKnownDest()
        val c = send(GearChanged(Gear.D))
        assertEquals(listOf(ShowS0), c)
        // 이후 도착한 LLM 결과는 커밋만 되고 요약은 시작되지 않는다
        assertEquals(listOf(CommitStructure(id)), send(LlmDone(id, 1)))
        assertEquals(SessionState.SESSION_OPEN, m.state)
    }

    @Test fun 요약_재생_중_기어_D는_요약_중지와_S0() {
        open()
        arriveAtKnownDest()
        assertEquals(listOf(StopSummary, ShowS0), send(GearChanged(Gear.D)))
    }

    @Test fun 요약_재생_끝난_뒤_기어_D는_중지_명령_없음() {
        open()
        arriveAtKnownDest()
        send(SummaryDone)
        assertEquals(listOf(ShowS0), send(GearChanged(Gear.D)))
    }

    @Test fun 요약_재생_중_시동_OFF는_중지와_세션_종료() {
        open()
        arriveAtKnownDest()
        val c = send(IgnitionOff)
        assertEquals(listOf(StopSummary, CloseSession), c)
        assertEquals(SessionState.CLOSED, m.state)
    }

    @Test fun 재출발_후_다시_도착하면_같은_세션에서_카드_재표시() {
        open()
        arriveAtKnownDest()
        send(GearChanged(Gear.D))
        val c = send(GearChanged(Gear.P))
        assertTrue(c.isEmpty()) // 도착 플래그는 D에서 초기화됨
        assertTrue(ShowCard in send(Arrived))
    }
}
