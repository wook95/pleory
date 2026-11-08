package ai.pleos.hello.ui

import ai.pleos.hello.core.CardFormat
import ai.pleos.hello.core.CardItem
import ai.pleos.hello.core.CardModel
import ai.pleos.hello.core.CardUtterance
import ai.pleos.hello.core.GeoPoint
import ai.pleos.hello.core.LayoutMode
import ai.pleos.hello.core.TrackMath
import ai.pleos.hello.data.ItemType
import android.content.Context
import android.graphics.Canvas
import android.graphics.CornerPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/**
 * S1 카드(PRD §5, 시안 r2 A "일지"). 상자·칩·통계 타일 없이 글자 크기·굵기·여백·가는 선으로 위계를 만든다.
 * 창이 넓으면([LayoutMode.WIDE]) 왼쪽 글 58% + 오른쪽 경로 판 42%, 좁으면 한 열(날짜·제목·문장 → 감정 흐름 → 요약 → 항목 → 경로)이고
 * 아래 버튼 줄과, 재생 중일 때 그 위의 요약 자막 줄은 스크롤 밖에 고정이다(건너뛰기가 화면 밖으로 밀리지 않게). [setModel] 로 내용을, [setSummary] 로 요약 자막(재생 중에만)을 바꾼다. 배치를 바꿔도 내용·자막은 그대로다.
 */
