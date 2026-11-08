package ai.pleos.hello.core

import ai.pleos.hello.data.Emotion
import ai.pleos.hello.data.Item
import ai.pleos.hello.data.ItemType
import ai.pleos.hello.data.Session
import ai.pleos.hello.data.Utterance
import java.util.Calendar
import java.util.TimeZone

/**
 * S2 지난 기록의 세션 한 줄(F11). [moods] 는 감정 흐름 바(정리된 항목의 감정, 말한 순서), [itemCount] 는 그 세션 카드의 항목 수와 같다.
 * [todos] 는 지우지 않고 아직 안 한("했어" 전) 할 일 항목의 글 — "할 일만" 필터에서 행 아래에 붙는다.
 */
data class HistoryRow(
    val sessionId: Long,
    val day: String,
    val segment: String,
    val timeRange: String,
    val stats: String,
    val moods: List<Emotion>,
    val itemCount: Int,
    val todos: List<String>,
)

object History {
    const val DAYS = 7

    /** 7일 경계: 오늘 0시 기준 6일 전 0시(US-10). 이 시각 이후에 시작한 세션만 보인다. */
    fun windowStart(nowMillis: Long, tz: TimeZone = TimeZone.getDefault()): Long =
        startOfDay(nowMillis, tz) - (DAYS - 1) * DAY_MS.toLong()

    /**
     * 끝난 세션들을 행으로 바꾼다. 최신순. 7일 창 밖, 아직 안 끝난 세션, 시간도 한마디도 없는 빈 세션(시동만 켰다 끈 경우)은 뺀다.
     * 항목 수·감정은 카드([CardModelBuilder])와 같은 규칙으로 센다 — 지운 항목은 빠지고, 정리 전 한마디는 원문 한 줄이다.
     * [todoOnly] 면 할 일이 하나라도 남은 세션만 남긴다.
     */
    fun rows(
        sessions: List<Session>,
        utterances: List<Utterance>,
        items: List<Item>,
        nowMillis: Long,
        todoOnly: Boolean = false,
        tz: TimeZone = TimeZone.getDefault(),
    ): List<HistoryRow> {
        val since = windowStart(nowMillis, tz)
        val bySession = utterances.groupBy { it.sessionId }
        val byUtterance = items.groupBy { it.utteranceId }
        return sessions
            .filter { it.endedAt != null && it.startedAt >= since }
            .filter { it.durationMin > 0 || bySession[it.id].orEmpty().isNotEmpty() }
            .sortedByDescending { it.startedAt }
            .map { s -> row(s, CardModelBuilder.build(s, bySession[s.id].orEmpty().map { it to byUtterance[it.id].orEmpty() }, emptyList(), tz), nowMillis, tz) }
            .filter { !todoOnly || it.todos.isNotEmpty() }
    }

    /** 카드 모델 → 행. 날짜는 오늘·어제·그 외 "11월 5일 목요일". */
    fun row(session: Session, card: CardModel, nowMillis: Long, tz: TimeZone = TimeZone.getDefault()): HistoryRow {
        val endedAt = session.endedAt ?: session.startedAt
        val today = startOfDay(nowMillis, tz)
        val day = when (startOfDay(session.startedAt, tz)) {
            today -> "오늘"
            today - DAY_MS -> "어제"
            else -> CardFormat.date(session.startedAt, tz)
        }
        return HistoryRow(
            sessionId = session.id,
            day = day,
            segment = CardFormat.segmentName(session.segment),
            timeRange = CardFormat.timeRange(session.startedAt, endedAt, tz),
            stats = "${session.durationMin}분 · ${CardModelBuilder.km(session.distanceKm)}km",
            moods = card.items.filter { it.type != ItemType.RAW }.map { it.emotion },
            itemCount = card.items.size,
            todos = card.items.filter { it.type == ItemType.TODO && !it.done }.map { it.text },
        )
    }

    private const val DAY_MS = 24 * 60 * 60 * 1000

    /** 그 시각이 속한 날의 0시. 서머타임이 없는 시간대 기준이라 하루 = 24시간으로 뺀다. */
    private fun startOfDay(millis: Long, tz: TimeZone) = Calendar.getInstance(tz).apply {
        timeInMillis = millis
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis
}
