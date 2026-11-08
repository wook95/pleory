package ai.pleos.hello.core

import ai.pleos.hello.data.ItemType
import ai.pleos.hello.data.Segment
import java.util.TimeZone

/**
 * 도착 리마인드 후보 한 줄(F15). [spokenAt] 은 그 할 일을 말한 시각(Utterance.at).
 * Room 조회 결과를 그대로 받는다 — 열 이름이 생성자 인자와 같아야 한다(ReminderDao).
 */
data class ReminderCandidate(
    val itemId: Long,
    val text: String,
    val sessionId: Long,
    val segment: Segment,
    val spokenAt: Long,
    val type: ItemType = ItemType.TODO,
    val done: Boolean = false,
    val deleted: Boolean = false,
    val remindedAt: Long? = null,
)

/**
 * F15 도착 리마인드 규칙. 순수 Kotlin.
 * 대상: 지금 세션이 아닌 세션에서 말한 할 일 중 끝내지 않았고(done=false), 지우지 않았고, 아직 알리지 않았고(remindedAt=null),
 * [WINDOW_DAYS]일 안에 말한 것. 순서는 최근에 말한 것부터(같은 시각이면 itemId 순).
 * 말하기: 주차(카드 상태)일 때만, 대상이 있을 때만 한 줄. 한 번 말한 항목은 remindedAt 이 찍혀 다시 고르지 않는다.
 */
object ArrivalReminder {
    const val WINDOW_DAYS = 7
    const val WINDOW_MS = WINDOW_DAYS * 24L * 60 * 60 * 1000

    /** 한 줄에 넣는 첫 할 일의 최대 길이. 길면 잘라 리마인드가 요약을 오래 미루지 않게 한다. */
    const val MAX_ITEM_CHARS = 30

    fun select(candidates: List<ReminderCandidate>, currentSessionId: Long, nowMillis: Long): List<ReminderCandidate> =
        candidates.filter {
            it.sessionId != currentSessionId && it.type == ItemType.TODO && !it.done && !it.deleted &&
                it.remindedAt == null && it.spokenAt <= nowMillis && nowMillis - it.spokenAt <= WINDOW_MS
        }.sortedWith(compareByDescending<ReminderCandidate> { it.spokenAt }.thenBy { it.itemId })

    /** 말할 한 줄. 주행 중이거나([parked] = false) 대상이 없으면 null. [selected] 는 [select] 의 결과 순서여야 한다. */
    fun line(selected: List<ReminderCandidate>, nowMillis: Long, parked: Boolean, tz: TimeZone = TimeZone.getDefault()): String? {
        if (!parked || selected.isEmpty()) return null
        val first = selected.first()
        val sameSession = selected.all { it.sessionId == first.sessionId }
        val where = if (sameSession) "${relativeDay(first.spokenAt, nowMillis, tz)} ${segmentPhrase(first.segment)}" else "지난 주행에서"
        val count = SummaryComposer.countWord(selected.size)
        val text = itemText(first.text).let { if (it.length > MAX_ITEM_CHARS) it.take(MAX_ITEM_CHARS).trimEnd() + "…" else it }.trimEnd('.', '。')
        val rest = if (selected.size > 1) " 외 ${selected.size - 1}개" else ""
        return "$where 할 일 $count 말했어. $text$rest."
    }

    /**
     * 리마인드에 보일·읽을 할 일 문구. 말한 날 기준의 상대 날짜(내일·오늘 …)로 시작하면 그 낱말을 뗀다 —
     * 리마인드가 이미 "어제 퇴근길에" 처럼 언제 말했는지 붙이므로, 다음 날 "내일 거래처 서류…" 로 읽으면 날짜가 어긋난다.
     * 저장된 항목 텍스트(카드·지난 기록)는 바꾸지 않는다.
     */
    fun itemText(text: String): String {
        val t = text.trim()
        val m = LEADING_DAY.find(t) ?: return t
        return t.substring(m.range.last + 1).ifBlank { t }
    }

    private val LEADING_DAY = Regex("^(오늘|내일|모레|어제|이따가?)\\s+")

    /** 달력 날짜 차이로 오늘/어제/그저께/N일 전. */
    fun relativeDay(thenMillis: Long, nowMillis: Long, tz: TimeZone = TimeZone.getDefault()): String =
        when (val d = dayIndex(nowMillis, tz) - dayIndex(thenMillis, tz)) {
            0L -> "오늘"
            1L -> "어제"
            2L -> "그저께"
            else -> "${d}일 전"
        }

    private fun segmentPhrase(s: Segment) = when (s) {
        Segment.COMMUTE_OUT -> "퇴근길에"
        Segment.COMMUTE_IN -> "출근길에"
        Segment.OUTING -> "외출 중에"
    }

    /** 그 시간대의 달력 날짜 번호(그 시각의 오프셋을 더해 자정 기준으로 나눈다). */
    private fun dayIndex(millis: Long, tz: TimeZone): Long = Math.floorDiv(millis + tz.getOffset(millis), 24L * 60 * 60 * 1000)
}