class S1CardView(
    context: Context,
    private val onReplay: () -> Unit,
    private val onSkip: () -> Unit,
    private val onQr: () -> Unit,
    private val onSettings: () -> Unit = {},
) : LinearLayout(context) {

    private val page = context.col()
    private val route = RouteView(context)
    private val routePlate = FrameLayout(context).apply {
        background = shape(Tok.PLATE, context.dpf(Tok.PLATE_R.toFloat()))
        addView(route, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
    }
    private val subtitle = context.txt(Tok.BODY_L, Tok.FG, 500)
    private val summaryBlock = context.row().apply {
        visibility = GONE
        addView(context.rule().apply { setBackgroundColor(Tok.FG) }, lp(context.dp(3), context.dp(72)).m(r = context.dp(24)))
        addView(subtitle, lp(0, WRAP_CONTENT, 1f))
        addView(context.button("건너뛰기", BtnKind.SECONDARY, padH = 28) { onSkip() }, lp(WRAP_CONTENT).m(l = context.dp(24)))
    }
    private val primary = context.button("15초 요약 듣기", BtnKind.PRIMARY, PlayGlyph(context, Tok.ON_PRIMARY)) { onReplay() }
    private val primaryLabel = primary.findViewWithTag<TextView>(BUTTON_LABEL)
    private val actions = context.row().apply {
        addView(primary, lp(WRAP_CONTENT))
        addView(context.button("폰에서 열기", BtnKind.SECONDARY, PhoneGlyph(context, Tok.FG)) { onQr() }, lp(WRAP_CONTENT).m(l = context.dp(16)))
        addView(View(context), lp(0, 1, 1f))
        addView(context.button("설정", BtnKind.TEXT, padH = 28) { onSettings() }, lp(WRAP_CONTENT))
    }
    private var mode: LayoutMode? = null
    private var model: CardModel? = null

    init {
        setBackgroundColor(Tok.BG)
        applyMode(LayoutMode.WIDE)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val next = LayoutMode.from((w / resources.displayMetrics.density).toInt())
        // 레이아웃 패스 안에서 구조를 바꾸지 않는다. 실행 시점의 폭으로 다시 판단해 빠른 연속 변경에도 마지막 폭을 따른다
        if (next != mode) post {
            val now = LayoutMode.from((width / resources.displayMetrics.density).toInt())
            if (now != mode) applyMode(now)
        }
    }

    /** 폭에 따라 뼈대만 다시 짠다. 글(page)·경로 판·버튼 줄은 같은 뷰를 옮겨 붙이므로 내용과 요약 상태가 남는다. */
    private fun applyMode(next: LayoutMode) {
        mode = next
        removeAllViews()
        page.detach(); routePlate.detach(); actions.detach(); summaryBlock.detach()
        val c = context
        val scroll = ScrollView(c).apply { isVerticalScrollBarEnabled = false }
        if (next == LayoutMode.WIDE) {
            orientation = HORIZONTAL
            scroll.addView(page)
            addView(c.col().apply {
                setPadding(c.dp(64), c.dp(56), c.dp(48), c.dp(40))
                addView(scroll, lp(MATCH_PARENT, 0, 1f))
                addView(summaryBlock, lp().m(t = c.dp(24)))
                addView(actions, lp().m(t = c.dp(24)))
            }, lp(0, MATCH_PARENT, 58f))
            addView(routePlate, lp(0, MATCH_PARENT, 42f).m(t = c.dp(24), r = c.dp(24), b = c.dp(24)))
        } else {
            orientation = VERTICAL
            scroll.addView(c.col().apply {
                setPadding(c.dp(40), c.dp(44), c.dp(40), c.dp(32))
                addView(page)
                addView(routePlate, lp(MATCH_PARENT, c.dp(380)).m(t = c.dp(36)))
            })
            addView(scroll, lp(MATCH_PARENT, 0, 1f))
            addView(c.rule(), lp(MATCH_PARENT, c.dp(1)))
            addView(summaryBlock, lp().m(l = c.dp(40), t = c.dp(20), r = c.dp(28)))
            addView(actions, lp().m(l = c.dp(40), t = c.dp(20), r = c.dp(28), b = c.dp(24)))
        }
        route.narrow = next == LayoutMode.NARROW
        requestLayout()
    }

    fun setModel(m: CardModel) {
        model = m
        val c = context
        page.removeAllViews()
        page.addView(c.txt(Tok.LABEL_M, Tok.SUB, tnum = true, s = m.date))
        page.addView(c.txt(Tok.HEAD_S, Tok.FG, 700, s = m.title), lp().m(t = c.dp(12)))
        page.addView(c.txt(Tok.BODY_L, Tok.SUB, tnum = true, s = m.sentence), lp().m(t = c.dp(16)))
        if (m.items.isNotEmpty()) page.addView(c.txt(Tok.BODY_M, Tok.FG, 600, s = moodLine(m.items)), lp().m(t = c.dp(32)))
        if (m.items.isEmpty()) {
            page.addView(c.rule(), lp(MATCH_PARENT, c.dp(1)).m(t = c.dp(40)))
            page.addView(c.txt(Tok.BODY_M, Tok.FG, 600, s = "남긴 한마디가 없어요"), lp().m(t = c.dp(32)))
            page.addView(c.txt(Tok.BODY_S, Tok.SUB, s = "다음에는 운전 중에 말하기 버튼을 누르고 떠오르는 대로 말해 보세요."), lp().m(t = c.dp(8)))
        } else {
            page.addView(c.txt(Tok.LABEL_M, Tok.SUB, s = "한마디 ${m.utterances.size}개에서 항목 ${m.items.size}개"), lp().m(t = c.dp(44)))
            page.addView(c.rule(), lp(MATCH_PARENT, c.dp(1)).m(t = c.dp(14)))
            m.utterances.forEach { u ->
                page.addView(entry(u))
                page.addView(c.rule(), lp(MATCH_PARENT, c.dp(1)))
            }
        }
        route.setModel(m)
    }

    /** 요약 재생 중이면 자막과 "건너뛰기" 를 보이고, null 이면 숨긴다(PRD §4-2). 재생 중에는 주 버튼이 "다시 듣기" 다. */
    fun setSummary(text: String?) {
        summaryBlock.visibility = if (text == null) GONE else VISIBLE
        subtitle.text = text.orEmpty()
        primaryLabel.text = if (text == null) "15초 요약 듣기" else "다시 듣기"
        primary.contentDescription = primaryLabel.text
    }

    /** ↓ 가라앉음 — → 그냥 — ↑ 올라옴 : 글리프·낱말·색 세 겹. 항목이 많으면 줄이 넘어간다. */
    private fun moodLine(items: List<CardItem>) = SpannableStringBuilder().apply {
        items.forEachIndexed { i, it ->
            if (i > 0) {
                val s = length; append("  —  ")
                setSpan(ForegroundColorSpan(Tok.FAINT), s, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            val s = length; append("${CardFormat.emotionGlyph(it.emotion)} ${Tok.emotionWord(it.emotion)}")
            setSpan(ForegroundColorSpan(Tok.emotionColor(it.emotion)), s, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }

    /** 한마디 하나 = 왼쪽 여백에 시각·장소, 오른쪽에 정리된 항목들. 항목이 여럿이면 말한 그대로를 먼저 보인다. */
    private fun entry(u: CardUtterance) = context.row().apply {
        val c = context
        gravity = android.view.Gravity.TOP
        setPadding(0, c.dp(24), 0, c.dp(20))
        addView(c.col().apply {
            addView(c.txt(Tok.BODY_S, Tok.FG, 600, tnum = true, s = u.time))
            u.place?.let { addView(c.txt(Tok.LABEL_S, Tok.SUB, s = it), lp().m(t = c.dp(6))) }
            if (u.congested) addView(c.txt(Tok.LABEL_S, Tok.SUB, s = "정체 중"), lp().m(t = c.dp(2)))
        }, lp(c.dp(132)))
        addView(c.col().apply {
            if (u.items.size > 1) addView(c.txt(Tok.LABEL_M, Tok.SUB, s = "“${u.quote}”"), lp().m(b = c.dp(10)))
            u.items.forEach { addView(itemRow(it)) }
        }, lp(0, WRAP_CONTENT, 1f))
    }

    private fun itemRow(it: CardItem) = context.col().apply {
        val c = context
        minimumHeight = c.dp(80)
        gravity = android.view.Gravity.CENTER_VERTICAL
        addView(c.txt(Tok.BODY_M, Tok.FG, 500, s = it.text))
        // 원문(정리 전·실패)은 감정이 정해지지 않았으므로 유형만 쓴다
        val meta = SpannableStringBuilder(CardFormat.typeTag(it.type))
        if (it.type != ItemType.RAW) {
            meta.append("  ·  ")
            val s = meta.length
            meta.append("${CardFormat.emotionGlyph(it.emotion)} ${Tok.emotionWord(it.emotion)}")
            meta.setSpan(ForegroundColorSpan(Tok.emotionColor(it.emotion)), s, meta.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        addView(c.txt(Tok.LABEL_M, Tok.SUB, s = meta), lp().m(t = c.dp(6), b = c.dp(8)))
    }

    /**
     * 경로와 핀. 지도가 아니라 도식이다 — 위경도를 판 안쪽까지 각각 늘리고, 출발이 왼쪽에 오도록 좌우를 뒤집는다.
     * 핀은 발화 좌표에서 가장 가까운 경로 점에 놓고, 감정 색(2개 이상이면 대각 반반) + 시각(진행 방향의 법선 쪽)을 붙인다.
     * 시각이 목록 왼쪽 여백의 시각과 짝이다.
     */
    private class RouteView(c: Context) : View(c) {
        private val d = resources.displayMetrics.density
        private var model: CardModel? = null
        var narrow = false
            set(v) { field = v; invalidate() }
        private val road = Paint(Paint.ANTI_ALIAS_FLAG).also {
            it.color = Tok.ROUTE; it.strokeWidth = 6 * d; it.style = Paint.Style.STROKE
            it.strokeCap = Paint.Cap.ROUND; it.strokeJoin = Paint.Join.ROUND; it.pathEffect = CornerPathEffect(60 * d)
        }
        private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
        private val label = Paint(Paint.ANTI_ALIAS_FLAG).also { it.textSize = 22 * d; it.typeface = face(500); it.fontFeatureSettings = "tnum" }

        fun setModel(m: CardModel) {
            model = m
            invalidate()
        }

        override fun onDraw(c: Canvas) {
            val m = model ?: return
            if (m.route.size < 2) return
            val w = width.toFloat(); val h = height.toFloat()
            val inset = if (narrow) 0.10f else 0.14f
            val minLat = m.route.minOf { it.lat }; val maxLat = m.route.maxOf { it.lat }
            val minLng = m.route.minOf { it.lng }; val maxLng = m.route.maxOf { it.lng }
            val flip = m.route.first().lng > m.route.last().lng
            fun xy(p: GeoPoint): Pair<Float, Float> {
                val nx = if (maxLng > minLng) ((p.lng - minLng) / (maxLng - minLng)).toFloat() else 0.5f
                val ny = if (maxLat > minLat) ((p.lat - minLat) / (maxLat - minLat)).toFloat() else 0.5f
                return (inset + (if (flip) 1f - nx else nx) * (1f - 2 * inset)) * w to (inset + (1f - ny) * (1f - 2 * inset)) * h
            }
            val step = (m.route.size / 150).coerceAtLeast(1)
            val idx = m.route.indices.filter { it % step == 0 || it == m.route.lastIndex }
            val pts = idx.map { xy(m.route[it]) }
            c.drawPath(Path().apply { moveTo(pts[0].first, pts[0].second); pts.drop(1).forEach { lineTo(it.first, it.second) } }, road)

            // 출발·도착 — 라벨은 점의 옆(판 안쪽)에 둬서 낮은 판에서도 잘리지 않는다
            val s = pts.first(); val e = pts.last()
            fill.color = Tok.PLATE; c.drawCircle(s.first, s.second, 14 * d, fill); c.drawCircle(e.first, e.second, 16 * d, fill)
            fill.color = Tok.SUB; c.drawCircle(s.first, s.second, 9 * d, fill)
            fill.color = Tok.FG; c.drawCircle(e.first, e.second, 11 * d, fill)
            // 점이 판의 왼쪽 반이면 라벨을 오른쪽으로, 오른쪽 반이면 왼쪽으로 낸다(가장자리에서 잘리지 않게)
            fun side(x: Float, y: Float, text: String, color: Int) {
                val left = x < w / 2
                label.textAlign = if (left) Paint.Align.LEFT else Paint.Align.RIGHT; label.color = color
                c.drawText(text, if (left) x + 28 * d else x - 28 * d, y + 8 * d, label)
            }
            side(s.first, s.second, m.startLabel, Tok.SUB)
            // 왕복처럼 출발·도착이 겹치면 도착 라벨은 빼고 점만 둔다
            if (Math.hypot((e.first - s.first).toDouble(), (e.second - s.second).toDouble()) > 120 * d) side(e.first, e.second, m.endLabel, Tok.FG)

            m.utterances.forEach { u ->
                val at = u.point ?: return@forEach
                val near = idx.indices.minBy { TrackMath.distanceMeters(m.route[idx[it]], at) }
                val (x, y) = pts[near]
                val r = 16 * d
                fill.color = Tok.PLATE; c.drawCircle(x, y, r + 5 * d, fill)
                val moods = CardFormat.pinEmotions(u.items).map(Tok::emotionColor)
                val o = RectF(x - r, y - r, x + r, y + r)
                if (moods.size >= 2) {
                    fill.color = moods[0]; c.drawArc(o, 135f, 180f, true, fill)
                    fill.color = moods[1]; c.drawArc(o, -45f, 180f, true, fill)
                } else {
                    fill.color = moods.firstOrNull() ?: Tok.NEUTRAL; c.drawCircle(x, y, r, fill)
                }
                // 진행 방향의 위쪽 법선으로 시각을 민다(선을 가리지 않게)
                val a = pts[(near - 1).coerceAtLeast(0)]; val b = pts[(near + 1).coerceAtMost(pts.lastIndex)]
                val len = Math.hypot((b.first - a.first).toDouble(), (b.second - a.second).toDouble()).toFloat().coerceAtLeast(1f)
                var nx = (b.second - a.second) / len; var ny = -(b.first - a.first) / len
                if (ny > 0) { nx = -nx; ny = -ny }
                if (nx == 0f && ny == 0f) ny = -1f // 경로 점이 한 곳에 모이면 위로
                label.textAlign = if (nx < 0) Paint.Align.RIGHT else Paint.Align.LEFT; label.color = Tok.FG
                c.drawText(u.time, x + nx * (r + 22 * d), y + ny * (r + 22 * d) + 8 * d, label)
            }
        }
    }
}
