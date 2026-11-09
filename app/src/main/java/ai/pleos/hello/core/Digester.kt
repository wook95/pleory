package ai.pleos.hello.core

import kotlinx.coroutines.withTimeoutOrNull

/** 카드 대표 문장(TRD §8-11)을 만드는 LLM 한 단계. 입력은 [CardDigest.input] 줄 목록, 응답 검증은 [CardDigest.parse]. */
interface Digester {
    suspend fun digest(input: String): StructurerResult
}

/**
 * 대표 문장 체인: 단계를 차례로 시도하고(Claude → Gemini) 계약에 맞는 첫 결과를 돌려준다. 모두 실패하면 null —
 * 호출 쪽이 저장하지 않고 화면은 [CardDigest.fallback] 으로 그린다. 로그는 단계 이름과 분류만 남긴다.
 */
class DigestChain(
    private val steps: List<Step>,
    private val trace: (String) -> Unit = {},
) {
    class Step(
        val name: String,
        val digester: Digester,
        val timeoutMs: Long? = null,
        val available: () -> Boolean = { true },
    )

    suspend fun run(items: List<DigestItem>): Headlines? {
        if (items.isEmpty()) return null
        val input = CardDigest.input(items)
        for (step in steps) {
            if (!step.available()) continue
            val result = step.timeoutMs?.let { ms -> withTimeoutOrNull(ms) { step.digester.digest(input) } ?: StructurerResult.Error("${step.name} timeout") }
                ?: step.digester.digest(input)
            val parsed = (result as? StructurerResult.Ok)?.let { CardDigest.parse(it.rawJsonText, items) }
            if (parsed is DigestResult.Ok) {
                trace("digest ok: ${step.name}")
                return parsed.headlines
            }
            val why = when {
                result is StructurerResult.Error -> result.message
                result is StructurerResult.NetworkUnavailable -> "network unavailable"
                parsed is DigestResult.Failed -> "contract violation"
                else -> "unknown"
            }
            trace("digest ${step.name} failed: $why")
        }
        return null
    }
}
