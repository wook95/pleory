package ai.pleos.hello.core

/** 개인정보 동의 상태(PRD §0 원칙 4–5, TRD §8-1 1번). UNSET 과 DECLINED/WITHDRAWN 모두 LLM 호출을 막는다. */
enum class ConsentState { UNSET, GRANTED, DECLINED, WITHDRAWN }

object ConsentPolicy {
    /** 고지 내용이 바뀌면 올린다. 저장된 버전이 다르면 동의가 없는 것으로 보고 다시 묻는다. */
    const val VERSION = 1

    fun effective(state: ConsentState, version: Int): ConsentState =
        if (state == ConsentState.GRANTED && version != VERSION) ConsentState.UNSET else state

    /** 명시적으로 동의한 경우에만 외부 LLM 으로 발화 텍스트를 보낸다. */
    fun llmAllowed(state: ConsentState, version: Int) = effective(state, version) == ConsentState.GRANTED

    /** 동의 화면을 띄워야 하는가(아직 한 번도 답하지 않았거나 고지 버전이 바뀜). */
    fun needsPrompt(state: ConsentState, version: Int) = effective(state, version) == ConsentState.UNSET
}

/**
 * 동의가 없으면 [llm] 을 부르지 않고 즉시 Error 를 돌려준다 — 컨트롤러가 이를 RAW 저장(FAILED)으로 처리한다.
 * 동의 여부는 호출 시점에 읽어서 철회가 바로 반영된다.
 */
class ConsentGatedStructurer(
    private val allowed: () -> Boolean,
    private val llm: Structurer,
    private val trace: (String) -> Unit = {},
) : Structurer {
    override suspend fun structure(text: String): StructurerResult {
        if (!allowed()) {
            trace("llm skipped: no consent")
            return StructurerResult.Error("동의 없음")
        }
        trace("llm call")
        return llm.structure(text)
    }
}
