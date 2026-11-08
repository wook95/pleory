package ai.pleos.hello.proto.pleorycard.r2

import android.app.Activity
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView

/*
 * R2-B "계기" — 흑연 무채색 + 강조색 하나(시안 청록은 '듣는 중'과 '올라옴'에만).
 * 감정 흐름을 색 막대 대신 주행 시간축 위의 높낮이 선으로 그린다: 높이가 뜻을 나르고 색은 거든다.
 * 통계는 따로 타일로 세우지 않고 시간축 양끝(출발·도착)과 제목에 녹인다. 숫자는 고정폭(tnum).
 */
internal val TONE_B = Tone(
    bg = hex("#0E0F11"), plate = hex("#16181B"), raised = hex("#1F2226"), line = hex("#26292E"),
    fg = hex("#ECEEF0"), sub = hex("#9EA3AA"), faint = hex("#3A3E44"),
    primary = hex("#ECEEF0"), onPrimary = hex("#0E0F11"),
    down = hex("#EC7A86"), neu = hex("#DDB168"), up = hex("#2BBBDD"),
    listen = hex("#2BBBDD"), onListen = hex("#04161B"),
)

// ── S0 ──────────────────────────────────────────────────────
internal fun r2bS0(a: Activity, listening: Boolean, narrow: Boolean = false): View {
    val t = TONE_B
    val e = a.px(48)
    val root = FrameLayout(a).apply { setBackgroundColor(t.bg) }
    root.addView(a.txt("${D.SEG} 기록 중", T.TITLE_M, t.sub, 600), FrameLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT, Gravity.TOP or Gravity.START).apply { setMargins(e, e, 0, 0) })
    // 창 폭 전체를 쓰는 낮은 띠 버튼. 운전석 쪽(왼쪽)에 글자를 둔다.
    val fg = if (listening) t.onListen else t.fg
    val bar = a.row {
        gravity = Gravity.CENTER_VERTICAL
        setPadding(a.px(48), 0, a.px(48), 0)
        background = if (listening) shape(t.listen, a.pxf(32f)) else shape(t.raised, a.pxf(32f), a.px(2), hex("#5E636B")) // 링 대비 3.2:1
        addView(MicGlyph(a, fg), lp(a.px(56), a.px(56)).m(r = a.px(24)))
        addView(a.txt(if (listening) "듣고 있어요" else "말하기", T.TITLE_L, fg, 700), lp(0, WRAP_CONTENT, 1f))
        if (listening) addView(a.txt("한 번 더 누르면 취소", T.BODY_S, fg, 500), lp(WRAP_CONTENT))
        contentDescription = if (listening) "듣는 중. 누르면 취소" else "말하기"
    }
    root.addView(bar, FrameLayout.LayoutParams(MATCH_PARENT, a.px(176), Gravity.BOTTOM).apply { setMargins(e, 0, e, e) })
    return if (narrow) a.narrowFrame(root) else root
}

