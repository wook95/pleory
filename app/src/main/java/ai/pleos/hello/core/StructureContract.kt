package ai.pleos.hello.core

import ai.pleos.hello.data.Emotion
import ai.pleos.hello.data.ItemType

data class StructuredItem(
    val text: String,
    val type: ItemType,
    val emotion: Emotion,
    val keywords: List<String>,
)

sealed interface StructureResult {
    data class Ok(val items: List<StructuredItem>) : StructureResult
    data class Failed(val reason: String) : StructureResult
}

/**
 * PRD §4-1 구조화 응답 계약.
 * 응답 전체가 JSON 객체 하나여야 한다(앞뒤 공백만 허용 — 코드펜스·설명 문구가 붙으면 실패).
 * `items` 는 1~[MAX_ITEMS]개, 각 항목은 비어 있지 않은 text, type(thought/todo/emotion/person/raw — person 은 옛 응답 호환),
 * emotion(down/neutral/up; 필수) 을 가져야 한다. keywords 는 선택이며 문자열만 취한다.
 * 실패하면 호출 쪽이 [rawFallback] 으로 RAW 항목 1개를 만들고 llmStatus=FAILED 로 둔다(D12).
 */
object StructureContract {
    /**
     * 파서가 받는 상한. 프롬프트는 최대 8개를 요구하고(20초 남짓한 혼잣말에 주제 6~7개, TRD §8-11),
     * 모델이 한두 개 더 나눠도 혼잣말 전체가 RAW 로 떨어지지 않게 여유 2개를 둔다.
     */
    const val MAX_ITEMS = 10

    /** 구조화 LLM(Claude·Gemini 공통) 시스템 프롬프트. 사용자 메시지는 발화 텍스트뿐이다. */
    const val SYSTEM_PROMPT = """당신은 운전자가 운전 중에 떠오르는 대로 말한 혼잣말을 정리한다. 여러 주제가 길게 섞여 있을 수 있다.
규칙:
1. 주제가 바뀔 때마다 항목으로 나눈다. 한 항목에는 한 주제만, 한 문장으로. 최대 8개. 말한 순서를 지킨다.
2. 각 항목의 type은 thought(생각·아이디어·궁금한 것) / todo(할 일) / emotion(감정·기분·감탄) 중 하나. 사람 이야기도 그 말의 성격으로 고른다.
3. emotion은 down / neutral / up 중 하나. 할 일은 보통 neutral.
4. 말투는 다듬되 뜻을 바꾸지 않는다. 없는 내용을 추가하지 않는다. "음", "그 뭐냐", "아 맞다" 같은 군말은 버린다.
5. 정리할 수 없으면 원문을 그대로 1개 항목(type=raw, emotion=neutral, keywords=[])으로 돌려준다.
6. 항목 text는 "내일 거래처 서류 다시 보내기", "노을이 예뻤다"처럼 짧고 담백한 메모체로 쓴다. "~합니다" 같은 존댓말·설명체는 쓰지 않는다.
JSON만 출력한다. 코드펜스·설명 금지."""

    fun parse(response: String): StructureResult {
        val root = try {
            MiniJson.parse(response)
        } catch (e: IllegalArgumentException) {
            return fail("JSON 파싱 실패: ${e.message}")
        }
        @Suppress("UNCHECKED_CAST")
        val obj = root as? Map<String, Any?> ?: return fail("최상위가 객체가 아님")
        val arr = obj["items"] as? List<*> ?: return fail("items 가 배열이 아님")
        if (arr.isEmpty()) return fail("items 가 비어 있음")
        if (arr.size > MAX_ITEMS) return fail("항목 ${arr.size}개 (최대 $MAX_ITEMS)")
        val items = arr.mapIndexed { i, raw ->
            @Suppress("UNCHECKED_CAST")
            val o = raw as? Map<String, Any?> ?: return fail("items[$i] 가 객체가 아님")
            val text = (o["text"] as? String)?.trim()
            if (text.isNullOrEmpty()) return fail("items[$i].text 가 비어 있음")
            val type = ItemType.entries.firstOrNull { it.name.lowercase() == o["type"] }
                ?: return fail("items[$i].type 미지의 값: ${o["type"]}")
            val emotion = Emotion.entries.firstOrNull { it.name.lowercase() == o["emotion"] }
                ?: return fail("items[$i].emotion 미지의 값: ${o["emotion"]}")
            val keywords = (o["keywords"] as? List<*>)?.filterIsInstance<String>()?.map { it.trim() }?.filter { it.isNotBlank() }.orEmpty()
            StructuredItem(text, type, emotion, keywords)
        }
        return StructureResult.Ok(items)
    }

