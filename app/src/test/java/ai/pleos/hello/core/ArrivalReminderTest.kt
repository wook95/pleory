package ai.pleos.hello.core

import ai.pleos.hello.data.ItemType
import ai.pleos.hello.data.Segment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.TimeZone

class ArrivalReminderTest {
    private val tz = TimeZone.getTimeZone("Asia/Seoul")
    private val hour = 60 * 60 * 1000L
    private val day = 24 * hour

    // 2026-11-08 08:30 KST (= 2026-11-07 23:30 UTC)
    private val now = 1_794_094_200_000L

    private fun c(id: Long, session: Long = 1, at: Long = now - day, text: String = "할 일 $id", segment: Segment = Segment.COMMUTE_OUT) =
        ReminderCandidate(itemId = id, text = text, sessionId = session, segment = segment, spokenAt = at)

    @Test fun select_onlyPreviousSessions_excludesDoneDeletedRemindedOldAndNonTodo() {
        val current = 9L
        val picked = ArrivalReminder.select(
            listOf(
                c(1),
                c(2, session = current),
                c(3).copy(done = true),
                c(4).copy(deleted = true),
                c(5).copy(remindedAt = now - hour),
                c(6, at = now - 8 * day),
                c(7).copy(type = ItemType.THOUGHT),
                c(8, at = now - 7 * day), // 경계: 정확히 7일은 포함
                c(10, at = now + hour), // 미래(시계 어긋남)는 제외
            ),
            currentSessionId = current, nowMillis = now,
        )
        assertEquals(listOf(1L, 8L), picked.map { it.itemId })
    }

    @Test fun select_ordersNewestFirst_thenItemId() {
        val picked = ArrivalReminder.select(listOf(c(3, at = now - 2 * day), c(2, at = now - day), c(1, at = now - day)), 9, now)
        assertEquals(listOf(1L, 2L, 3L), picked.map { it.itemId })
    }

    @Test fun line_yesterdayCommuteOut_single() {
        val line = ArrivalReminder.line(listOf(c(1, text = "거래처 서류 다시 보내기")), now, parked = true, tz = tz)
        assertEquals("어제 퇴근길에 할 일 하나 말했어. 거래처 서류 다시 보내기.", line)
    }

    @Test fun line_multipleSameSession_countAndRest() {
        val items = listOf(c(1, text = "거래처 서류 다시 보내기."), c(2), c(3))
        assertEquals("어제 퇴근길에 할 일 셋 말했어. 거래처 서류 다시 보내기 외 2개.", ArrivalReminder.line(items, now, true, tz))
    }

    @Test fun line_multipleSessions_genericPlace() {
        val items = listOf(c(1, session = 1, text = "우유 사기"), c(2, session = 2, at = now - 3 * day))
        assertEquals("지난 주행에서 할 일 둘 말했어. 우유 사기 외 1개.", ArrivalReminder.line(items, now, true, tz))
    }

    @Test fun line_longItemTruncated() {
        val long = "가".repeat(40)
        val line = ArrivalReminder.line(listOf(c(1, text = long, segment = Segment.COMMUTE_IN)), now, true, tz)!!
        assertEquals("어제 출근길에 할 일 하나 말했어. ${"가".repeat(ArrivalReminder.MAX_ITEM_CHARS)}….", line)
    }

    @Test fun line_suppressedWhileDriving_orEmpty() {
        assertNull(ArrivalReminder.line(listOf(c(1)), now, parked = false, tz = tz))
        assertNull(ArrivalReminder.line(emptyList(), now, parked = true, tz = tz))
    }

    @Test fun line_dropsLeadingRelativeDayOfTheItem() {
        val picked = ArrivalReminder.select(listOf(c(1, text = "내일 거래처 서류 다시 보내기")), currentSessionId = 9, nowMillis = now)
        assertEquals("어제 퇴근길에 할 일 하나 말했어. 거래처 서류 다시 보내기.", ArrivalReminder.line(picked, now, parked = true, tz = tz))
    }

    @Test fun itemText_stripsOnlyALeadingDayWord() {
        assertEquals("거래처 서류 다시 보내기", ArrivalReminder.itemText(" 오늘 거래처 서류 다시 보내기"))
        assertEquals("보고서 내일 보내기", ArrivalReminder.itemText("보고서 내일 보내기"))
        assertEquals("내일모레 출장 준비", ArrivalReminder.itemText("내일모레 출장 준비")) // 낱말 경계가 아니면 그대로
        assertEquals("내일", ArrivalReminder.itemText("내일")) // 떼면 빈 문구가 되면 그대로
    }

    @Test fun relativeDay_calendarDaysInZone() {
        // 오늘 00:10 KST 는 오늘, 어제 23:50 KST 는 어제(시간 차는 9시간 미만이어도)
        val midnight = now - (8 * hour + 30 * 60 * 1000L)
        assertEquals("오늘", ArrivalReminder.relativeDay(midnight + 10 * 60 * 1000L, now, tz))
        assertEquals("어제", ArrivalReminder.relativeDay(midnight - 10 * 60 * 1000L, now, tz))
        assertEquals("그저께", ArrivalReminder.relativeDay(now - 2 * day, now, tz))
        assertEquals("5일 전", ArrivalReminder.relativeDay(now - 5 * day, now, tz))
        assertEquals("오늘 외출 중에 할 일 하나 말했어. 약 받기.", ArrivalReminder.line(listOf(c(1, at = now - hour, text = "약 받기", segment = Segment.OUTING)), now, true, tz))
    }
}
