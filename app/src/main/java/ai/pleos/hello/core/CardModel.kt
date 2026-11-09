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

/**
 * 카드의 항목 한 줄. 시각·장소는 [CardUtterance] 에 있다. [id] 는 DB Item id — 구조화 전 원문 한 줄은 아직 행이 없어 null(지울 수 없음).
 * [done] 은 리마인드 띠의 "했어"(F15)로 끝낸 할 일.
 */
data class CardItem(
    val id: Long?,
    val text: String,
    val type: ItemType,
    val emotion: Emotion,
    val keywords: List<String>,
    val done: Boolean = false,
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
 * 묶음 상세의 항목 한 줄. 시각·장소·원문은 그 항목이 나온 한마디의 것.
 * [quote] 는 항목이 셋 이상 나온 긴 혼잣말이면 빈 문자열이다 — 항목마다 같은 혼잣말 앞부분이 되풀이되기만 해서 뜻이 없다.
 */
data class CardGroupItem(val item: CardItem, val time: String, val place: String?, val quote: String)

/** 카드 묶음(감정·일·아이디어) 하나: 대표 문장과 항목들(TRD §8-11). 항목이 없는 묶음은 만들지 않는다. */
data class CardGroup(val group: ItemGroup, val headline: String, val items: List<CardGroupItem>)

/**
 * S1 카드 표시용 값 전부. 순수 데이터라 JVM 테스트로 검증한다.
 * [sentence] 는 통계를 타일 대신 문장으로 쓴 두 줄(시안 r2 A "일지"). [startLabel]·[endLabel] 은 경로 끝점 라벨.
 * [heading] 은 "11월 8일 퇴근길 18:40–19:31", [summary] 는 저장된 요약 문장(아직 없으면 null) — 둘 다 QR 평문(F10)용.
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
    val heading: String = "",
    val summary: String? = null,
    /** 감정 → 일 → 아이디어 순, 항목이 있는 묶음만. */
    val groups: List<CardGroup> = emptyList(),
    /** 저장된 대표 문장이 없어 규칙 폴백으로 채웠는가(LLM 결과를 기다리는 중이거나 실패). */
    val headlinesFallback: Boolean = false,
) {
    val items: List<CardItem> get() = utterances.flatMap { it.items }
}

object CardFormat {
    fun emotionGlyph(e: Emotion) = when (e) {
        Emotion.DOWN -> "↓"
        Emotion.NEUTRAL -> "→"
        Emotion.UP -> "↑"
    }

    fun segmentName(s: Segment) = when (s) {
        Segment.COMMUTE_OUT -> "퇴근길"
        Segment.COMMUTE_IN -> "출근길"
        Segment.OUTING -> "외출"
    }

    fun title(segment: Segment, durationMin: Int) = "${segmentName(segment)} ${durationMin}분"

    fun time(millis: Long, tz: TimeZone = TimeZone.getDefault()): String = format("HH:mm", millis, tz)

    fun date(millis: Long, tz: TimeZone = TimeZone.getDefault()): String = format("M월 d일 EEEE", millis, tz)

    /** "18:40–19:31". 끝이 시작보다 이르면(데모 시계로 끝난 세션) 시작 시각만 쓴다. */
    fun timeRange(startedAt: Long, endedAt: Long, tz: TimeZone = TimeZone.getDefault()): String =
        if (endedAt <= startedAt) time(startedAt, tz) else "${time(startedAt, tz)}–${time(endedAt, tz)}"

    /** 한마디 하나의 핀 색(감정) — 항목 1개면 그 색, 2개 이상이면 첫 항목과 마지막 항목의 대각 분할(PRD §3). */
    fun pinEmotions(items: List<CardItem>): List<Emotion> = when {
        items.isEmpty() -> emptyList()
        items.size == 1 -> listOf(items[0].emotion)
        else -> listOf(items.first().emotion, items.last().emotion)
    }

    internal fun format(pattern: String, millis: Long, tz: TimeZone) =
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
        val (groups, fallback) = groups(session, cards)
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
            heading = "${CardFormat.format("M월 d일", session.startedAt, tz)} ${CardFormat.segmentName(session.segment)} ${CardFormat.timeRange(session.startedAt, endedAt, tz)}",
            summary = session.summaryText?.takeIf { it.isNotBlank() },
            groups = groups,
            headlinesFallback = fallback,
        )
    }

    /**
     * 묶음 나누기와 대표 문장. 저장된 대표 문장(세션 행)이 하나라도 있으면 그것을 쓰고, 저장값이 빈 묶음만 규칙 폴백으로 채운다.
     * 하나도 없으면 전부 규칙 폴백([CardDigest.fallback]).
     */
    private fun groups(session: Session, cards: List<CardUtterance>): Pair<List<CardGroup>, Boolean> {
        val flat = cards.flatMap { u -> u.items.map { CardGroupItem(it, u.time, u.place, if (u.items.size >= LONG_UTTERANCE_ITEMS) "" else u.quote) } }
        if (flat.isEmpty()) return emptyList<CardGroup>() to false
        val stored = Headlines(session.headlineEmotion, session.headlineWork, session.headlineIdea)
        val anyStored = ItemGroup.entries.any { stored[it] != null }
        val rule = CardDigest.fallback(flat.map { DigestItem(it.item.text, it.item.type, it.item.emotion) })
        var usedRule = !anyStored
        val groups = ItemGroup.entries.mapNotNull { g ->
            val items = flat.filter { ItemGroup.of(it.item.type) == g }
            if (items.isEmpty()) return@mapNotNull null
            val headline = stored[g]?.takeIf { anyStored } ?: rule[g]!!.also { usedRule = true }
            CardGroup(g, headline, items)
        }
        return groups to usedRule
    }

    private fun utteranceCard(u: Utterance, items: List<Item>, tz: TimeZone): CardUtterance {
        val time = CardFormat.time(u.at, tz)
        val cardItems = if (items.isEmpty()) {
            listOf(CardItem(null, u.text, ItemType.RAW, Emotion.NEUTRAL, emptyList()))
        } else {
            items.filter { !it.deleted }.sortedBy { it.order }.map { CardItem(it.id, it.editedText ?: it.text, it.type, it.emotion, it.keywords, it.done) }
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

    /** 이 개수 이상 항목이 나온 한마디는 긴 혼잣말로 보고 상세에서 원문 인용을 뺀다. */
    const val LONG_UTTERANCE_ITEMS = 3

    /** 1km 미만은 소수 한 자리(0.4km), 그 이상은 반올림 정수(23km). "0km 달려" 를 막는다. */
    internal fun km(v: Double) = if (v < 1.0) String.format(Locale.KOREA, "%.1f", v) else Math.round(v).toString()
}
