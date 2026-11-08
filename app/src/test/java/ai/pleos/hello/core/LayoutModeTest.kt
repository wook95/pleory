package ai.pleos.hello.core

import org.junit.Assert.assertEquals
import org.junit.Test

class LayoutModeTest {
    @Test fun `전체 창 1682dp 는 WIDE`() = assertEquals(LayoutMode.WIDE, LayoutMode.from(1682))
    @Test fun `경계 1200dp 는 WIDE, 1199dp 는 NARROW`() {
        assertEquals(LayoutMode.WIDE, LayoutMode.from(1200))
        assertEquals(LayoutMode.NARROW, LayoutMode.from(1199))
    }
    @Test fun `분할 창 841dp 는 NARROW`() = assertEquals(LayoutMode.NARROW, LayoutMode.from(841))
    @Test fun `S0 버튼 폭은 40퍼센트이고 440dp 를 넘지 않는다`() {
        assertEquals(440, LayoutMode.s0ButtonWidthDp(1682))
        assertEquals(336, LayoutMode.s0ButtonWidthDp(840))
        assertEquals(440, LayoutMode.s0ButtonWidthDp(1100))
    }
    @Test fun `S0 버튼 높이는 80에서 176 사이`() {
        assertEquals(175, LayoutMode.s0ButtonHeightDp(1210))
        assertEquals(176, LayoutMode.s0ButtonHeightDp(2000))
        assertEquals(80, LayoutMode.s0ButtonHeightDp(300))
    }
}
