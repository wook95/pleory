package ai.pleos.hello.proto.pleorycard.r2

import android.app.Activity
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView

/*
 * R2-C "한마디" — 운전자가 실제로 한 말이 주인공. 경로는 테두리 없이 창 가장자리까지 번지고(content bleeds),
 * 오른쪽 시트에 말한 그대로를 크게, AI가 나눈 항목은 그 아래 작게 둔다. "AI가 정리했다"가 구조로 보인다.
 * 주 행동은 Pleos 청록. 그래서 '올라옴'은 하늘색으로 15° 이상 옮겨 한 색에 두 뜻이 겹치지 않게 했다.
 */
internal val TONE_C = Tone(
    bg = hex("#0F1314"), plate = hex("#161B1D"), raised = hex("#20272A"), line = hex("#283033"),
    fg = hex("#EAF1F2"), sub = hex("#9CABAF"), faint = hex("#3B4649"),
    primary = hex("#1FB5DA"), onPrimary = hex("#041316"),
    down = hex("#EA6E7C"), neu = hex("#E3A954"), up = hex("#79AEF2"),
    listen = hex("#EAF1F2"), onListen = hex("#0F1314"),
)

// ── S0 ──────────────────────────────────────────────────────
internal fun r2cS0(a: Activity, listening: Boolean, narrow: Boolean = false): View {
    val t = TONE_C
    val e = a.px(48)
    val root = FrameLayout(a).apply { setBackgroundColor(t.bg) }
    root.addView(a.col {
        addView(a.txt(D.SEG, T.HEAD_S, t.fg, 700))
        addView(a.txt("말하면 적어 둘게요", T.BODY_L, t.sub), lp().m(t = a.px(8)))
    }, FrameLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT, Gravity.TOP or Gravity.START).apply { setMargins(e, e, 0, 0) })
    val fg = if (listening) t.onListen else t.onPrimary
    val button = a.col {
        gravity = Gravity.CENTER_VERTICAL
        setPadding(a.px(48), 0, a.px(40), 0)
        background = shape(if (listening) t.listen else t.primary, a.pxf(32f))
        addView(MicGlyph(a, fg), lp(a.px(64), a.px(64)))
        addView(a.txt(if (listening) "듣고 있어요" else "말하기", T.TITLE_L, fg, 700), lp().m(t = a.px(20)))
        addView(a.txt(if (listening) "한 번 더 누르면 취소" else "누르고 바로 말하세요", T.LABEL_M, fg), lp().m(t = a.px(8)))
        contentDescription = if (listening) "듣는 중. 누르면 취소" else "말하기"
    }
    val w = if (narrow) 840 - 96 else 480
    root.addView(button, FrameLayout.LayoutParams(a.px(w), a.px(272), Gravity.BOTTOM or Gravity.START).apply { setMargins(e, 0, 0, e) })
    return if (narrow) a.narrowFrame(root) else root
}