    /** 실패 시 쓰는 원문 그대로의 RAW 항목 1개. */
    fun rawFallback(original: String) =
        StructuredItem(original, ItemType.RAW, Emotion.NEUTRAL, emptyList())

    private fun fail(reason: String) = StructureResult.Failed(reason)
}

/** 계약 검증용 최소 JSON 파서. 객체→Map, 배열→List, 문자열, Double, Boolean, null. 잘못된 입력은 IllegalArgumentException. */
internal object MiniJson {
    fun parse(src: String): Any? {
        val p = Parser(src)
        p.skipWs()
        val v = p.value()
        p.skipWs()
        require(p.pos == src.length) { "JSON 뒤에 여분의 텍스트 (위치 ${p.pos})" }
        return v
    }

    private class Parser(val s: String) {
        var pos = 0

        fun skipWs() {
            while (pos < s.length && s[pos] in " \t\r\n") pos++
        }

        fun value(): Any? {
            require(pos < s.length) { "입력이 끝남" }
            return when (val c = s[pos]) {
                '{' -> obj()
                '[' -> arr()
                '"' -> str()
                't' -> lit("true", true)
                'f' -> lit("false", false)
                'n' -> lit("null", null)
                else -> if (c == '-' || c in '0'..'9') num() else throw IllegalArgumentException("예상치 못한 문자 '$c' (위치 $pos)")
            }
        }

        fun lit(word: String, v: Any?): Any? {
            require(s.startsWith(word, pos)) { "잘못된 리터럴 (위치 $pos)" }
            pos += word.length
            return v
        }

        fun num(): Double {
            val start = pos
            while (pos < s.length && s[pos] in "+-0123456789.eE") pos++
            return s.substring(start, pos).toDoubleOrNull() ?: throw IllegalArgumentException("잘못된 숫자 (위치 $start)")
        }

        fun str(): String {
            pos++ // opening quote
            val sb = StringBuilder()
            while (true) {
                require(pos < s.length) { "문자열이 닫히지 않음" }
                val c = s[pos++]
                when (c) {
                    '"' -> return sb.toString()
                    '\\' -> {
                        require(pos < s.length) { "잘못된 이스케이프" }
                        when (val e = s[pos++]) {
                            '"', '\\', '/' -> sb.append(e)
                            'b' -> sb.append('\b')
                            'f' -> sb.append('\u000C')
                            'n' -> sb.append('\n')
                            'r' -> sb.append('\r')
                            't' -> sb.append('\t')
                            'u' -> {
                                require(pos + 4 <= s.length) { "잘못된 \\u 이스케이프" }
                                sb.append(s.substring(pos, pos + 4).toIntOrNull(16)?.toChar()
                                    ?: throw IllegalArgumentException("잘못된 \\u 이스케이프"))
                                pos += 4
                            }
                            else -> throw IllegalArgumentException("잘못된 이스케이프 \\$e")
                        }
                    }
                    else -> {
                        require(c >= ' ') { "문자열 안의 제어 문자" }
                        sb.append(c)
                    }
                }
            }
        }

        fun arr(): List<Any?> {
            pos++
            val out = mutableListOf<Any?>()
            skipWs()
            if (peek() == ']') { pos++; return out }
            while (true) {
                skipWs()
                out.add(value())
                skipWs()
                when (next()) {
                    ',' -> continue
                    ']' -> return out
                    else -> throw IllegalArgumentException("배열 구분자 오류 (위치 ${pos - 1})")
                }
            }
        }

        fun obj(): Map<String, Any?> {
            pos++
            val out = LinkedHashMap<String, Any?>()
            skipWs()
            if (peek() == '}') { pos++; return out }
            while (true) {
                skipWs()
                require(peek() == '"') { "객체 키는 문자열이어야 함 (위치 $pos)" }
                val k = str()
                skipWs()
                require(next() == ':') { "':' 누락 (위치 ${pos - 1})" }
                skipWs()
                out[k] = value()
                skipWs()
                when (next()) {
                    ',' -> continue
                    '}' -> return out
                    else -> throw IllegalArgumentException("객체 구분자 오류 (위치 ${pos - 1})")
                }
            }
        }

        fun peek(): Char = if (pos < s.length) s[pos] else '\u0000'
        fun next(): Char = if (pos < s.length) s[pos++] else '\u0000'
    }
}
