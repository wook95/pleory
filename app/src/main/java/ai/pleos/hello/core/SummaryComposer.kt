package ai.pleos.hello.core

import ai.pleos.hello.data.Emotion
import ai.pleos.hello.data.ItemType

/** 요약 입력. 삭제된 항목은 호출 전에 걸러서 넘긴다. */
data class SummaryItem(
    val type: ItemType,
    val emotion: Emotion = Emotion.NEUTRAL,
    val keywords: List<String> = emptyList(),
)

/**
 * PRD §4-2 규칙 요약(LLM 미사용).
 * "감정 항목" = type 이 EMOTION 인 항목. 다른 유형의 emotion 값은 요약에 쓰지 않는다
 * (PRD §3: 유형을 비감정으로 바꾸면 emotion 은 NEUTRAL 로). 순서는 입력 순서(발화 시각순 + 발화 내 order).
 * 할 일 수 = type 이 TODO 인 항목 수.
 */
object SummaryComposer {
    const val MAX_LENGTH = 40
    private const val EMPTY = "오늘은 말이 없었네. 그래도 여기까지 왔어."
    private val COUNT_WORDS = listOf("", "하나", "둘", "셋", "넷", "다섯", "여섯", "일곱", "여덟", "아홉", "열")

    fun compose(items: List<SummaryItem>): String {
        if (items.isEmpty()) return EMPTY
        val emotions = items.filter { it.type == ItemType.EMOTION }
        val main = when {
            emotions.size >= 2 && emotions.first().emotion == Emotion.DOWN && emotions.last().emotion == Emotion.UP -> {
                // 마지막 up 항목의 첫 키워드. 키워드가 없으면 "좋게"로 대체
                val kw = emotions.last().keywords.firstOrNull { it.isNotBlank() }
                if (kw != null) "힘들게 시작해서 ${kw}${roParticle(kw)} 끝났어." else "힘들게 시작해서 좋게 끝났어."
            }
            emotions.size >= 2 -> "감정이 " + emotions.joinToString(", ") { emotionPhrase(it.emotion) } + "."
            emotions.size == 1 ->
                if (emotions[0].emotion == Emotion.DOWN) "오늘은 좀 힘들었네." else "오늘은 괜찮았네."
            else -> "생각이 좀 있었네."
        }
        val todos = items.count { it.type == ItemType.TODO }
        val full = if (todos > 0) "$main 할 일 ${countWord(todos)} 있어." else main
        return full.take(MAX_LENGTH)
    }

    private fun emotionPhrase(e: Emotion) = when (e) {
        Emotion.DOWN -> "가라앉았다"
        Emotion.NEUTRAL -> "그냥"
        Emotion.UP -> "올라왔다"
    }

    /** 받침이 없거나 ㄹ 받침이면 "로", 그 외 받침이면 "으로". 한글이 아니면 "로". */
    private fun roParticle(word: String): String {
        val c = word.last()
        if (c !in '가'..'힣') return "로"
        val jong = (c - '가') % 28
        return if (jong == 0 || jong == 8) "로" else "으로"
    }

    private fun countWord(n: Int) = COUNT_WORDS.getOrNull(n) ?: n.toString()
}