// ── S1 ──────────────────────────────────────────────────────
internal fun r2cS1(a: Activity, narrow: Boolean, summary: Boolean = false, empty: Boolean = false): View {
    val t = TONE_C
    val sheet = a.col {
        addView(a.txt(D.DATE, T.LABEL_M, t.sub, tnum = true))
        addView(a.txt(if (empty) "${D.SEG} 32분" else "${D.SEG} ${D.MIN}분", T.TITLE_M, t.fg, 700, tnum = true), lp().m(t = a.px(10)))
        addView(a.txt(
            if (empty) "19:02 회사 → 19:34 집 · 14 km · 맑음"
            else "${D.T0} ${D.FROM} → ${D.T1} ${D.TO} · ${D.KM} km · ${D.WEATHER}\n${D.JAM_ROAD} 정체 ${D.JAM_MIN}분",
            T.BODY_S, t.sub, tnum = true,
        ), lp().m(t = a.px(10)))
        if (summary || empty) addView(a.row {
            gravity = Gravity.CENTER_VERTICAL
            background = shape(t.raised, a.pxf(32f)) // 안쪽 버튼 20 + 여백 12 (동심 모서리)
            setPadding(a.px(28), a.px(12), a.px(12), a.px(12))
            addView(a.txt(if (empty) D.EMPTY_SUMMARY else D.SUMMARY, T.BODY_L, t.fg, 500), lp(0, WRAP_CONTENT, 1f))
            if (!empty) addView(a.btn(t, "건너뛰기", 2, height = 80, padH = 28), lp(WRAP_CONTENT).m(l = a.px(12)))
        }, lp().m(t = a.px(28)))
        if (empty) {
            addView(a.txt("남긴 한마디가 없어요", T.TITLE_S, t.fg, 600), lp().m(t = a.px(48)))
            addView(a.txt("다음 주행에서 말하기 버튼을 누르고 떠오르는 대로 말해 보세요. 말한 그대로 여기에 남아요.", T.BODY_S, t.sub), lp().m(t = a.px(10)))
        } else {
            addView(flowC(a, t), lp().m(t = a.px(32)))
            D.UTTS.forEachIndexed { i, u -> addView(words(a, t, u, i), lp().m(t = a.px(if (i == 0) 40 else 36))) }
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
        setBackgroundColor(t.bg)
        addView(RouteCanvas(a, t, t.bg, if (narrow) 0.1f else 0.16f, empty = empty), FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
    }

    if (narrow) {
        val root = a.col {
            setBackgroundColor(t.plate)
            addView(ScrollView(a).apply {
                isVerticalScrollBarEnabled = false
                addView(a.col {
                    if (!empty) addView(route, lp(MATCH_PARENT, a.px(320)))
                    addView(sheet.apply { setPadding(a.px(40), a.px(36), a.px(40), a.px(32)) })
                })
            }, lp(MATCH_PARENT, 0, 1f))
            addView(a.rule(t.line), lp(MATCH_PARENT, a.px(1)))
            addView(actions, lp().m(l = a.px(40), t = a.px(20), r = a.px(28), b = a.px(24)))
        }
        return a.narrowFrame(root)
    }
    return a.row {
        setBackgroundColor(t.bg)
        addView(route, lp(0, MATCH_PARENT, 44f))
        addView(a.col {
            setBackgroundColor(t.plate)
            setPadding(a.px(56), a.px(52), a.px(48), a.px(40))
            addView(ScrollView(a).apply { isVerticalScrollBarEnabled = false; addView(sheet) }, lp(MATCH_PARENT, 0, 1f))
            addView(actions, lp().m(t = a.px(24)))
        }, lp(0, MATCH_PARENT, 56f))
    }
}

/** 흐름: 감정 동그라미를 순서대로 잇고, 옆에 말로 풀어 쓴다 */
private fun flowC(a: Activity, t: Tone) = a.row {
    gravity = Gravity.CENTER_VERTICAL
    D.ITEMS.forEachIndexed { i, it ->
        if (i > 0) addView(a.rule(t.faint), lp(a.px(28), a.px(2)))
        addView(MoodDot(a, t, it.mood), lp(a.px(44), a.px(44)))
    }
    addView(a.txt("가라앉았다가 올라왔어요", T.BODY_M, t.fg, 500), lp(WRAP_CONTENT).m(l = a.px(20)))
}

/** 한마디 한 덩이: 말한 그대로(크게) → 시각·장소 → AI가 나눈 항목(작게, 감정 동그라미 + 유형) */
private fun words(a: Activity, t: Tone, u: RUtt, idx: Int) = a.col {
    addView(a.txt("“${u.quote}”", T.TITLE_S, t.fg, 500))
    addView(a.txt("${idx + 1} · ${u.time} ${u.place}${if (u.jam) ", 정체 중" else ""}", T.LABEL_M, t.sub, tnum = true), lp().m(t = a.px(10)))
    u.items.forEach { it ->
        addView(a.row {
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = a.px(80)
            addView(MoodDot(a, t, it.mood), lp(a.px(36), a.px(36)).m(r = a.px(18)))
            val same = u.items.size == 1
            addView(a.txt(if (same) "${it.mood.word} · ${it.type}" else it.text, T.BODY_M, if (same) t.sub else t.fg, if (same) 400 else 500), lp(0, WRAP_CONTENT, 1f))
            if (!same) addView(a.txt(it.type, T.LABEL_M, t.sub), lp(WRAP_CONTENT).m(l = a.px(16)))
            addView(FrameLayout(a).apply {
                background = pressable(shape(Color.TRANSPARENT, a.pxf(16f)), shape(t.raised, a.pxf(16f)))
                contentDescription = "‘${it.text}’ 고치기·지우기"
                isClickable = true
                addView(MoreGlyph(a, t.sub), FrameLayout.LayoutParams(a.px(32), a.px(32), Gravity.CENTER))
            }, LinearLayout.LayoutParams(a.px(72), a.px(72)).m(l = a.px(8)))
        }, lp().m(t = a.px(4)))
    }
}
