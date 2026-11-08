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

/** 카드의 항목 한 줄. 시각·장소는 [CardUtterance] 에 있다. */
data class CardItem(
    val text: String,
    val type: ItemType,
    val emotion: Emotion,
    val keywords: List<String>,
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

/**
 * S1 카드 표시용 값 전부. 순수 데이터라 JVM 테스트로 검증한다.
 * [sentence] 는 통계를 타일 대신 문장으로 쓴 두 줄(시안 r2 A "일지"). [startLabel]·[endLabel] 은 경로 끝점 라벨.
 */
data class CardModel(
    val date: String,
    val title: String,
    val sentence: String,
    val durationMin: Int,
    val distanceKm: Double,
    val utterances: List<CardUtterance>,
    val route: List<GeoPoint>,
    val startLabel: String = "",
    val endLabel: String = "",
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

    fun date(millis: Long, tz: TimeZone = TimeZone.getDefault()): String = format("M월 d일 EEEE", millis, tz)

    /** 한마디 하나의 핀 색(감정) — 항목 1개면 그 색, 2개 이상이면 첫 항목과 마지막 항목의 대각 분할(PRD §3). */
    fun pinEmotions(items: List<CardItem>): List<Emotion> = when {
        items.isEmpty() -> emptyList()
        items.size == 1 -> listOf(items[0].emotion)
        else -> listOf(items.first().emotion, items.last().emotion)
    }

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
        val cards = utterances.sortedBy { it.first.at }.map { (u, items) -> utteranceCard(u, items, tz) }
        fun leg(at: Long, label: String?) = listOfNotNull(CardFormat.time(at, tz), label).joinToString(" ")
        return CardModel(
            date = CardFormat.date(endedAt, tz),
            title = CardFormat.title(session.segment, session.durationMin),
            sentence = sentence(session, cards, endedAt, tz),
            durationMin = session.durationMin,
            distanceKm = session.distanceKm,
            utterances = cards,
            route = track.sortedBy { it.at }.map { GeoPoint(it.lat, it.lng) },
            startLabel = leg(session.startedAt, session.originLabel),
            endLabel = leg(endedAt, session.destLabel),
        )
    }

    private fun utteranceCard(u: Utterance, items: List<Item>, tz: TimeZone): CardUtterance {
        val time = CardFormat.time(u.at, tz)
        val cardItems = if (items.isEmpty()) {
            listOf(CardItem(u.text, ItemType.RAW, Emotion.NEUTRAL, emptyList()))
        } else {
            items.filter { !it.deleted }.sortedBy { it.order }.map { CardItem(it.editedText ?: it.text, it.type, it.emotion, it.keywords) }
        }
        val point = if (u.lat != null && u.lng != null) GeoPoint(u.lat, u.lng) else null
        return CardUtterance(time, u.placeLabel, u.congested, u.text, cardItems, point)
    }

    /**
     * "18:40 회사에서 출발해 23km 달려 19:31 집에 도착했어요." + 둘째 줄 "비, 올림픽대로 정체 23분."
     * 장소를 모르면 시각에 바로 "에"를 붙인다. 날씨·정체가 둘 다 없으면 한 줄이다.
     */
    private fun sentence(session: Session, cards: List<CardUtterance>, endedAt: Long, tz: TimeZone): String {
        val t0 = CardFormat.time(session.startedAt, tz)
        val t1 = CardFormat.time(endedAt, tz)
        val from = session.originLabel?.let { "$t0 ${it}에서" } ?: "${t0}에"
        val to = session.destLabel?.let { "$t1 ${it}에" } ?: "${t1}에"
        val first = "$from 출발해 ${km(session.distanceKm)}km 달려 $to 도착했어요."
        val road = cards.firstOrNull { it.congested && it.place != null }?.place
        val second = listOfNotNull(
            session.weather,
            if (session.congestionMin > 0) listOfNotNull(road, "정체 ${session.congestionMin}분").joinToString(" ") else null,
        )
        return if (second.isEmpty()) first else first + "\n" + second.joinToString(", ") + "."
    }

    /** 1km 미만은 소수 한 자리(0.4km), 그 이상은 반올림 정수(23km). "0km 달려" 를 막는다. */
    private fun km(v: Double) = if (v < 1.0) String.format(Locale.KOREA, "%.1f", v) else Math.round(v).toString()
}
