package ai.pleos.hello.proto.pleorycard.r2

import android.app.Activity
import android.graphics.Color
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView

/*
 * R2-A "일지" — 따뜻한 흑연 바탕에 글로 읽히는 카드.
 * 통계 타일·태그 칩·색 점·카드 상자를 없애고, 위계는 글자 크기·굵기·여백·가는 선으로만 만든다.
 * 색은 감정 3색뿐. 주 행동 버튼도 무채색(밝은 잉크)이다.
 */
internal val TONE_A = Tone(
    bg = hex("#161412"), plate = hex("#1D1A17"), raised = hex("#26221E"), line = hex("#2E2A25"),
    fg = hex("#EFE9DF"), sub = hex("#ADA597"), faint = hex("#4A443D"),
    primary = hex("#EFE9DF"), onPrimary = hex("#161412"),
    down = hex("#E8817A"), neu = hex("#D9A957"), up = hex("#56BDD6"),
    listen = hex("#EFE9DF"), onListen = hex("#161412"),
)

// ── S0 ──────────────────────────────────────────────────────
internal fun r2aS0(a: Activity, listening: Boolean, narrow: Boolean = false): View {
    val t = TONE_A
    val root = FrameLayout(a).apply { setBackgroundColor(t.bg) }
    val e = a.px(48)
    root.addView(a.col {
        addView(a.txt(D.SEG, T.TITLE_L, t.fg, 700))
        addView(a.txt("기록하고 있어요", T.BODY_M, t.sub), lp().m(t = a.px(10)))
    }, FrameLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT, Gravity.TOP or Gravity.START).apply { setMargins(e, e, 0, 0) })

    // 기본 = 낮은 면 + 링(차분), 듣는 중 = 밝은 잉크 채움(가장 눈에 띄는 상태가 '지금 듣는 중')
    val fg = if (listening) t.onListen else t.fg
    val button = a.row {
        gravity = Gravity.CENTER_VERTICAL
        setPadding(a.px(44), 0, a.px(40), 0)
        background = if (listening) shape(t.listen, a.pxf(28f)) else shape(t.raised, a.pxf(28f), a.px(2), hex("#6E665B")) // 링 대비 3.2:1(비텍스트 3:1)
        addView(MicGlyph(a, fg), lp(a.px(56), a.px(56)).m(r = a.px(24)))
        addView(a.col {
            addView(a.txt(if (listening) "듣고 있어요" else "말하기", T.TITLE_L, fg, 700))
            if (listening) addView(a.txt("한 번 더 누르면 취소", T.LABEL_M, fg), lp().m(t = a.px(8)))
        }, lp(0, WRAP_CONTENT, 1f))
        contentDescription = if (listening) "듣는 중. 누르면 취소" else "말하기"
    }
    val w = if (narrow) 840 - 96 else 440
    root.addView(button, FrameLayout.LayoutParams(a.px(w), a.px(176), Gravity.BOTTOM or Gravity.START).apply { setMargins(e, 0, 0, e) })
    return if (narrow) a.narrowFrame(root) else root
}

