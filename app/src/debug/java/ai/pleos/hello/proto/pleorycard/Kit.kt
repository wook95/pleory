package ai.pleos.hello.proto.pleorycard

import android.app.Activity
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.CornerPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.text.LineBreakConfig
import android.os.Build
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.LinearLayout
import android.widget.TextView

// ── 색 (PRD §3) ─────────────────────────────────────────────
internal val BG = Color.parseColor("#0B1220")
internal val PANEL = Color.parseColor("#0F1828")   // 지도·보조 패널 바탕 (BG 보다 한 단계 밝게)
internal val SURFACE = Color.parseColor("#141F33") // 항목 행
internal val CHIP = Color.parseColor("#1D2A42")
internal val LINE = Color.parseColor("#1A2538")
internal val FG = Color.parseColor("#E8ECF2")
internal val SUB = Color.parseColor("#9AA8BC")     // 보조 글자 — BG 대비 약 7.6:1
internal val ACCENT = Color.parseColor("#00AAD2")
internal val ON_ACCENT = Color.parseColor("#04121A")
internal val DOWN = Color.parseColor("#E05A6A")
internal val NEUTRAL = Color.parseColor("#E0A24A")
internal val UP = Color.parseColor("#00AAD2")

// ── 글자 크기 (Pleos 디자인 가이드 Typography, 1dp = 1px) ───────────
internal const val HEADLINE_S = 48f
internal const val TITLE_L = 40f
internal const val TITLE_S = 32f
internal const val BODY_M = 28f
internal const val BODY_S = 26f
internal const val LABEL_M = 24f
internal const val LABEL_S = 20f
internal const val TOUCH = 80 // 터치 타깃 최소 (AAOS 76dp 이상)

// ── 데모 데이터 (PRD §8, card_mock.png) ─────────────────────────
internal const val DATE = "오늘의 나 · 11월 6일 금요일"
internal const val TITLE = "퇴근길 51분"
internal const val META = "18:40 회사 → 19:31 집 · 비 · 올림픽대로 정체 23분"

internal class Stat(val value: String, val unit: String, val label: String)
internal val STATS = listOf(
    Stat("51", "분", "주행 시간"), Stat("23", "km", "주행 거리"),
    Stat("2", "", "한마디"), Stat("3", "", "항목"),
)

internal class Item(val text: String, val tag: String, val color: Int, val meta: String)
internal class Utt(val at: String, val place: String, val quote: String, val items: List<Item>)

internal val UTTS = listOf(
    Utt("18:57", "올림픽대로 · 정체 23분 구간", "오늘 팀장 때문에 진짜 힘들었다", listOf(
        Item("오늘 팀장 때문에 진짜 힘들었다", "감정", DOWN, "18:57 · 올림픽대로 · 정체 23분 구간"),
    )),
    Utt("19:08", "한강대교", "내일 거래처 서류 다시 보내야 해, 아 노을 예쁘다", listOf(
        Item("내일 거래처 서류 다시 보내기", "할 일", NEUTRAL,
            "19:08 · 한강대교 · 원문 \"내일 거래처 서류 다시 보내야 해, 아 노을 예쁘다\""),
        Item("노을이 예뻤다", "감정", UP, "19:08 · 한강대교"),
    )),
)
internal val ITEMS = UTTS.flatMap { it.items }

// ── 기본 조립 도구 ─────────────────────────────────────────────
internal fun Context.px(dp: Int) = (dp * resources.displayMetrics.density).toInt()
internal fun Context.pxf(dp: Float) = dp * resources.displayMetrics.density

internal fun weight(w: Int) = Typeface.create(Typeface.DEFAULT, w, false)

/** 한글 단어 중간 줄바꿈 방지 (API 33+ 구 단위 줄바꿈) */
internal fun TextView.keepAll() {
    if (Build.VERSION.SDK_INT >= 33) {
        lineBreakWordStyle = LineBreakConfig.LINE_BREAK_WORD_STYLE_PHRASE
    }
}

