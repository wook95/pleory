package ai.pleos.hello.core

import ai.pleos.hello.data.Emotion
import ai.pleos.hello.data.Item
import ai.pleos.hello.data.ItemType
import ai.pleos.hello.data.Segment
import ai.pleos.hello.data.Session
import ai.pleos.hello.data.TrackPoint
import ai.pleos.hello.data.Utterance
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Test

class CardModelTest {
    private val tz = TimeZone.getTimeZone("Asia/Seoul")
    private val t1840 = 1_794_130_800_000L // 2026-11-08 18:40 KST (일요일)

    private fun item(text: String, type: ItemType, emotion: Emotion, order: Int, kw: List<String> = emptyList()) =
        Item(utteranceId = 1, order = order, text = text, type = type, emotion = emotion, keywords = kw)

    private val session = Session(
        profileId = "p", startedAt = t1840, endedAt = t1840 + 51 * 60_000L, segment = Segment.COMMUTE_OUT,
        originLabel = "회사", destLabel = "집", distanceKm = 23.39, durationMin = 51, weather = "비", congestionMin = 23,
    )
    private val u1 = Utterance(id = 1, sessionId = 1, at = t1840 + 17 * 60_000L, text = "오늘 팀장 때문에 진짜 힘들었다",
        placeLabel = "올림픽대로", congested = true, lat = 37.5, lng = 127.0)
    private val u2 = Utterance(id = 2, sessionId = 1, at = t1840 + 28 * 60_000L, text = "내일 거래처 서류 다시 보내야 해 아 노을 예쁘다",
        placeLabel = "한강대교", lat = 37.52, lng = 126.95)

    private fun build(u2Items: List<Item>) = CardModelBuilder.build(
        session,
        listOf(u2 to u2Items, u1 to listOf(item("오늘 팀장 때문에 진짜 힘들었다", ItemType.EMOTION, Emotion.DOWN, 0))),
        listOf(TrackPoint(1, 2, 37.5, 127.0, 0), TrackPoint(1, 1, 37.51, 127.1, 0)),
        tz,
    )

    private val twoItems = listOf(
        item("내일 거래처 서류 다시 보내기", ItemType.TODO, Emotion.NEUTRAL, 0, listOf("거래처")),
        item("노을이 예뻤다", ItemType.EMOTION, Emotion.UP, 1, listOf("노을")),
    )

    @Test
    fun `제목 날짜 문장 끝점 라벨`() {
        val m = build(twoItems)
        assertEquals("퇴근길 51분", m.title)
        assertEquals("11월 8일 일요일", m.date)
        assertEquals("18:40 회사에서 출발해 23km 달려 19:31 집에 도착했어요.\n비, 올림픽대로 정체 23분.", m.sentence)
        assertEquals("18:40 회사", m.startLabel)
        assertEquals("19:31 집", m.endLabel)
    }

    @Test
    fun `발화는 시각순 항목은 order 순 개수 집계`() {
        val m = build(twoItems)
        assertEquals(listOf("18:57", "19:08"), m.utterances.map { it.time })
        assertEquals(3, m.items.size)
        assertEquals(listOf(Emotion.DOWN, Emotion.NEUTRAL, Emotion.UP), m.items.map { it.emotion })
        assertEquals(listOf("↓", "→", "↑"), m.items.map { CardFormat.emotionGlyph(it.emotion) })
        assertEquals(2, m.route.size)
        assertEquals(127.1, m.route.first().lng, 0.0) // at 순 정렬
    }

    @Test
    fun `발화 줄 - 시각 장소 정체 원문`() {
        val m = build(twoItems)
        assertEquals(listOf("올림픽대로", "한강대교"), m.utterances.map { it.place })
        assertEquals(listOf(true, false), m.utterances.map { it.congested })
        assertEquals(u2.text, m.utterances[1].quote)
    }

    @Test
    fun `구조화 대기 중인 발화는 원문 한 줄로 보인다`() {
        val m = build(emptyList())
        assertEquals(2, m.items.size)
        assertEquals(ItemType.RAW, m.items[1].type)
        assertEquals(u2.text, m.items[1].text)
    }

    @Test
    fun `삭제된 항목은 빠지고 수정문이 우선한다`() {
        val m = build(listOf(twoItems[0].copy(editedText = "서류 재전송"), twoItems[1].copy(deleted = true)))
        assertEquals(listOf("서류 재전송"), m.utterances[1].items.map { it.text })
    }

    @Test
    fun `핀 색 - 항목 1개는 그 색 2개 이상은 첫 마지막 대각 분할`() {
        val m = build(twoItems)
        assertEquals(listOf(Emotion.DOWN), CardFormat.pinEmotions(m.utterances[0].items))
        assertEquals(listOf(Emotion.NEUTRAL, Emotion.UP), CardFormat.pinEmotions(m.utterances[1].items))
        val three = m.utterances[1].items + m.utterances[0].items
        assertEquals(listOf(Emotion.NEUTRAL, Emotion.DOWN), CardFormat.pinEmotions(three))
        assertEquals(emptyList<Emotion>(), CardFormat.pinEmotions(emptyList()))
    }

    @Test
    fun `정체 0분이면 문장에 정체 없음 - 장소 모르면 시각에 바로 조사 - 구간별 제목`() {
        val m = CardModelBuilder.build(session.copy(congestionMin = 0, segment = Segment.COMMUTE_IN, destLabel = null), emptyList(), emptyList(), tz)
        assertEquals("18:40 회사에서 출발해 23km 달려 19:31에 도착했어요.\n비.", m.sentence)
        val bare = CardModelBuilder.build(session.copy(congestionMin = 0, weather = null, originLabel = null), emptyList(), emptyList(), tz)
        assertEquals("18:40에 출발해 23km 달려 19:31 집에 도착했어요.", bare.sentence)
        assertEquals("18:40", bare.startLabel)
        assertEquals("출근길 51분", m.title)
        assertEquals("외출 7분", CardFormat.title(Segment.OUTING, 7))
    }

    @Test
    fun `문장 - 날씨 없이 정체만 - 정체 도로 모름 - 1km 미만은 소수 한 자리`() {
        val noWeather = CardModelBuilder.build(session.copy(weather = null), emptyList(), emptyList(), tz)
        assertEquals("18:40 회사에서 출발해 23km 달려 19:31 집에 도착했어요.\n정체 23분.", noWeather.sentence)
        val short = CardModelBuilder.build(session.copy(distanceKm = 0.42, congestionMin = 0, weather = null), emptyList(), emptyList(), tz)
        assertEquals("18:40 회사에서 출발해 0.4km 달려 19:31 집에 도착했어요.", short.sentence)
        val round = CardModelBuilder.build(session.copy(distanceKm = 22.5, congestionMin = 0, weather = null), emptyList(), emptyList(), tz)
        assertEquals("18:40 회사에서 출발해 23km 달려 19:31 집에 도착했어요.", round.sentence)
    }
}
