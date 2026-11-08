package ai.pleos.hello.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LayoutModeTest {
    @Test fun `전체 창 1682dp 는 WIDE`() = assertEquals(LayoutMode.WIDE, LayoutMode.from(1682))
    @Test fun `경계 1200dp 는 WIDE, 1199dp 는 NARROW`() {
        assertEquals(LayoutMode.WIDE, LayoutMode.from(1200))
        assertEquals(LayoutMode.NARROW, LayoutMode.from(1199))
    }
    @Test fun `분할 창 841dp 는 NARROW`() = assertEquals(LayoutMode.NARROW, LayoutMode.from(841))
    @Test fun `S0 버튼은 창 폭 전체 띠 - 양옆 여백 48dp`() {
        assertEquals(1586, LayoutMode.s0ButtonWidthDp(1682))
        assertEquals(744, LayoutMode.s0ButtonWidthDp(840))
        assertEquals("여백보다 좁은 창에서 음수가 되지 않는다", 0, LayoutMode.s0ButtonWidthDp(50))
        assertEquals(0, LayoutMode.s0ButtonWidthDp(0))
    }
    @Test fun `S0 버튼 높이는 창 높이의 3분의 1 - 176에서 400 사이`() {
        assertEquals(400, LayoutMode.s0ButtonHeightDp(1210)) // 403 → 상한
        assertEquals(300, LayoutMode.s0ButtonHeightDp(900))
        assertEquals(400, LayoutMode.s0ButtonHeightDp(2000))
        assertEquals(176, LayoutMode.s0ButtonHeightDp(500))
    }
    @Test fun `낮은 창에서 S0 띠는 위쪽 상태 글(174dp)을 덮지 않는다`() {
        val h = 300
        val top = h - LayoutMode.S0_EDGE_DP - LayoutMode.s0ButtonHeightDp(h)
        assertTrue("띠 윗변 $top", top >= 174)
        assertEquals(0, LayoutMode.s0ButtonHeightDp(100))
    }
}
