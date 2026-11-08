package ai.pleos.hello.core

import kotlinx.coroutines.withTimeoutOrNull

/**
 * 구조화기를 차례로 시도한다(TRD D6: Claude → Gemini). 앞 단계가 어떤 이유로든(HTTP 오류·타임아웃·거절·형식·계약 위반)
 * 계약에 맞는 결과를 못 내면 다음 단계로 넘어간다. 모두 실패하면 마지막 단계의 결과를 돌려준다(컨트롤러가 RAW/PENDING 결정).
 * 전체 시간 상한은 호출자(컨트롤러 8초)가 쥔다. 취소되면 진행 중인 단계도 함께 취소된다.
 */
class FallbackStructurer(
    private val steps: List<Step>,
    private val trace: (String) -> Unit = {},
) : Structurer {

    /** [timeoutMs] 가 있으면 이 단계는 그 안에 끝나야 한다. [available] 이 false 면(키 없음 등) 건너뛴다. */
    class Step(
        val name: String,
        val structurer: Structurer,
        val timeoutMs: Long? = null,
        val available: () -> Boolean = { true },
    )

    override suspend fun structure(text: String): StructurerResult {
        var last: StructurerResult = StructurerResult.Error("no structurer available")
        for (step in steps) {
            if (!step.available()) {
                trace("llm ${step.name} skipped: unavailable")
                continue
            }
            val result = step.timeoutMs?.let { ms -> withTimeoutOrNull(ms) { step.structurer.structure(text) } ?: StructurerResult.Error("${step.name} timeout") }
                ?: step.structurer.structure(text)
            val parsed = (result as? StructurerResult.Ok)?.let { StructureContract.parse(it.rawJsonText) }
            if (parsed is StructureResult.Ok) {
                trace("llm ok: ${step.name}")
                return result
            }
            trace("llm ${step.name} failed: ${StructureFailure.reason(result, parsed)}")
            last = result
        }
        return last
    }
}