// ── S1 ──────────────────────────────────────────────────────
internal fun r2aS1(a: Activity, narrow: Boolean, summary: Boolean = false, empty: Boolean = false): View {
    val t = TONE_A
    val page = a.col {
        addView(a.txt(D.DATE, T.LABEL_M, t.sub, tnum = true))
        addView(a.txt(if (empty) "${D.SEG}, 32분" else "${D.SEG}, ${D.MIN}분", T.HEAD_S, t.fg, 700), lp().m(t = a.px(12)))
        addView(a.txt(
            if (empty) "19:02 회사를 나와 14km 달려 19:34 집에 왔어요. 맑음."
            else "${D.T0} ${D.FROM}를 나와 ${D.KM}km 달려 ${D.T1} ${D.TO}에 왔어요.\n${D.WEATHER}, ${D.JAM_ROAD} 정체 ${D.JAM_MIN}분.",
            T.BODY_L, t.sub, tnum = true,
        ), lp().m(t = a.px(16)))
        if (!empty) addView(moodLine(a, t), lp().m(t = a.px(32)))
        if (summary || empty) addView(summaryBlock(a, t, if (empty) D.EMPTY_SUMMARY else D.SUMMARY, skip = !empty), lp().m(t = a.px(36)))
        if (empty) {
            addView(a.rule(t.line), lp(MATCH_PARENT, a.px(1)).m(t = a.px(40)))
            addView(a.txt("남긴 한마디가 없어요", T.BODY_M, t.fg, 600), lp().m(t = a.px(32)))
            addView(a.txt("다음에는 운전 중에 말하기 버튼을 누르고 떠오르는 대로 말해 보세요.", T.BODY_S, t.sub), lp().m(t = a.px(8)))
        } else {
            addView(a.txt("한마디 ${D.UTTS.size}개에서 항목 ${D.ITEMS.size}개", T.LABEL_M, t.sub), lp().m(t = a.px(44)))
            addView(a.rule(t.line), lp(MATCH_PARENT, a.px(1)).m(t = a.px(14)))
            D.UTTS.forEachIndexed { i, u ->
                addView(entry(a, t, u, i), lp())
                addView(a.rule(t.line), lp(MATCH_PARENT, a.px(1)))
            }
        }
    }
    val actions = a.row {
        gravity = Gravity.CENTER_VERTICAL
        addView(a.btn(t, if (summary) "다시 듣기" else "15초 요약 듣기", 0, PlayGlyph(a, t.onPrimary)), lp(WRAP_CONTENT))
        addView(a.btn(t, "폰에서 열기", 1, PhoneGlyph(a, t.fg)), lp(WRAP_CONTENT).m(l = a.px(16)))
        addView(View(a), lp(0, 1, 1f))
        addView(a.btn(t, "설정", 2, padH = 28), lp(WRAP_CONTENT))
    }
    val route = FrameLayout(a).apply {
        background = shape(t.plate, a.pxf(28f))
        addView(RouteCanvas(a, t, t.plate, if (narrow) 0.1f else 0.14f), FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
    }

    if (narrow) {
        val root = a.col {
            setBackgroundColor(t.bg)
            addView(ScrollView(a).apply {
                isVerticalScrollBarEnabled = false
                addView(a.col {
                    setPadding(a.px(40), a.px(44), a.px(40), a.px(32))
                    addView(page)
                    if (!empty) addView(route, lp(MATCH_PARENT, a.px(380)).m(t = a.px(36)))
                })
            }, lp(MATCH_PARENT, 0, 1f))
            addView(a.rule(t.line), lp(MATCH_PARENT, a.px(1)))
            addView(actions, lp().m(l = a.px(40), t = a.px(20), r = a.px(28), b = a.px(24)))
        }
        return a.narrowFrame(root)
    }
    return a.row {
        setBackgroundColor(t.bg)
        addView(a.col {
            setPadding(a.px(64), a.px(56), a.px(48), a.px(40))
            addView(ScrollView(a).apply { isVerticalScrollBarEnabled = false; addView(page) }, lp(MATCH_PARENT, 0, 1f))
            addView(actions, lp().m(t = a.px(24)))
        }, lp(0, MATCH_PARENT, 58f))
        if (empty) {
            addView(FrameLayout(a).apply {
                background = shape(t.plate, a.pxf(28f))
                addView(a.txt("오늘 지나온 길", T.LABEL_M, t.sub), FrameLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT, Gravity.TOP or Gravity.START).apply { setMargins(a.px(32), a.px(32), 0, 0) })
                addView(RouteCanvas(a, t, t.plate, 0.16f, empty = true), FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
            }, lp(0, MATCH_PARENT, 42f).m(t = a.px(24), r = a.px(24), b = a.px(24)))
        } else {
            addView(route, lp(0, MATCH_PARENT, 42f).m(t = a.px(24), r = a.px(24), b = a.px(24)))
        }
    }
}

/** ↓ 가라앉음 — → 그냥 — ↑ 올라옴 : 글리프·낱말·색 세 가지로 같은 뜻을 나른다 */
private fun moodLine(a: Activity, t: Tone) = a.row {
    gravity = Gravity.CENTER_VERTICAL
    D.ITEMS.forEachIndexed { i, it ->
        if (i > 0) addView(a.rule(t.faint), lp(a.px(40), a.px(2)).m(l = a.px(16), r = a.px(16)))
        addView(a.moodLabel(t, it.mood, T.BODY_M), lp(WRAP_CONTENT))
    }
}

private fun summaryBlock(a: Activity, t: Tone, s: String, skip: Boolean) = a.row {
    gravity = Gravity.CENTER_VERTICAL
    addView(a.rule(t.fg), lp(a.px(3), a.px(72)).m(r = a.px(24)))
    addView(a.txt(s, T.BODY_L, t.fg, 500), lp(0, WRAP_CONTENT, 1f))
    if (skip) addView(a.btn(t, "건너뛰기", 1, padH = 28), lp(WRAP_CONTENT).m(l = a.px(24)))
}

/** 한마디 하나 = 왼쪽 여백에 시각·장소, 오른쪽에 정리된 항목들. 항목이 여럿이면 말한 그대로를 먼저 보인다. */
private fun entry(a: Activity, t: Tone, u: RUtt, idx: Int) = a.row {
    setPadding(0, a.px(24), 0, a.px(20))
    addView(a.col {
        addView(a.txt(u.time, T.BODY_S, t.fg, 600, tnum = true))
        addView(a.txt(u.place, T.LABEL_S, t.sub), lp().m(t = a.px(6)))
        if (u.jam) addView(a.txt("정체 중", T.LABEL_S, t.sub), lp().m(t = a.px(2)))
    }, lp(a.px(132)))
    addView(a.col {
        if (u.items.size > 1) addView(a.txt("“${u.quote}”", T.LABEL_M, t.sub), lp().m(b = a.px(10)))
        u.items.forEach { addView(itemRow(a, t, it), lp()) }
    }, lp(0, WRAP_CONTENT, 1f))
}

private fun itemRow(a: Activity, t: Tone, it: RItem) = a.row {
    gravity = Gravity.CENTER_VERTICAL
    minimumHeight = a.px(80)
    addView(a.col {
        addView(a.txt(it.text, T.BODY_M, t.fg, 500))
        val meta = SpannableStringBuilder("${it.type}  ·  ")
        val st = meta.length
        meta.append("${it.mood.glyph} ${it.mood.word}")
        meta.setSpan(ForegroundColorSpan(it.mood.color(t)), st, meta.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        addView(a.txt(meta, T.LABEL_M, t.sub), lp().m(t = a.px(6)))
    }, lp(0, WRAP_CONTENT, 1f))
    addView(FrameLayout(a).apply {
        background = pressable(shape(Color.TRANSPARENT, a.pxf(16f)), shape(t.raised, a.pxf(16f)))
        contentDescription = "‘${it.text}’ 고치기·지우기"
        isClickable = true
        addView(MoreGlyph(a, t.sub), FrameLayout.LayoutParams(a.px(32), a.px(32), Gravity.CENTER))
    }, LinearLayout.LayoutParams(a.px(72), a.px(72)))
}
