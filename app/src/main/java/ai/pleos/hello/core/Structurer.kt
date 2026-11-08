package ai.pleos.hello.core

import ai.pleos.hello.data.Emotion
import ai.pleos.hello.data.ItemType

sealed interface StructurerResult {
    /** [rawJsonText] 는 LLM 응답 원문. 검증은 [StructureContract.parse] 가 맡는다. */
    data class Ok(val rawJsonText: String) : StructurerResult
    /** 네트워크 없음. D12: PENDING 유지. */
    data object NetworkUnavailable : StructurerResult
    /** 호출 실패·응답 불량. D12: FAILED + RAW. */
    data class Error(val message: String) : StructurerResult
}

/**
 * 발화 텍스트를 PRD §4-1 JSON 으로 구조화한다. 시그니처는 텍스트만 받는다(프라이버시 원칙: 위치·궤적은 보내지 않는다).
 * 공모전은 Gemini. 플랫폼 LLM(Gleo AI)이 서드파티에 열리면 PleosLlmStructurer로 전환
 */
interface Structurer {
    suspend fun structure(text: String): StructurerResult
}

/**
 * 개발·테스트 전용 규칙 기반 구조화(docs/pleory/prototype.html 의 휴리스틱). 데모에 쓰지 않는다 —
 * 이걸로 찍은 영상은 "기능 축소 데모"다(TRD D6).
 */
class RuleStructurer : Structurer {
    override suspend fun structure(text: String): StructurerResult {
        val parts = text.replace(Regex("\\s+(그리고|근데|아 근데)\\s+"), "|")
            .replace(Regex(",\\s*"), "|")
            .split('|').map { it.trim() }.filter { it.length > 1 }
        if (parts.isEmpty()) return StructurerResult.Error("빈 발화")
        val json = parts.take(StructureContract.MAX_ITEMS).joinToString(",", "{\"items\":[", "]}") { p ->
            var type = ItemType.THOUGHT
            var emotion = Emotion.NEUTRAL
            when {
                TODO_RE.containsMatchIn(p) -> type = ItemType.TODO
                DOWN_RE.containsMatchIn(p) -> { type = ItemType.EMOTION; emotion = Emotion.DOWN }
                UP_RE.containsMatchIn(p) -> { type = ItemType.EMOTION; emotion = Emotion.UP }
                PERSON_RE.containsMatchIn(p) -> type = ItemType.PERSON
            }
            "{\"text\":${quote(p)},\"type\":\"${type.name.lowercase()}\",\"emotion\":\"${emotion.name.lowercase()}\",\"keywords\":[]}"
        }
        return StructurerResult.Ok(json)
    }

    private fun quote(s: String) = "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

    private companion object {
        val TODO_RE = Regex("해야|보내|하기|해야지|전화|세차|사야|가야")
        val DOWN_RE = Regex("힘들|짜증|피곤|싫|답답|울|불안|맞나")
        val UP_RE = Regex("예쁘|좋|행복|고마|시원|편하")
        val PERSON_RE = Regex("팀장|엄마|친구|거래처")
    }
}

/**
 * 구조화가 성공하지 못한 사유를 로그용 분류 한 줄로 만든다(성공이면 null). 본문·발화는 담지 않는다.
 * [result] null = 컨트롤러 타임아웃. 계약 위반 사유는 모델 값이 섞일 수 있어 분류만 남긴다.
 */
object StructureFailure {
    fun reason(result: StructurerResult?, parsed: StructureResult?): String? = when {
        result == null -> "timeout"
        result is StructurerResult.NetworkUnavailable -> "network unavailable"
        result is StructurerResult.Error -> result.message
        parsed is StructureResult.Failed -> "contract violation"
        else -> null
    }
}