// ── S1 ──────────────────────────────────────────────────────
internal fun r2bS1(a: Activity, narrow: Boolean, summary: Boolean = false, empty: Boolean = false): View {
    val t = TONE_B
    val header = a.col {
        addView(a.txt(if (empty) "${D.SEG} 32분" else "${D.SEG} ${D.MIN}분", T.HEAD_S, t.fg, 700, tnum = true))
        addView(a.txt("${D.DATE} · ${if (empty) "맑음 · 14 km" else "${D.WEATHER} · ${D.KM} km"}", T.BODY_S, t.sub, tnum = true), lp().m(t = a.px(8)))
    }
    val chart = FrameLayout(a).apply {
        background = shape(t.plate, a.pxf(24f))
        addView(MoodChart(a, t, empty), FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
    }
    val list = a.col {
        if (empty) {
            addView(a.txt("남긴 한마디가 없어요", T.BODY_M, t.fg, 600))
            addView(a.txt("운전 중에 말하기 버튼을 누르고 떠오르는 대로 말하면 여기에 쌓여요.", T.BODY_S, t.sub), lp().m(t = a.px(8)))
        } else {
            addView(a.txt("한마디 ${D.UTTS.size} → 항목 ${D.ITEMS.size}", T.LABEL_M, t.sub, tnum = true), lp().m(b = a.px(4)))
            D.UTTS.forEachIndexed { i, u ->
                addView(a.rule(t.line), lp(MATCH_PARENT, a.px(1)))
                if (u.items.size > 1) addView(a.txt("말한 그대로 “${u.quote}”", T.LABEL_M, t.sub).apply {
                    setPadding(a.px(COL_TIME + COL_MOOD), a.px(18), 0, a.px(2))
                })
                u.items.forEachIndexed { j, it -> addView(rowB(a, t, u, i, j, it), lp()) }
            }
            addView(a.rule(t.line), lp(MATCH_PARENT, a.px(1)))
        }
    }
    val route = FrameLayout(a).apply {
        background = shape(t.plate, a.pxf(24f))
        addView(RouteCanvas(a, t, t.plate, 0.12f, empty = empty), FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
    }
    val actions = a.row {
        gravity = Gravity.CENTER_VERTICAL
        addView(a.btn(t, if (summary) "다시 듣기" else "15초 요약 듣기", 0, PlayGlyph(a, t.onPrimary)), lp(WRAP_CONTENT))
        addView(a.btn(t, "폰에서 열기", 1, PhoneGlyph(a, t.fg)), lp(WRAP_CONTENT).m(l = a.px(16)))
        addView(View(a), lp(0, 1, 1f))
        addView(a.btn(t, "설정", 1, padH = 32), lp(WRAP_CONTENT))
    }
    val sub = if (summary || empty) a.row {
        gravity = Gravity.CENTER_VERTICAL
        background = shape(t.raised, a.pxf(32f)) // 안쪽 버튼 20 + 여백 12 (동심 모서리)
        setPadding(a.px(32), a.px(12), a.px(12), a.px(12))
        addView(a.txt(if (empty) D.EMPTY_SUMMARY else D.SUMMARY, T.BODY_L, t.fg, 500), lp(0, WRAP_CONTENT, 1f))
        if (!empty) addView(a.btn(t, "건너뛰기", 2, height = 80, padH = 28), lp(WRAP_CONTENT).m(l = a.px(16)))
    } else null

    if (narrow) {
        val root = a.col {
            setBackgroundColor(t.bg)
            addView(ScrollView(a).apply {
                isVerticalScrollBarEnabled = false
                addView(a.col {
                    setPadding(a.px(40), a.px(44), a.px(40), a.px(32))
                    addView(header)
                    addView(chart, lp(MATCH_PARENT, a.px(320)).m(t = a.px(28)))
                    sub?.let { addView(it, lp().m(t = a.px(20))) }
                    addView(list, lp().m(t = a.px(32)))
                    if (!empty) addView(route, lp(MATCH_PARENT, a.px(360)).m(t = a.px(32)))
                })
            }, lp(MATCH_PARENT, 0, 1f))
            addView(a.rule(t.line), lp(MATCH_PARENT, a.px(1)))
            addView(actions, lp().m(l = a.px(40), t = a.px(20), r = a.px(40), b = a.px(24)))
        }
        return a.narrowFrame(root)
    }
    return a.col {
        setBackgroundColor(t.bg)
        setPadding(a.px(48), a.px(44), a.px(48), a.px(36))
        addView(header)
        addView(chart, lp(MATCH_PARENT, a.px(300)).m(t = a.px(28)))
        addView(a.row {
            addView(ScrollView(a).apply { isVerticalScrollBarEnabled = false; addView(list) }, lp(0, MATCH_PARENT, 58f))
            addView(route, lp(0, MATCH_PARENT, 42f).m(l = a.px(40)))
        }, lp(MATCH_PARENT, 0, 1f).m(t = a.px(32)))
        sub?.let { addView(it, lp().m(t = a.px(24))) }
        addView(actions, lp().m(t = a.px(24)))
    }
}

private const val COL_TIME = 116
private const val COL_MOOD = 64

/** 표 한 줄: 번호·시각 | 감정 글리프 | 항목 문장 | 유형 | 더보기 */
private fun rowB(a: Activity, t: Tone, u: RUtt, ui: Int, j: Int, it: RItem) = a.row {
    gravity = Gravity.CENTER_VERTICAL
    minimumHeight = a.px(84)
    addView(a.txt(if (j == 0) "${ui + 1}  ${u.time}" else "", T.BODY_S, t.sub, 500, tnum = true), lp(a.px(COL_TIME)))
    addView(MoodDot(a, t, it.mood), lp(a.px(40), a.px(40)).m(r = a.px(COL_MOOD - 40)))
    addView(a.txt(it.text, T.BODY_M, t.fg, 500), lp(0, WRAP_CONTENT, 1f))
    addView(a.txt(it.type, T.LABEL_M, t.sub).apply { gravity = Gravity.END }, lp(a.px(88)))
    addView(FrameLayout(a).apply {
        background = pressable(shape(Color.TRANSPARENT, a.pxf(16f)), shape(t.raised, a.pxf(16f)))
        contentDescription = "‘${it.text}’ 고치기·지우기"
        isClickable = true
        addView(MoreGlyph(a, t.sub), FrameLayout.LayoutParams(a.px(32), a.px(32), Gravity.CENTER))
    }, LinearLayout.LayoutParams(a.px(72), a.px(72)).m(l = a.px(8)))
}

/** 감정 동그라미: 감정 색 링 + 같은 색 글리프. 접근성 이름에 낱말을 넣는다. */
internal class MoodDot(c: Context, private val t: Tone, private val m: Mood) : View(c) {
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val g = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; typeface = face(700) }
    init { contentDescription = m.word }
    override fun onDraw(c: Canvas) {
        val w = width.toFloat(); val r = w / 2
        ring.color = m.color(t); ring.strokeWidth = w * 0.07f
        c.drawCircle(r, r, r - ring.strokeWidth, ring)
        g.color = m.color(t); g.textSize = w * 0.58f
        c.drawText(m.glyph, r, r + g.textSize * 0.36f, g)
    }
}

/**
 * 주행 시간축 + 감정 높낮이.
 * 위 3단(올라옴/그냥/가라앉음)에 항목을 말한 시각 위치로 찍고 선으로 잇는다. 아래 축에 출발·도착·정체 구간·한마디 번호.
 */
private class MoodChart(c: Context, private val t: Tone, private val empty: Boolean) : View(c) {
    private val d = resources.displayMetrics.density
    private val txt = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 22 * d; typeface = face(500); fontFeatureSettings = "tnum" }
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onDraw(c: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        val left = 168 * d; val right = w - 40 * d
        val top = 40 * d; val levelGap = 56 * d
        val axisY = top + levelGap * 2 + 64 * d
        fun ly(m: Mood) = top + levelGap * when (m) { Mood.UP -> 0; Mood.NEU -> 1; Mood.DOWN -> 2 }
        fun tx(f: Float) = left + f * (right - left)

        // 단 이름 + 안내선
        line.pathEffect = DashPathEffect(floatArrayOf(4 * d, 8 * d), 0f); line.strokeWidth = 2 * d; line.color = t.faint
        for (m in listOf(Mood.UP, Mood.NEU, Mood.DOWN)) {
            c.drawLine(left, ly(m), right, ly(m), line)
            txt.textAlign = Paint.Align.LEFT; txt.color = m.color(t); txt.typeface = face(700)
            c.drawText(m.glyph, 32 * d, ly(m) + 8 * d, txt)
            txt.color = t.sub; txt.typeface = face(500)
            c.drawText(m.word, 60 * d, ly(m) + 8 * d, txt)
        }
        line.pathEffect = null

        // 시간축
        line.color = t.sub; line.strokeWidth = 2 * d
        c.drawLine(left, axisY, right, axisY, line)
        // 정체 구간: 축 위 두꺼운 빗금 띠
        val jx0 = tx(D.JAM_FROM); val jx1 = tx(D.JAM_TO)
        fill.color = t.raised; c.drawRect(RectF(jx0, axisY - 10 * d, jx1, axisY + 10 * d), fill)
        line.color = t.faint; line.strokeWidth = 2 * d
        var hx = jx0 - 20 * d
        c.save(); c.clipRect(jx0, axisY - 10 * d, jx1, axisY + 10 * d)
        while (hx < jx1) { c.drawLine(hx, axisY + 10 * d, hx + 20 * d, axisY - 10 * d, line); hx += 12 * d }
        c.restore()
        txt.color = t.sub; txt.textAlign = Paint.Align.CENTER
        c.drawText("${D.JAM_ROAD} 정체 ${D.JAM_MIN}분", (jx0 + jx1) / 2, axisY + 44 * d, txt)
        // 양끝
        txt.textAlign = Paint.Align.LEFT; txt.color = t.fg
        c.drawText(if (empty) "19:02 회사" else "${D.T0} ${D.FROM}", left, axisY - 18 * d, txt)
        txt.textAlign = Paint.Align.RIGHT
        c.drawText(if (empty) "19:34 집" else "${D.T1} ${D.TO}", right, axisY - 18 * d, txt)
        fill.color = t.fg
        c.drawCircle(left, axisY, 6 * d, fill); c.drawCircle(right, axisY, 6 * d, fill)

        if (empty) {
            txt.textAlign = Paint.Align.CENTER; txt.color = t.sub
            c.drawText("남긴 한마디 없음", (left + right) / 2, ly(Mood.NEU) + 8 * d, txt)
            return
        }

        // 한마디 눈금 + 번호
        D.UTTS.forEachIndexed { i, u ->
            val x = tx(u.at)
            line.color = t.fg; line.strokeWidth = 3 * d
            c.drawLine(x, axisY - 12 * d, x, axisY + 12 * d, line)
            txt.textAlign = Paint.Align.CENTER; txt.color = t.fg
            c.drawText("${i + 1}", x, axisY - 22 * d, txt)
        }
        // 감정 점과 이음선 (같은 한마디의 항목은 20dp 간격으로 나란히)
        val pts = D.UTTS.flatMap { u -> u.items.mapIndexed { j, it -> (tx(u.at) + j * 40 * d) to it.mood } }
        line.color = t.sub; line.strokeWidth = 3 * d
        val p = Path().apply { moveTo(pts[0].first, ly(pts[0].second)); pts.drop(1).forEach { lineTo(it.first, ly(it.second)) } }
        c.drawPath(p, line)
        for ((x, m) in pts) {
            fill.color = t.plate; c.drawCircle(x, ly(m), 15 * d, fill)
            fill.color = m.color(t); c.drawCircle(x, ly(m), 11 * d, fill)
        }
    }
}