internal fun Context.text(s: CharSequence, sp: Float, color: Int = FG, w: Int = 400, lh: Float = 1.3f) =
    TextView(this).apply {
        text = s
        textSize = sp
        setTextColor(color)
        typeface = weight(w)
        includeFontPadding = false
        setLineSpacing(sp * (lh - 1f) * resources.displayMetrics.density, 1f)
        keepAll()
    }

internal fun round(color: Int, r: Float, stroke: Int = 0, strokeColor: Int = 0) = GradientDrawable().apply {
    cornerRadius = r
    setColor(color)
    if (stroke > 0) setStroke(stroke, strokeColor)
}

internal fun oval(color: Int) = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(color) }

internal fun lp(w: Int = MATCH_PARENT, h: Int = WRAP_CONTENT, weight: Float = 0f) =
    LinearLayout.LayoutParams(w, h, weight)

internal fun LinearLayout.LayoutParams.m(l: Int = 0, t: Int = 0, r: Int = 0, b: Int = 0) =
    apply { setMargins(l, t, r, b) }

internal fun Context.col(block: LinearLayout.() -> Unit = {}) =
    LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; block() }

internal fun Context.row(block: LinearLayout.() -> Unit = {}) =
    LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; block() }

// ── 공통 블록 ─────────────────────────────────────────────────

/** 제목 블록 — 날짜 / 퇴근길 51분 / 메타 줄 */
internal fun Activity.titleBlock(meta: String = META) = col {
    addView(text(DATE, LABEL_M, SUB))
    addView(text(TITLE, HEADLINE_S, FG, 700, 1.2f), lp().m(t = px(10)))
    addView(text(meta, BODY_S, SUB), lp().m(t = px(10)))
}

/** 통계 1개 — 숫자 + 단위, 아래 라벨 */
internal fun Activity.stat(s: Stat, numSp: Float = TITLE_L) = col {
    addView(row {
        gravity = Gravity.BOTTOM
        addView(text(s.value, numSp, FG, 700, 1.1f))
        if (s.unit.isNotEmpty()) addView(text(s.unit, LABEL_M, SUB, 500, 1.1f), lp(WRAP_CONTENT).m(l = px(4), b = px(3)))
    })
    addView(text(s.label, LABEL_S, SUB), lp().m(t = px(8)))
}

internal fun Activity.statsRow(gap: Int = 56, numSp: Float = TITLE_L) = row {
    STATS.forEachIndexed { i, s -> addView(stat(s, numSp), lp(WRAP_CONTENT).m(l = if (i == 0) 0 else px(gap))) }
}

/** 감정 흐름 바 — 전 항목 순서대로 */
internal fun Activity.flowRow(showLabel: Boolean = true) = row {
    gravity = Gravity.CENTER_VERTICAL
    if (showLabel) addView(text("감정 흐름", LABEL_S, SUB), lp(WRAP_CONTENT).m(r = px(20)))
    ITEMS.forEachIndexed { i, it ->
        addView(View(context).apply { background = round(it.color, pxf(5f)) },
            lp(0, px(10), 1f).m(l = if (i == 0) 0 else px(8)))
    }
    addView(text("↓ → ↑", LABEL_M, SUB, 500), lp(WRAP_CONTENT).m(l = px(20)))
}

internal fun Activity.tag(s: String) = text(s, LABEL_S, Color.parseColor("#B7C4D6"), 500, 1.1f).apply {
    background = round(CHIP, pxf(8f))
    setPadding(px(12), px(5), px(12), px(5))
}

/** 항목 행 — 색 점 · 정리 문장 · 유형 태그 / 메타 줄 */
internal fun Activity.itemRow(it: Item, showMeta: Boolean = true, bg: Int = SURFACE) = row {
    background = round(bg, pxf(14f))
    minimumHeight = px(if (showMeta) 96 else 84)
    if (!showMeta) gravity = Gravity.CENTER_VERTICAL
    setPadding(px(28), px(22), px(28), px(22))
    addView(View(context).apply { background = oval(it.color) },
        lp(px(16), px(16)).m(t = if (showMeta) px(9) else 0, r = px(22)))
    addView(col {
        addView(row {
            gravity = Gravity.CENTER_VERTICAL
            addView(text(it.text, BODY_M, FG, 500), lp(WRAP_CONTENT))
            addView(tag(it.tag), lp(WRAP_CONTENT).m(l = px(14)))
        })
        if (showMeta) addView(text(it.meta, LABEL_S, SUB), lp().m(t = px(10)))
    }, lp(0, WRAP_CONTENT, 1f))
}

