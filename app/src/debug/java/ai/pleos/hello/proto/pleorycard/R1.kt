package ai.pleos.hello.proto.pleorycard

import android.app.Activity
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView

// 앱 영역 1682×1210dp 가로. 바깥 여백 40dp.
private const val EDGE = 40

private fun Activity.itemList(gap: Int = 14, showMeta: Boolean = true) = ScrollView(this).apply {
    isVerticalScrollBarEnabled = false
    addView(col {
        ITEMS.forEachIndexed { i, it -> addView(itemRow(it, showMeta), lp().m(t = if (i == 0) 0 else px(gap))) }
    })
}

private fun Activity.actions(withLinks: Boolean) = row {
    gravity = Gravity.CENTER_VERTICAL
    addView(button("15초 요약 듣기", true), lp(WRAP_CONTENT))
    addView(button("수정", false), lp(WRAP_CONTENT).m(l = px(14)))
    addView(qrButton(), lp(WRAP_CONTENT).m(l = px(14)))
    addView(View(context), lp(0, 1, 1f))
    if (withLinks) addView(links(), lp(WRAP_CONTENT))
}

/**
 * A. 좌 경로 40% / 우 패널 60% — PRD §5 기본 배치 그대로.
 * 제목 블록이 우측 패널 맨 위로 올라가고, 왼쪽은 경로·말풍선만.
 */
