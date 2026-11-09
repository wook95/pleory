package ai.pleos.hello.core

import ai.pleos.hello.data.Emotion
import ai.pleos.hello.data.ItemType

/** 카드 묶음(TRD §8-11). 표시 묶음일 뿐 [ItemType] 과 DB 는 그대로다. 순서는 감정 → 일 → 아이디어. */
enum class ItemGroup(val label: String, val key: String) {
    EMOTION("감정", "emotion"),
    WORK("일", "work"),
    IDEA("아이디어", "idea");

    companion object {
        fun of(type: ItemType) = when (type) {
            ItemType.EMOTION -> EMOTION
            ItemType.TODO -> WORK
            ItemType.THOUGHT, ItemType.PERSON, ItemType.RAW -> IDEA
        }
    }
}

/** 묶음별 대표 문장. 항목이 없는 묶음은 null. */
data class Headlines(val emotion: String? = null, val work: String? = null, val idea: String? = null) {
    operator fun get(g: ItemGroup) = when (g) {
        ItemGroup.EMOTION -> emotion
        ItemGroup.WORK -> work
        ItemGroup.IDEA -> idea
    }
}

/** 대표 문장 입력 한 줄. 글·유형·감정만 담는다(위치·시각은 차 밖으로 보내지 않는다). */
data class DigestItem(val text: String, val type: ItemType, val emotion: Emotion)

sealed interface DigestResult {
    data class Ok(val headlines: Headlines) : DigestResult
    data class Failed(val reason: String) : DigestResult
}

/**
 * 카드 대표 문장 계약(TRD §8-11). 세션의 지워지지 않은 항목 전체로 감정·일·아이디어 묶음마다 한 문장을 만든다.
 * 응답은 JSON 객체 하나 `{"emotion": "...", "work": "...", "idea": "..."}`. 항목이 있는 묶음은 비어 있지 않은
 * [MAX_CHARS]자 이하 문장이어야 하고(프롬프트는 32자, 넘치면 다음 단계로), 항목이 없는 묶음의 값은 무시한다(null).
 * LLM 이 실패하면 [fallback] 규칙 문장을 쓴다.
 */
object CardDigest {
    /** 화면은 대표 문장을 두 줄까지 보인다. 프롬프트는 32자를 요구하고 이 상한은 넘친 응답을 거르는 선이다. */
    const val MAX_CHARS = 48

    const val SYSTEM_PROMPT = """운전자가 한 번의 주행 동안 말한 것을 정리한 항목 목록이 온다. 각 줄은 "묶음|기분|글" 이고 말한 순서대로다.
묶음별로 카드 맨 위에 보일 대표 문장 한 줄을 쓴다.
규칙:
1. emotion(감정 묶음): 감정 줄의 첫 줄에서 시작해 마지막 줄로 끝나는 흐름으로 쓴다. 중간은 빼도 되지만 마지막 감정은 꼭 넣는다. 예: "회의로 짜증 났다가 산책하고 후련해졌어"
2. work(일 묶음): 할 일을 모두 짧게 묶는다. 예: "세탁소 들르고 엄마한테 전화하기"
3. idea(아이디어 묶음): 생각을 모두 짧게 묶는다. 쉼표로 이어도 된다. 예: "캠핑 장비 알아보기, 새 카페, 운동 루틴"
4. 각 문장은 25자 안팎, 절대 32자를 넘기지 않는다. 반말 메모체. 마침표 없이.
5. 항목에 없는 내용(시간대, 이유 등)을 지어내지 않는다. 그 묶음에 항목이 없으면 빈 문자열.
JSON만 출력한다. 코드펜스·설명 금지."""

    /** LLM 사용자 메시지. 묶음 키|감정|글, 한 줄에 하나. */
    fun input(items: List<DigestItem>): String = items.joinToString("\n") {
        "${ItemGroup.of(it.type).key}|${MOOD.getValue(it.emotion)}|${it.text.replace('\n', ' ')}"
    }

    /** 감정 값을 한국어 낱말로 보낸다(영어 값 "up" 이 문장에 그대로 새는 것을 막는다). */
    private val MOOD = mapOf(Emotion.DOWN to "가라앉음", Emotion.NEUTRAL to "보통", Emotion.UP to "좋음")

    fun parse(response: String, items: List<DigestItem>): DigestResult {
        val root = try {
            MiniJson.parse(response)
        } catch (e: IllegalArgumentException) {
            return DigestResult.Failed("JSON 파싱 실패")
        }
        @Suppress("UNCHECKED_CAST")
        val obj = root as? Map<String, Any?> ?: return DigestResult.Failed("최상위가 객체가 아님")
        val present = items.map { ItemGroup.of(it.type) }.toSet()
        val lines = ItemGroup.entries.associateWith { g ->
            if (g !in present) return@associateWith null
            val v = (obj[g.key] as? String)?.trim()?.trimEnd('.')
            if (v.isNullOrEmpty()) return DigestResult.Failed("${g.key} 비어 있음")
            if (v.length > MAX_CHARS) return DigestResult.Failed("${g.key} ${v.length}자 (최대 $MAX_CHARS)")
            v
        }
        return DigestResult.Ok(Headlines(lines[ItemGroup.EMOTION], lines[ItemGroup.WORK], lines[ItemGroup.IDEA]))
    }

    /**
     * LLM 없이 만드는 대표 문장. 묶음 첫 항목 글 + 둘 이상이면 " 외 N개".
     * 감정은 첫 감정과 마지막 감정의 방향이 다르면 "첫 글 → 마지막 글".
     */
    fun fallback(items: List<DigestItem>): Headlines {
        val by = items.groupBy { ItemGroup.of(it.type) }
        fun counted(list: List<DigestItem>?) = list?.takeIf { it.isNotEmpty() }?.let {
            if (it.size == 1) it[0].text else "${it[0].text} 외 ${it.size - 1}개"
        }
        val emotions = by[ItemGroup.EMOTION]
        val emotion = when {
            emotions.isNullOrEmpty() -> null
            emotions.size >= 2 && emotions.first().emotion != emotions.last().emotion ->
                "${emotions.first().text} → ${emotions.last().text}"
            else -> counted(emotions)
        }
        return Headlines(emotion, counted(by[ItemGroup.WORK]), counted(by[ItemGroup.IDEA]))
    }
}