internal fun Activity.button(s: String, primary: Boolean) = text(s, BODY_S, if (primary) ON_ACCENT else FG, 700, 1.1f).apply {
    gravity = Gravity.CENTER
    minHeight = px(TOUCH)
    setPadding(px(36), 0, px(36), 0)
    background = round(if (primary) ACCENT else CHIP, pxf(16f))
}

/** 폰에서 열기 — QR 글리프 + 라벨 */
internal fun Activity.qrButton() = row {
    gravity = Gravity.CENTER_VERTICAL
    minimumHeight = px(TOUCH)
    setPadding(px(24), 0, px(32), 0)
    background = round(CHIP, pxf(16f))
    addView(QrGlyph(context), lp(px(40), px(40)).m(r = px(16)))
    addView(text("폰에서 열기", BODY_S, FG, 700, 1.1f), lp(WRAP_CONTENT))
}

internal fun Activity.linkText(s: String) = text(s, LABEL_M, SUB, 500, 1.1f).apply {
    gravity = Gravity.CENTER
    minHeight = px(TOUCH)
    setPadding(px(20), 0, px(20), 0)
}

internal fun Activity.links() = row {
    addView(linkText("설정"), lp(WRAP_CONTENT))
    addView(linkText("이번 주 →").apply { setTextColor(FG) }, lp(WRAP_CONTENT))
}

// ── 그리기 뷰 ─────────────────────────────────────────────────

/** 장식 없는 QR 모양 글리프 (실제 QR 아님) */
internal class QrGlyph(c: Context) : View(c) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = FG }
    private val bits = "111011101100101011100100111001110100101010110011101101001110111".map { it == '1' }
    override fun onDraw(c: Canvas) {
        val n = 7
        val cell = width / n.toFloat()
        for (y in 0 until n) for (x in 0 until n) {
            val finder = (x < 2 && y < 2) || (x > 4 && y < 2) || (x < 2 && y > 4)
            if (finder || bits[(y * n + x) % bits.size]) {
                c.drawRect(x * cell + 1, y * cell + 1, (x + 1) * cell - 1, (y + 1) * cell - 1, p)
            }
        }
    }
}

/** 마이크 아이콘 */
internal class MicGlyph(c: Context, private val color: Int) : View(c) {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = this@MicGlyph.color }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = this@MicGlyph.color; style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND
    }
    override fun onDraw(c: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        stroke.strokeWidth = w * 0.09f
        c.drawRoundRect(RectF(w * 0.34f, h * 0.06f, w * 0.66f, h * 0.60f), w * 0.16f, w * 0.16f, fill)
        c.drawArc(RectF(w * 0.18f, h * 0.22f, w * 0.82f, h * 0.74f), 0f, 180f, false, stroke)
        c.drawLine(w * 0.5f, h * 0.74f, w * 0.5f, h * 0.92f, stroke)
        c.drawLine(w * 0.32f, h * 0.92f, w * 0.68f, h * 0.92f, stroke)
    }
}

/** 핀 하나 — 경로 위 위치(꼭짓점 인덱스), 색 1~2개, 말풍선 방향 */
internal class Pin(val vertex: Int, val colors: List<Int>, val quote: String, val meta: String, val above: Boolean)

internal val PINS = listOf(
    Pin(2, listOf(DOWN), "\"오늘 팀장 때문에 진짜 힘들었다\"", "18:57 · 올림픽대로 · 정체", above = false),
    Pin(4, listOf(NEUTRAL, UP), "\"내일 거래처 서류 다시 보내야 해, 아 노을 예쁘다\"",
        "19:08 · 한강대교 · 한마디 1 → 항목 2", above = true),
)