fun cardA(a: Activity): View = a.row {
    setBackgroundColor(BG)
    addView(FrameLayout(a).apply {
        setBackgroundColor(PANEL)
        addView(RouteView(a, listOf(
            .14f to .86f, .26f to .78f, .38f to .70f, .52f to .58f, .60f to .47f, .74f to .30f, .86f to .14f,
        ), bubbleW = 420), FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
    }, lp(a.px(670), MATCH_PARENT))
    addView(a.col {
        setPadding(a.px(56), a.px(EDGE), a.px(EDGE), a.px(EDGE))
        addView(a.row {
            addView(a.titleBlock(), lp(0, WRAP_CONTENT, 1f))
            addView(a.links(), lp(WRAP_CONTENT).m(t = -a.px(20), r = -a.px(16)))
        })
        addView(a.statsRow(), lp().m(t = a.px(40)))
        addView(a.flowRow(), lp().m(t = a.px(36)))
        addView(a.itemList(), lp(MATCH_PARENT, 0, 1f).m(t = a.px(28)))
        addView(a.actions(withLinks = false), lp().m(t = a.px(24)))
    }, lp(0, MATCH_PARENT, 1f))
}

/**
 * B. 위 경로 띠 / 아래 목록 — 세로 분할(PRD §5 "위 경로 35%")을 가로에도 쓰는 안.
 * 제목과 통계가 한 줄 머리에 같이 서고, 경로는 출발→도착이 왼→오로 읽힌다.
 */
fun cardB(a: Activity): View = a.col {
    setBackgroundColor(BG)
    setPadding(a.px(EDGE), a.px(EDGE), a.px(EDGE), a.px(EDGE))
    addView(a.row {
        gravity = Gravity.BOTTOM
        addView(a.titleBlock(), lp(0, WRAP_CONTENT, 1f))
        addView(a.statsRow(gap = 48), lp(WRAP_CONTENT))
    })
    addView(FrameLayout(a).apply {
        background = round(PANEL, a.pxf(16f))
        clipToOutline = true
        addView(RouteView(a, listOf(
            .05f to .74f, .18f to .68f, .30f to .62f, .44f to .52f, .56f to .44f, .76f to .34f, .95f to .24f,
        ), bubbleW = 680), FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
    }, lp(MATCH_PARENT, a.px(340)).m(t = a.px(28)))
    addView(a.flowRow(), lp().m(t = a.px(28)))
    addView(a.itemList(gap = 10), lp(MATCH_PARENT, 0, 1f).m(t = a.px(20)))
    addView(a.actions(withLinks = true), lp().m(t = a.px(20)))
}

/**
 * C. 목록 우선 · 한마디 타임라인 — 경로는 왼쪽 위 작은 썸네일로 줄이고,
 * 오른쪽은 "한마디 1 → 항목 N" 구조를 시간축으로 그대로 보여 준다(말풍선 대신).
 */
fun cardC(a: Activity): View = a.row {
    setBackgroundColor(BG)
    // 왼쪽 요약 기둥
    addView(a.col {
        setBackgroundColor(PANEL)
        setPadding(a.px(EDGE), a.px(EDGE), a.px(EDGE), a.px(EDGE))
        // 좁은 기둥이라 메타 줄을 의미 단위로 미리 끊는다 ("23분" 홀로 남는 줄 방지)
        addView(a.titleBlock("18:40 회사 → 19:31 집 · 비\n올림픽대로 정체 23분"))
        addView(a.row {
            addView(a.col {
                addView(a.stat(STATS[0]))
                addView(a.stat(STATS[2]), lp().m(t = a.px(28)))
            }, lp(0, WRAP_CONTENT, 1f))
            addView(a.col {
                addView(a.stat(STATS[1]))
                addView(a.stat(STATS[3]), lp().m(t = a.px(28)))
            }, lp(0, WRAP_CONTENT, 1f))
        }, lp().m(t = a.px(40)))
        addView(FrameLayout(a).apply {
            background = round(BG, a.pxf(14f))
            clipToOutline = true
            addView(RouteView(a, listOf(
                .08f to .82f, .22f to .74f, .36f to .64f, .50f to .52f, .62f to .42f, .78f to .28f, .92f to .16f,
            ), bubbleW = 0, grid = false), FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        }, lp(MATCH_PARENT, 0, 1f).m(t = a.px(36)))
        addView(a.button("15초 요약 듣기", true), lp().m(t = a.px(28)))
        addView(a.row {
            addView(a.button("수정", false), lp(0, WRAP_CONTENT, 1f))
            addView(a.qrButton().apply { gravity = Gravity.CENTER }, lp(0, WRAP_CONTENT, 1f).m(l = a.px(12)))
        }, lp().m(t = a.px(12)))
    }, lp(a.px(560), MATCH_PARENT))

    // 오른쪽 타임라인
    addView(a.col {
        setPadding(a.px(56), a.px(EDGE), a.px(EDGE), a.px(EDGE))
        addView(a.row {
            gravity = Gravity.CENTER_VERTICAL
            addView(a.flowRow(), lp(0, WRAP_CONTENT, 1f))
            addView(a.links(), lp(WRAP_CONTENT).m(l = a.px(32), r = -a.px(16)))
        })
        addView(ScrollView(a).apply {
            isVerticalScrollBarEnabled = false
            addView(a.col {
                UTTS.forEachIndexed { i, u -> addView(a.uttGroup(u, last = i == UTTS.lastIndex), lp().m(t = if (i == 0) 0 else a.px(8))) }
            })
        }, lp(MATCH_PARENT, 0, 1f).m(t = a.px(36)))
        addView(a.text("기어 P에서만 표시됩니다 · 주행 중에는 음성 응답만", LABEL_S, SUB), lp().m(t = a.px(16)))
    }, lp(0, MATCH_PARENT, 1f))
}

/** 한마디 한 묶음 — 왼쪽 시각 + 세로선, 오른쪽 장소·원문·항목들 */
private fun Activity.uttGroup(u: Utt, last: Boolean) = row {
    addView(col {
        gravity = Gravity.CENTER_HORIZONTAL
        addView(text(u.at, TITLE_S, FG, 700, 1.1f))
        if (!last) addView(View(context).apply { setBackgroundColor(Color.parseColor("#26344D")) },
            lp(px(3), 0, 1f).m(t = px(14)))
    }, lp(px(110), MATCH_PARENT))
    addView(col {
        addView(text(u.place + "  ·  한마디 → 항목 " + u.items.size, LABEL_M, SUB), lp().m(t = px(6)))
        addView(text("\"" + u.quote + "\"", BODY_S, Color.parseColor("#C9D2DE")), lp().m(t = px(10)))
        u.items.forEach { addView(itemRow(it, showMeta = false), lp().m(t = px(14))) }
    }, lp(0, WRAP_CONTENT, 1f).m(l = px(20), b = px(36)))
}

// ── S0 주행 화면 ────────────────────────────────────────────────

private fun Activity.status(sp: Float) = row {
    gravity = Gravity.CENTER_VERTICAL
    addView(View(context).apply { background = oval(ACCENT) }, lp(px(14), px(14)).m(r = px(16)))
    addView(text("퇴근길 · 기록 중", sp, FG, 700, 1.1f))
}

/** S0-A. PRD 그대로 — 오른쪽 아래 원형 버튼(지름 168), 상태 텍스트는 왼쪽 위 고정 */
fun s0a(a: Activity, listening: Boolean = false): View = FrameLayout(a).apply {
    setBackgroundColor(BG)
    addView(a.status(TITLE_S), FrameLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT, Gravity.TOP or Gravity.START)
        .apply { setMargins(a.px(EDGE), a.px(EDGE), 0, 0) })
    val d = a.px(168)
    addView(a.col {
        gravity = Gravity.CENTER
        background = if (listening) round(Color.parseColor("#0E2F3C"), d / 2f, a.px(8), ACCENT)
        else oval(ACCENT)
        val fg = if (listening) ACCENT else ON_ACCENT
        addView(MicGlyph(a, fg), lp(a.px(56), a.px(56)))
        addView(a.text(if (listening) "듣는 중" else "말하기", LABEL_M, fg, 700, 1.1f), lp(WRAP_CONTENT).m(t = a.px(8)))
    }, FrameLayout.LayoutParams(d, d, Gravity.BOTTOM or Gravity.END).apply {
        setMargins(0, 0, a.px(EDGE), a.px(EDGE))
    })
}

/**
 * S0-B. 운전석 쪽(왼쪽 아래)에 넓은 버튼 440×176.
 * 앱 영역이 화면 오른쪽에 있으므로 왼쪽 모서리가 운전석에서 가장 가깝다(좌핸들 기준).
 */
fun s0b(a: Activity, listening: Boolean = false): View = FrameLayout(a).apply {
    setBackgroundColor(BG)
    addView(a.status(TITLE_L), FrameLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT, Gravity.TOP or Gravity.START)
        .apply { setMargins(a.px(EDGE), a.px(EDGE), 0, 0) })
    addView(a.row {
        gravity = Gravity.CENTER
        background = if (listening) round(Color.parseColor("#0E2F3C"), a.pxf(36f), a.px(8), ACCENT)
        else round(ACCENT, a.pxf(36f))
        val fg = if (listening) ACCENT else ON_ACCENT
        addView(MicGlyph(a, fg), lp(a.px(64), a.px(64)))
        addView(a.text(if (listening) "듣는 중" else "말하기", TITLE_L, fg, 700, 1.1f), lp(WRAP_CONTENT).m(l = a.px(20)))
    }, FrameLayout.LayoutParams(a.px(440), a.px(176), Gravity.BOTTOM or Gravity.START).apply {
        setMargins(a.px(EDGE), 0, 0, a.px(EDGE))
    })
}
