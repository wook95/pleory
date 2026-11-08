package ai.pleos.hello.core

import ai.pleos.hello.data.Emotion
import ai.pleos.hello.data.Item
import ai.pleos.hello.data.ItemType
import ai.pleos.hello.data.Segment
import ai.pleos.hello.data.Session
import ai.pleos.hello.data.TrackPoint
import ai.pleos.hello.data.Utterance
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** 카드의 항목 한 줄. [meta] 는 PRD §3 파생 규칙으로 만든 메타 줄. */
data class CardItem(
    val text: String,
    val type: ItemType,
    val emotion: Emotion,
    val keywords: List<String>,
    val meta: String,
)

/** 한마디 한 건과 거기서 나온 항목들. [quote] 는 STT 원문. */
data class CardUtterance(
    val time: String,
    val place: String?,
    val congested: Boolean,
    val quote: String,
    val items: List<CardItem>,
    val point: GeoPoint?,
)

/** S1 카드 표시용 값 전부. 순수 데이터라 JVM 테스트로 검증한다. */
data class CardModel(
    val dateLine: String,
    val title: String,
    val metaLine: String,
    val durationMin: Int,
    val distanceKm: Double,
    val utterances: List<CardUtterance>,
    val route: List<GeoPoint>,
) {
    val items: List<CardItem> get() = utterances.flatMap { it.items }
}

object CardFormat {
    fun emotionGlyph(e: Emotion) = when (e) {
        Emotion.DOWN -> "↓"
        Emotion.NEUTRAL -> "→"
        Emotion.UP -> "↑"
    }

    fun typeTag(t: ItemType) = when (t) {
        ItemType.THOUGHT -> "생각"
        ItemType.TODO -> "할 일"
        ItemType.EMOTION -> "감정"
        ItemType.PERSON -> "사람"
        ItemType.RAW -> "원문"
    }

    fun segmentName(s: Segment) = when (s) {
        Segment.COMMUTE_OUT -> "퇴근길"
        Segment.COMMUTE_IN -> "출근길"
        Segment.OUTING -> "외출"
    }

    fun title(segment: Segment, durationMin: Int) = "${segmentName(segment)} ${durationMin}분"

    fun time(millis: Long, tz: TimeZone = TimeZone.getDefault()): String = format("HH:mm", millis, tz)

    fun dateLine(millis: Long, tz: TimeZone = TimeZone.getDefault()): String =
        "오늘의 나 · " + format("M월 d일 EEEE", millis, tz)

    /** 한마디 하나의 핀 색(감정) — 항목 1개면 그 색, 2개 이상이면 첫 항목과 마지막 항목의 대각 분할(PRD §3). */
    fun pinEmotions(items: List<CardItem>): List<Emotion> = when {
        items.isEmpty() -> emptyList()
        items.size == 1 -> listOf(items[0].emotion)
        else -> listOf(items.first().emotion, items.last().emotion)
    }

    /** 말풍선 메타: "18:57 · 올림픽대로 · 정체" / 항목이 여럿이면 "· 한마디 1 → 항목 N". */
    fun pinMeta(u: CardUtterance): String = listOfNotNull(
        u.time,
        u.place,
        if (u.congested) "정체" else null,
        if (u.items.size > 1) "한마디 1 → 항목 ${u.items.size}" else null,
    ).joinToString(" · ")

    private fun format(pattern: String, millis: Long, tz: TimeZone) =
        SimpleDateFormat(pattern, Locale.KOREA).apply { timeZone = tz }.format(Date(millis))
}

object CardModelBuilder {
    /**
     * @param utterances 발화와 그 항목(순서대로). 항목이 아직 없는(구조화 대기 중) 발화는 원문 한 줄로 보여 준다.
     * 삭제된 항목은 걸러낸다.
     */
    fun build(
        session: Session,
        utterances: List<Pair<Utterance, List<Item>>>,
        track: List<TrackPoint>,
        tz: TimeZone = TimeZone.getDefault(),
    ): CardModel {
        val endedAt = session.endedAt ?: session.startedAt
        val cards = utterances.sortedBy { it.first.at }.map { (u, items) -> utteranceCard(u, items, session, tz) }
        return CardModel(
            dateLine = CardFormat.dateLine(endedAt, tz),
            title = CardFormat.title(session.segment, session.durationMin),
            metaLine = metaLine(session, cards, endedAt, tz),
            durationMin = session.durationMin,
            distanceKm = session.distanceKm,
            utterances = cards,
            route = track.sortedBy { it.at }.map { GeoPoint(it.lat, it.lng) },
        )
    }

    private fun utteranceCard(u: Utterance, items: List<Item>, session: Session, tz: TimeZone): CardUtterance {
        val time = CardFormat.time(u.at, tz)
        val live = items.filter { !it.deleted }.sortedBy { it.order }
        val split = live.size > 1
        val base = listOfNotNull(
            time,
            u.placeLabel,
            if (u.congested) "정체 ${session.congestionMin}분 구간" else null,
        )
        val cardItems = if (items.isEmpty()) {
            listOf(CardItem(u.text, ItemType.RAW, Emotion.NEUTRAL, emptyList(), base.joinToString(" · ")))
        } else {
            live.mapIndexed { i, it ->
                val meta = if (split && i == 0) base + "원문 \"${u.text}\"" else base
                CardItem(it.editedText ?: it.text, it.type, it.emotion, it.keywords, meta.joinToString(" · "))
            }
        }
        val point = if (u.lat != null && u.lng != null) GeoPoint(u.lat, u.lng) else null
        return CardUtterance(time, u.placeLabel, u.congested, u.text, cardItems, point)
    }

    private fun metaLine(session: Session, cards: List<CardUtterance>, endedAt: Long, tz: TimeZone): String {
        fun leg(at: Long, label: String?) = listOfNotNull(CardFormat.time(at, tz), label).joinToString(" ")
        val road = cards.firstOrNull { it.congested && it.place != null }?.place
        return listOfNotNull(
            leg(session.startedAt, session.originLabel) + " → " + leg(endedAt, session.destLabel),
            session.weather,
            if (session.congestionMin > 0) listOfNotNull(road, "정체 ${session.congestionMin}분").joinToString(" ") else null,
        ).joinToString(" · ")
    }
}
