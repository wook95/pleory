package ai.pleos.hello.core

import ai.pleos.hello.data.Emotion
import ai.pleos.hello.data.Item
import ai.pleos.hello.data.ItemType
import ai.pleos.hello.data.Segment
import ai.pleos.hello.data.Session
import ai.pleos.hello.data.Utterance
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoryTest {
    private val tz = TimeZone.getTimeZone("Asia/Seoul")
    private val min = 60_000L
    private val day = 24 * 60 * min

    /** 2026-11-08(일) 20:00 KST */
    private val now = 1_794_135_600_000L

    /** 2026-11-08 0:00 KST */
    private val today0 = now - 20 * 60 * min

    private fun session(id: Long, startedAt: Long, durationMin: Int = 51, endedAt: Long? = startedAt + durationMin * min, segment: Segment = Segment.COMMUTE_OUT) =
        Session(id = id, profileId = "p", startedAt = startedAt, endedAt = endedAt, segment = segment, distanceKm = 23.4, durationMin = durationMin)

    private fun utt(id: Long, sessionId: Long, at: Long, text: String = "한마디 $id") = Utterance(id = id, sessionId = sessionId, at = at, text = text)

    private fun item(id: Long, utteranceId: Long, type: ItemType, emotion: Emotion = Emotion.NEUTRAL, order: Int = 0, deleted: Boolean = false, text: String = "항목 $id") =
        Item(id = id, utteranceId = utteranceId, order = order, text = text, type = type, emotion = emotion, deleted = deleted)

    @Test
    fun `7일 경계는 오늘 0시 기준 6일 전 0시`() {
        assertEquals(today0 - 6 * day, History.windowStart(now, tz))
        assertEquals(today0 - 6 * day, History.windowStart(today0, tz)) // 0시 정각도 같은 날
        assertEquals(today0 - 6 * day, History.windowStart(today0 + day - 1, tz)) // 23:59:59.999 까지 같은 창
        assertEquals(today0 - 5 * day, History.windowStart(today0 + day, tz)) // 다음 날 0시에 하루 밀린다
    }

    @Test
    fun `경계 안쪽만 최신순으로 남는다`() {
        val since = today0 - 6 * day
        val sessions = listOf(
            session(1, since - 1),          // 경계 1ms 전 → 빠짐
            session(2, since),              // 경계 정각 → 들어감
            session(3, today0 + 18 * 60 * min),
            session(4, today0 - day + 8 * 60 * min),
        )
        val rows = History.rows(sessions, emptyList(), emptyList(), now, tz = tz)
        assertEquals(listOf(3L, 4L, 2L), rows.map { it.sessionId })
    }

    @Test
    fun `안 끝난 세션과 빈 세션은 빠진다`() {
        val t = today0 + 9 * 60 * min
        val sessions = listOf(
            session(1, t, endedAt = null),       // 진행 중
            session(2, t + min, durationMin = 0), // 시동만 켰다 끈 빈 세션
            session(3, t + 2 * min, durationMin = 0), // 0분이지만 한마디가 있으면 남긴다
        )
        val rows = History.rows(sessions, listOf(utt(30, 3, t + 2 * min)), emptyList(), now, tz = tz)
        assertEquals(listOf(3L), rows.map { it.sessionId })
    }

    @Test
    fun `지운 항목은 수와 감정과 할 일에서 빠진다`() {
        val t = today0 + 18 * 60 * min
        val rows = History.rows(
            listOf(session(1, t)),
            listOf(utt(10, 1, t + 5 * min), utt(11, 1, t + 10 * min), utt(12, 1, t + 20 * min)),
            listOf(
                item(100, 10, ItemType.EMOTION, Emotion.DOWN),
                item(101, 11, ItemType.TODO, order = 0, text = "서류 보내기"),
                item(102, 11, ItemType.EMOTION, Emotion.UP, order = 1),
                item(103, 11, ItemType.TODO, order = 2, deleted = true, text = "지운 할 일"),
                item(104, 12, ItemType.EMOTION, Emotion.DOWN, deleted = true), // 한마디의 항목을 모두 지움 → 0개
            ),
            now, tz = tz,
        )
        val r = rows.single()
        assertEquals(3, r.itemCount)
        assertEquals(listOf(Emotion.DOWN, Emotion.NEUTRAL, Emotion.UP), r.moods)
        assertEquals(listOf("서류 보내기"), r.todos)
    }

    @Test
    fun `정리 전 한마디는 원문 한 줄로 세고 감정 흐름에는 넣지 않는다`() {
        val t = today0 + 18 * 60 * min
        val r = History.rows(listOf(session(1, t)), listOf(utt(10, 1, t + min)), emptyList(), now, tz = tz).single()
        assertEquals(1, r.itemCount)
        assertTrue(r.moods.isEmpty())
        assertTrue(r.todos.isEmpty())
    }

    @Test
    fun `할 일만 필터는 할 일이 남은 세션만`() {
        val t = today0 + 8 * 60 * min
        val sessions = listOf(session(1, t), session(2, t + 60 * min), session(3, t + 120 * min))
        val utterances = listOf(utt(10, 1, t + min), utt(20, 2, t + 61 * min), utt(30, 3, t + 121 * min))
        val items = listOf(
            item(100, 10, ItemType.TODO, text = "약 사기"),
            item(200, 20, ItemType.EMOTION, Emotion.UP),
            item(300, 30, ItemType.TODO, deleted = true),
        )
        assertEquals(listOf(3L, 2L, 1L), History.rows(sessions, utterances, items, now, tz = tz).map { it.sessionId })
        val todo = History.rows(sessions, utterances, items, now, todoOnly = true, tz = tz)
        assertEquals(listOf(1L), todo.map { it.sessionId })
        assertEquals(listOf("약 사기"), todo.single().todos)
    }

    @Test
    fun `기록이 없으면 빈 목록`() {
        assertTrue(History.rows(emptyList(), emptyList(), emptyList(), now, tz = tz).isEmpty())
        assertTrue(History.rows(listOf(session(1, today0 + min)), emptyList(), emptyList(), now, todoOnly = true, tz = tz).isEmpty())
    }

    @Test
    fun `행 매핑 날짜 구간 시각 통계`() {
        val todayRow = History.rows(listOf(session(1, today0 + (18 * 60 + 40) * min)), emptyList(), emptyList(), now, tz = tz).single()
        assertEquals("오늘", todayRow.day)
        assertEquals("퇴근길", todayRow.segment)
        assertEquals("18:40–19:31", todayRow.timeRange)
        assertEquals("51분 · 23km", todayRow.stats)
        assertEquals(0, todayRow.itemCount)

        val yesterday = History.rows(listOf(session(2, today0 - day + 8 * 60 * min, segment = Segment.COMMUTE_IN)), emptyList(), emptyList(), now, tz = tz).single()
        assertEquals("어제", yesterday.day)
        assertEquals("출근길", yesterday.segment)

        val older = History.rows(listOf(session(3, today0 - 3 * day + 14 * 60 * min, segment = Segment.OUTING)), emptyList(), emptyList(), now, tz = tz).single()
        assertEquals("11월 5일 목요일", older.day)
        assertEquals("외출", older.segment)
    }

    @Test
    fun `끝이 시작보다 이르면 시작 시각만`() {
        val s = session(1, today0 + (19 * 60 + 54) * min, durationMin = 0).copy(endedAt = today0 + (18 * 60 + 40) * min)
        val r = History.rows(listOf(s), listOf(utt(10, 1, s.startedAt)), emptyList(), now, tz = tz).single()
        assertEquals("19:54", r.timeRange)
    }

    @Test
    fun `짧은 거리는 소수 한 자리`() {
        val s = session(1, today0 + min).copy(distanceKm = 0.43, durationMin = 3)
        assertEquals("3분 · 0.4km", History.rows(listOf(s), emptyList(), emptyList(), now, tz = tz).single().stats)
    }
}
