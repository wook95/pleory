package ai.pleos.hello.ui

import ai.pleos.hello.core.CardFormat
import ai.pleos.hello.core.CardItem
import ai.pleos.hello.core.CardModel
import ai.pleos.hello.core.GeoPoint
import ai.pleos.hello.core.TrackMath
import ai.pleos.hello.data.Emotion
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
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/**
 * S1 카드(PRD §5, 시안 r1 A안 배치). 좌 경로+핀 약 40%, 우 패널 약 60%.
 * 기능 기준 구현이다 — 시각 다듬기는 T8b. [setModel] 로 내용을, [setSummary] 로 요약 자막(재생 중에만)을 바꾼다.
 */
class S1CardView(
    context: Context,
    private val onReplay: () -> Unit,
    private val onSkip: () -> Unit,
    private val onEdit: () -> Unit,
    private val onQr: () -> Unit,
) : LinearLayout(context) {

    private val route = RouteView(context)
    private val dateText = text(DATE_SP, SUB)
    private val titleText = text(HEADLINE_SP, FG, 700)
    private val metaText = text(BODY_S_SP, SUB)
    private val stats = row()
    private val flow = row().apply { gravity = Gravity.CENTER_VERTICAL }
    private val itemsBox = col()
    private val subtitle = text(BODY_S_SP, FG, 500)
    private val skip = button("건너뛰기", false) { onSkip() }
    private val summaryBar = row().apply {
        gravity = Gravity.CENTER_VERTICAL
        visibility = GONE
        background = round(SURFACE, dp(14f))
        setPadding(dp(28), dp(14), dp(14), dp(14))
        addView(subtitle, lp(0, WRAP_CONTENT, 1f))
        addView(skip, lp(WRAP_CONTENT, WRAP_CONTENT).m(l = dp(20)))
    }

    init {
        orientation = HORIZONTAL
        setBackgroundColor(BG)
        addView(FrameLayout(context).apply {
            setBackgroundColor(PANEL)
            addView(route, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        }, lp(dp(670), MATCH_PARENT))
        addView(col().apply {
            setPadding(dp(56), dp(EDGE), dp(EDGE), dp(EDGE))
            addView(dateText)
            addView(titleText, lp().m(t = dp(10)))
            addView(metaText, lp().m(t = dp(10)))
            addView(stats, lp().m(t = dp(40)))
            addView(flow, lp().m(t = dp(36)))
            addView(summaryBar, lp().m(t = dp(24)))
            addView(ScrollView(context).apply {
                isVerticalScrollBarEnabled = false
                addView(itemsBox)
            }, lp(MATCH_PARENT, 0, 1f).m(t = dp(24)))
            addView(row().apply {
                gravity = Gravity.CENTER_VERTICAL
                addView(button("15초 요약 듣기", true) { onReplay() }, lp(WRAP_CONTENT, WRAP_CONTENT))
                addView(button("수정", false) { onEdit() }, lp(WRAP_CONTENT, WRAP_CONTENT).m(l = dp(14)))
                addView(button("폰에서 열기", false) { onQr() }, lp(WRAP_CONTENT, WRAP_CONTENT).m(l = dp(14)))
            }, lp().m(t = dp(24)))
        }, lp(0, MATCH_PARENT, 1f))
    }

    fun setModel(m: CardModel) {
        dateText.text = m.dateLine
        titleText.text = m.title
        metaText.text = m.metaLine
        stats.removeAllViews()
        listOf(
            Triple(m.durationMin.toString(), "분", "주행 시간"),
            Triple(Math.round(m.distanceKm).toString(), "km", "주행 거리"),
            Triple(m.utterances.size.toString(), "", "한마디"),
            Triple(m.items.size.toString(), "", "항목"),
        ).forEachIndexed { i, (v, unit, label) -> stats.addView(stat(v, unit, label), lp(WRAP_CONTENT, WRAP_CONTENT).m(l = if (i == 0) 0 else dp(56))) }

        flow.removeAllViews()
        flow.addView(text(LABEL_S_SP, SUB).apply { text = "감정 흐름" }, lp(WRAP_CONTENT, WRAP_CONTENT).m(r = dp(20)))
        m.items.forEachIndexed { i, it ->
            // 색에 더해 ↓ → ↑ 기호를 막대 위에 같이 둔다(색각 접근성)
            flow.addView(col().apply {
                gravity = Gravity.CENTER_HORIZONTAL
                addView(text(LABEL_M_SP, emotionColor(it.emotion), 700).apply { text = CardFormat.emotionGlyph(it.emotion) })
                addView(View(context).apply { background = round(emotionColor(it.emotion), dp(5f)) }, lp(MATCH_PARENT, dp(10)).m(t = dp(4)))
            }, lp(0, WRAP_CONTENT, 1f).m(l = if (i == 0) 0 else dp(8)))
        }

        itemsBox.removeAllViews()
        m.items.forEachIndexed { i, it -> itemsBox.addView(itemRow(it), lp().m(t = if (i == 0) 0 else dp(14))) }
        route.setModel(m)
    }

    /** 요약 재생 중이면 자막과 "건너뛰기" 를 보이고, null 이면 숨긴다(PRD §4-2). */
    fun setSummary(text: String?) {
        summaryBar.visibility = if (text == null) GONE else VISIBLE
        subtitle.text = text.orEmpty()
    }

    private fun stat(value: String, unit: String, label: String) = col().apply {
        addView(row().apply {
            gravity = Gravity.BOTTOM
            addView(text(TITLE_L_SP, FG, 700).apply { text = value })
            if (unit.isNotEmpty()) addView(text(LABEL_M_SP, SUB, 500).apply { text = unit }, lp(WRAP_CONTENT, WRAP_CONTENT).m(l = dp(4), b = dp(3)))
        })
        addView(text(LABEL_S_SP, SUB).apply { text = label }, lp().m(t = dp(8)))
    }

    private fun itemRow(it: CardItem) = row().apply {
        background = round(SURFACE, dp(14f))
        minimumHeight = dp(96)
        setPadding(dp(28), dp(22), dp(28), dp(22))
        addView(View(context).apply { background = oval(emotionColor(it.emotion)) }, lp(dp(16), dp(16)).m(t = dp(9), r = dp(22)))
        addView(col().apply {
            addView(row().apply {
                gravity = Gravity.CENTER_VERTICAL
                addView(text(BODY_M_SP, FG, 500).apply { text = it.text }, lp(WRAP_CONTENT, WRAP_CONTENT))
                addView(text(LABEL_S_SP, TAG_FG, 500).apply {
                    text = CardFormat.typeTag(it.type)
                    background = round(CHIP, dp(8f))
                    setPadding(dp(12), dp(5), dp(12), dp(5))
                }, lp(WRAP_CONTENT, WRAP_CONTENT).m(l = dp(14)))
            })
            addView(text(LABEL_S_SP, SUB).apply { text = it.meta }, lp().m(t = dp(10)))
        }, lp(0, WRAP_CONTENT, 1f))
    }

    private fun button(label: String, primary: Boolean, onClick: () -> Unit) =
        text(BODY_S_SP, if (primary) ON_ACCENT else FG, 700).apply {
            text = label
            gravity = Gravity.CENTER
            minHeight = dp(TOUCH)
            setPadding(dp(36), 0, dp(36), 0)
            background = round(if (primary) ACCENT else CHIP, dp(16f))
            setOnClickListener { onClick() }
        }

    private fun text(sp: Float, color: Int, weight: Int = 400) = TextView(context).apply {
        textSize = sp
        setTextColor(color)
        typeface = Typeface.create(Typeface.DEFAULT, weight, false)
        includeFontPadding = false
        if (Build.VERSION.SDK_INT >= 33) lineBreakWordStyle = LineBreakConfig.LINE_BREAK_WORD_STYLE_PHRASE // 한글 단어 중간 줄바꿈 방지
    }

    private fun col() = LinearLayout(context).apply { orientation = VERTICAL }
    private fun row() = LinearLayout(context).apply { orientation = HORIZONTAL }
    private fun lp(w: Int = MATCH_PARENT, h: Int = WRAP_CONTENT, weight: Float = 0f) = LayoutParams(w, h, weight)
    private fun LayoutParams.m(l: Int = 0, t: Int = 0, r: Int = 0, b: Int = 0) = apply { setMargins(l, t, r, b) }
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun dp(v: Float) = v * resources.displayMetrics.density

    private fun round(color: Int, r: Float) = GradientDrawable().apply { cornerRadius = r; setColor(color) }
    private fun oval(color: Int) = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(color) }

    /**
     * 경로와 핀. 지도 없이 Canvas 로 그리는 도식이다 — 경로 박스를 채우도록 위경도를 각각 정규화하고
     * 출발이 왼쪽, 도착이 오른쪽에 오도록 좌우를 뒤집는다. 핀은 발화 좌표에서 가장 가까운 경로 점에 놓는다.
     */
    private class RouteView(c: Context) : View(c) {
        private val d = resources.displayMetrics.density
        private var model: CardModel? = null
        private val routeP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#5B6B84"); strokeWidth = 7 * d; style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND; pathEffect = CornerPathEffect(60 * d)
        }
        private val dot = Paint(Paint.ANTI_ALIAS_FLAG)
        private val bubbleBg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#162236") }
        private val bubbleLine = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#26344D"); style = Paint.Style.STROKE; strokeWidth = 1.5f * d
        }
        private val quoteP = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = FG; textSize = LABEL_M_SP * d; typeface = Typeface.create(Typeface.DEFAULT, 500, false) }
        private val metaP = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = SUB; textSize = LABEL_S_SP * d }

        fun setModel(m: CardModel) {
            model = m
            invalidate()
        }

        private fun layout(s: String, p: TextPaint, w: Int): StaticLayout =
            StaticLayout.Builder.obtain(s, 0, s.length, p, w)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(0f, 1.2f)
                .apply {
                    if (Build.VERSION.SDK_INT >= 33) setLineBreakConfig(
                        LineBreakConfig.Builder().setLineBreakWordStyle(LineBreakConfig.LINE_BREAK_WORD_STYLE_PHRASE).build(),
                    )
                }.build()

        override fun onDraw(c: Canvas) {
            val m = model ?: return
            if (m.route.size < 2) return
            val w = width.toFloat(); val h = height.toFloat()
            val minLat = m.route.minOf { it.lat }; val maxLat = m.route.maxOf { it.lat }
            val minLng = m.route.minOf { it.lng }; val maxLng = m.route.maxOf { it.lng }
            val flip = m.route.first().lng > m.route.last().lng
            fun xy(p: GeoPoint): Pair<Float, Float> {
                val nx = if (maxLng > minLng) ((p.lng - minLng) / (maxLng - minLng)).toFloat() else 0.5f
                val ny = if (maxLat > minLat) ((p.lat - minLat) / (maxLat - minLat)).toFloat() else 0.5f
                return (MARGIN + (if (flip) 1f - nx else nx) * (1f - 2 * MARGIN)) * w to (MARGIN + (1f - ny) * (1f - 2 * MARGIN)) * h
            }
            val step = (m.route.size / 150).coerceAtLeast(1)
            val shown = m.route.filterIndexed { i, _ -> i % step == 0 || i == m.route.lastIndex }.map(::xy)
            c.drawPath(Path().apply {
                moveTo(shown[0].first, shown[0].second)
                shown.drop(1).forEach { lineTo(it.first, it.second) }
            }, routeP)
            val start = xy(m.route.first()); val end = xy(m.route.last())
            dot.color = Color.parseColor("#8090A8"); c.drawCircle(start.first, start.second, 10 * d, dot)
            dot.color = FG; c.drawCircle(end.first, end.second, 12 * d, dot)

            val pad = 20 * d
            m.utterances.forEachIndexed { idx, u ->
                val at = u.point ?: return@forEachIndexed
                val (px, py) = xy(m.route.minBy { TrackMath.distanceMeters(it, at) })
                val r = 15 * d
                dot.color = BG; c.drawCircle(px, py, r + 6 * d, dot)
                val colors = CardFormat.pinEmotions(u.items).map(::emotionColor)
                val oval = RectF(px - r, py - r, px + r, py + r)
                if (colors.size >= 2) { // 대각 반반
                    dot.color = colors[0]; c.drawArc(oval, 135f, 180f, true, dot)
                    dot.color = colors[1]; c.drawArc(oval, -45f, 180f, true, dot)
                } else {
                    dot.color = colors.firstOrNull() ?: NEUTRAL; c.drawCircle(px, py, r, dot)
                }
                val inner = (BUBBLE_W * d - 2 * pad).toInt()
                val q = layout("\"${u.quote}\"", quoteP, inner)
                val meta = layout(CardFormat.pinMeta(u), metaP, inner)
                val textW = (0 until q.lineCount).maxOf { q.getLineWidth(it) }.coerceAtLeast((0 until meta.lineCount).maxOf { meta.getLineWidth(it) })
                val bw = textW + 2 * pad
                val bh = q.height + 8 * d + meta.height + 2 * (pad * 0.8f)
                val above = idx % 2 == 1
                val bx = (px - bw * 0.3f).coerceIn(16 * d, w - 16 * d - bw)
                val by = (if (above) py - r - 18 * d - bh else py + r + 18 * d).coerceIn(16 * d, h - 16 * d - bh)
                val box = RectF(bx, by, bx + bw, by + bh)
                c.drawRoundRect(box, 12 * d, 12 * d, bubbleBg)
                c.drawRoundRect(box, 12 * d, 12 * d, bubbleLine)
                c.save(); c.translate(bx + pad, by + pad * 0.8f); q.draw(c)
                c.translate(0f, q.height + 8 * d); meta.draw(c); c.restore()
            }
        }
    }

    companion object {
        private const val EDGE = 40
        private const val TOUCH = 80
        private const val MARGIN = 0.14f
        private const val BUBBLE_W = 420
        private const val HEADLINE_SP = 48f
        private const val TITLE_L_SP = 40f
        private const val BODY_M_SP = 28f
        private const val BODY_S_SP = 26f
        private const val LABEL_M_SP = 24f
        private const val DATE_SP = 24f
        private const val LABEL_S_SP = 20f
        private val BG = Color.parseColor("#0B1220")
        private val PANEL = Color.parseColor("#0F1828")
        private val SURFACE = Color.parseColor("#141F33")
        private val CHIP = Color.parseColor("#1D2A42")
        private val FG = Color.parseColor("#E8ECF2")
        private val SUB = Color.parseColor("#9AA8BC")
        private val TAG_FG = Color.parseColor("#B7C4D6")
        private val ACCENT = Color.parseColor("#00AAD2")
        private val ON_ACCENT = Color.parseColor("#04121A")
        private val DOWN = Color.parseColor("#E05A6A")
        private val NEUTRAL = Color.parseColor("#E0A24A")
        private val UP = Color.parseColor("#00AAD2")

        private fun emotionColor(e: Emotion) = when (e) {
            Emotion.DOWN -> DOWN
            Emotion.NEUTRAL -> NEUTRAL
            Emotion.UP -> UP
        }
    }
}