/**
 * 경로 + 핀 + 말풍선. 지도 없이 Canvas 로 그린다.
 * pts = 0..1 정규화 좌표. bubbleW = 말풍선 최대 폭(dp). 0 이면 말풍선 없음.
 */
internal class RouteView(
    c: Context,
    private val pts: List<Pair<Float, Float>>,
    private val bubbleW: Int,
    private val grid: Boolean = true,
) : View(c) {
    private val d = resources.displayMetrics.density
    private val gridP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = LINE; strokeWidth = 2 * d; style = Paint.Style.STROKE }
    private val routeP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#5B6B84"); strokeWidth = 7 * d; style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND; pathEffect = CornerPathEffect(120 * d)
    }
    private val dot = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bubbleBg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#162236") }
    private val bubbleLine = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#26344D"); style = Paint.Style.STROKE; strokeWidth = 1.5f * d
    }
    private val quoteP = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = FG; textSize = LABEL_M * d; typeface = weight(500) }
    private val metaP = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = SUB; textSize = LABEL_S * d }

    private fun layout(s: String, p: TextPaint, w: Int): StaticLayout =
        StaticLayout.Builder.obtain(s, 0, s.length, p, w)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(0f, 1.2f)
            .apply {
                if (Build.VERSION.SDK_INT >= 33) setLineBreakConfig(
                    LineBreakConfig.Builder().setLineBreakWordStyle(LineBreakConfig.LINE_BREAK_WORD_STYLE_PHRASE).build()
                )
            }.build()

    override fun onDraw(c: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        if (grid) {
            // 도로 느낌의 옅은 사선 격자 — 정보가 아니라 바탕
            var x = -h * 0.1f
            while (x < w) { c.drawLine(x, 0f, x + h * 0.08f, h, gridP); x += 260 * d }
            var y = h * 0.12f
            while (y < h) { c.drawLine(0f, y, w, y - w * 0.04f, gridP); y += 300 * d }
        }
        val xy = pts.map { it.first * w to it.second * h }
        val path = Path().apply {
            moveTo(xy[0].first, xy[0].second)
            xy.drop(1).forEach { lineTo(it.first, it.second) }
        }
        c.drawPath(path, routeP)
        // 출발(회색) · 도착(흰색)
        dot.color = Color.parseColor("#8090A8"); c.drawCircle(xy.first().first, xy.first().second, 10 * d, dot)
        dot.color = FG; c.drawCircle(xy.last().first, xy.last().second, 12 * d, dot)

        val pad = 20 * d
        for (pin in PINS) {
            val (px, py) = xy[pin.vertex]
            val r = 15 * d
            dot.color = BG; c.drawCircle(px, py, r + 6 * d, dot)
            val oval = RectF(px - r, py - r, px + r, py + r)
            if (pin.colors.size == 1) {
                dot.color = pin.colors[0]; c.drawCircle(px, py, r, dot)
            } else { // 대각 반반
                dot.color = pin.colors[0]; c.drawArc(oval, 135f, 180f, true, dot)
                dot.color = pin.colors[1]; c.drawArc(oval, -45f, 180f, true, dot)
            }
            if (bubbleW <= 0) continue
            val inner = (bubbleW * d - 2 * pad).toInt()
            val q = layout(pin.quote, quoteP, inner)
            val m = layout(pin.meta, metaP, inner)
            val textW = (0 until q.lineCount).maxOf { q.getLineWidth(it) }.coerceAtLeast(m.getLineWidth(0))
            val bw = textW + 2 * pad
            val bh = q.height + 8 * d + m.height + 2 * (pad * 0.8f)
            val bx = (px - bw * 0.3f).coerceIn(16 * d, w - 16 * d - bw)
            val by = if (pin.above) py - r - 18 * d - bh else py + r + 18 * d
            val box = RectF(bx, by, bx + bw, by + bh)
            c.drawRoundRect(box, 12 * d, 12 * d, bubbleBg)
            c.drawRoundRect(box, 12 * d, 12 * d, bubbleLine)
            c.save(); c.translate(bx + pad, by + pad * 0.8f); q.draw(c)
            c.translate(0f, q.height + 8 * d); m.draw(c); c.restore()
        }
    }
}
