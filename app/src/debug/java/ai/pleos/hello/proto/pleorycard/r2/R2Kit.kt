package ai.pleos.hello.proto.pleorycard.r2

import android.app.Activity
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.CornerPathEffect
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.graphics.text.LineBreakConfig
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

/*
 * R2 공용 — 세 시안이 같은 데이터·같은 글자 단계를 쓰고, 색(Tone)과 배치만 다르다.
 * 글자 단계는 Pleos Typography (size/line-height). 1dp = 1px(밀도 160).
 */

// ── 글자 단계 (Pleos) ───────────────────────────────────────
internal object T {
    val HEAD_S = 48f to 64
    val TITLE_L = 40f to 52
    val TITLE_M = 36f to 44
    val TITLE_S = 32f to 40
    val BODY_L = 30f to 38
    val BODY_M = 28f to 36
    val BODY_S = 26f to 34
    val LABEL_M = 24f to 28
    val LABEL_S = 20f to 24
}

/** 시안별 색. 이름은 역할 기준(hue 이름 안 씀). */
internal class Tone(
    val bg: Int,
    val plate: Int,      // 한 단계 위 면(경로 판·시트)
    val raised: Int,     // 보조 버튼 면
    val line: Int,       // 구분선(구조용)
    val fg: Int,
    val sub: Int,
    val faint: Int,      // 글자 아닌 눈금·안내선 전용
    val primary: Int,    // 주 행동 버튼 면
    val onPrimary: Int,
    val down: Int,
    val neu: Int,
    val up: Int,
    val listen: Int,     // S0 듣는 중
    val onListen: Int,
)

internal fun hex(s: String) = Color.parseColor(s)

// ── 데모 데이터 (PRD v4 §8) ──────────────────────────────────
internal enum class Mood(val glyph: String, val word: String) {
    DOWN("↓", "가라앉음"), NEU("→", "그냥"), UP("↑", "올라옴");

    fun color(t: Tone) = when (this) { DOWN -> t.down; NEU -> t.neu; UP -> t.up }
}

internal class RItem(val text: String, val type: String, val mood: Mood)
internal class RUtt(
    val time: String,
    val place: String,
    val jam: Boolean,
    val quote: String,
    val at: Float,                 // 주행 시간 중 위치 0..1
    val items: List<RItem>,
)

internal object D {
    const val DATE = "11월 8일 일요일"
    const val SEG = "퇴근길"
    const val MIN = 51
    const val KM = 23
    const val FROM = "회사"
    const val TO = "집"
    const val T0 = "18:40"
    const val T1 = "19:31"
    const val WEATHER = "비"
    const val JAM_ROAD = "올림픽대로"
    const val JAM_MIN = 23
    const val JAM_FROM = 0.20f
    const val JAM_TO = 0.45f
    const val SUMMARY = "힘들게 시작해서 노을로 끝났어. 할 일 하나 있어."
    const val EMPTY_SUMMARY = "오늘은 말이 없었네. 그래도 여기까지 왔어."
    val UTTS = listOf(
        RUtt("18:57", "올림픽대로", true, "오늘 팀장 때문에 진짜 힘들었다", 0.33f, listOf(
            RItem("오늘 팀장 때문에 진짜 힘들었다", "감정", Mood.DOWN),
        )),
        RUtt("19:08", "한강대교", false, "내일 거래처 서류 다시 보내야 해, 아 노을 예쁘다", 0.55f, listOf(
            RItem("내일 거래처 서류 다시 보내기", "할 일", Mood.NEU),
            RItem("노을이 예뻤다", "감정", Mood.UP),
        )),
    )
    val ITEMS = UTTS.flatMap { it.items }

    /** 회사(서) → 집(동). 강변을 따라 가는 모양의 도식 좌표 0..1 */
    val ROUTE = listOf(
        0.05f to 0.80f, 0.12f to 0.74f, 0.20f to 0.73f, 0.28f to 0.68f, 0.36f to 0.66f,
        0.44f to 0.60f, 0.50f to 0.58f, 0.57f to 0.52f, 0.63f to 0.44f, 0.70f to 0.40f,
        0.78f to 0.33f, 0.86f to 0.27f, 0.95f to 0.20f,
    )
}

// ── 조립 도구 ────────────────────────────────────────────────
internal fun Context.px(dp: Int) = (dp * resources.displayMetrics.density).toInt()
internal fun Context.pxf(dp: Float) = dp * resources.displayMetrics.density
internal fun face(w: Int): Typeface = Typeface.create(Typeface.DEFAULT, w, false)

internal fun Context.txt(
    s: CharSequence,
    style: Pair<Float, Int>,
    color: Int,
    w: Int = 400,
    tnum: Boolean = false,
) = TextView(this).apply {
    text = s
    textSize = style.first
    setTextColor(color)
    typeface = face(w)
    includeFontPadding = false
    lineHeight = px(style.second)
    if (tnum) fontFeatureSettings = "tnum"
    if (Build.VERSION.SDK_INT >= 33) lineBreakWordStyle = LineBreakConfig.LINE_BREAK_WORD_STYLE_PHRASE
}

internal fun lp(w: Int = MATCH_PARENT, h: Int = WRAP_CONTENT, weight: Float = 0f) = LinearLayout.LayoutParams(w, h, weight)
internal fun LinearLayout.LayoutParams.m(l: Int = 0, t: Int = 0, r: Int = 0, b: Int = 0) = apply { setMargins(l, t, r, b) }
internal fun Context.col(block: LinearLayout.() -> Unit = {}) = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; block() }
internal fun Context.row(block: LinearLayout.() -> Unit = {}) = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; block() }
internal fun Context.gap(h: Int) = View(this).apply { minimumHeight = px(h) }
internal fun Context.rule(color: Int) = View(this).apply { setBackgroundColor(color) }

internal fun shape(color: Int, r: Float, stroke: Int = 0, strokeColor: Int = 0) = GradientDrawable().apply {
    cornerRadius = r
    setColor(color)
    if (stroke > 0) setStroke(stroke, strokeColor)
}

/** 눌림 상태가 있는 면. 주행 중이 아닌 화면에서만 쓴다(S0 는 색만 바뀌고 움직이지 않는다). */
internal fun pressable(normal: GradientDrawable, pressed: GradientDrawable) = StateListDrawable().apply {
    addState(intArrayOf(android.R.attr.state_pressed), pressed)
    addState(intArrayOf(), normal)
}

/** 살짝 밝게/어둡게 — 눌림 색 계산용 */
internal fun mix(a: Int, b: Int, t: Float) = Color.rgb(
    (Color.red(a) + (Color.red(b) - Color.red(a)) * t).toInt(),
    (Color.green(a) + (Color.green(b) - Color.green(a)) * t).toInt(),
    (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * t).toInt(),
)

/**
 * 버튼. kind: 0 = 주 행동(채움), 1 = 보조(옅은 면), 2 = 글자만(면 없음, 눌릴 때만 면).
 * 높이 88dp(터치 80dp 이상), 모서리 20. 눌림 = 면 색 변화 + 0.96 축소(150ms).
 */
internal fun Context.btn(
    t: Tone,
    label: String,
    kind: Int,
    glyph: View? = null,
    height: Int = 88,
    padH: Int = 36,
    fg: Int? = null,
): LinearLayout = row {
    gravity = Gravity.CENTER
    minimumHeight = px(height)
    setPadding(px(if (glyph != null) padH - 4 else padH), 0, px(padH), 0) // 아이콘 쪽은 광학 보정으로 4dp 덜어낸다
    val r = pxf(20f)
    val (n, p) = when (kind) {
        0 -> t.primary to mix(t.primary, t.bg, 0.18f)
        1 -> t.raised to mix(t.raised, t.fg, 0.10f)
        else -> Color.TRANSPARENT to t.raised
    }
    background = pressable(shape(n, r), shape(p, r))
    val color = fg ?: if (kind == 0) t.onPrimary else t.fg
    glyph?.let { addView(it, lp(px(32), px(32)).m(r = px(14))) }
    addView(txt(label, T.BODY_S, color, 600), lp(WRAP_CONTENT))
    isClickable = true
    contentDescription = label
    setOnTouchListener { v, e ->
        when (e.actionMasked) {
            android.view.MotionEvent.ACTION_DOWN -> v.animate().scaleX(0.96f).scaleY(0.96f).setDuration(150).start()
            android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> v.animate().scaleX(1f).scaleY(1f).setDuration(150).start()
        }
        false
    }
}

/** 감정 표지: 글리프 + 낱말 (색만으로 뜻을 나르지 않는다) */
internal fun Context.moodLabel(t: Tone, m: Mood, style: Pair<Float, Int> = T.LABEL_M, word: Boolean = true) =
    txt(if (word) "${m.glyph} ${m.word}" else m.glyph, style, m.color(t), 600)

/** 세로(분할) 창 흉내: 왼쪽 840dp 에만 시안을 띄우고 나머지는 다른 앱 자리로 비운다. */
internal fun Activity.narrowFrame(content: View): View = FrameLayout(this).apply {
    setBackgroundColor(hex("#050506"))
    addView(content, FrameLayout.LayoutParams(px(840), MATCH_PARENT))
}

// ── 그림 ─────────────────────────────────────────────────────

internal class MicGlyph(c: Context, var color: Int) : View(c) {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    override fun onDraw(c: Canvas) {
        fill.color = color; stroke.color = color
        val w = width.toFloat(); val h = height.toFloat()
        stroke.strokeWidth = w * 0.085f
        c.drawRoundRect(RectF(w * 0.35f, h * 0.06f, w * 0.65f, h * 0.58f), w * 0.15f, w * 0.15f, fill)
        c.drawArc(RectF(w * 0.20f, h * 0.24f, w * 0.80f, h * 0.74f), 0f, 180f, false, stroke)
        c.drawLine(w * 0.5f, h * 0.74f, w * 0.5f, h * 0.92f, stroke)
    }
}

/** 재생 삼각형. 무게중심이 왼쪽으로 쏠려 보이므로 오른쪽으로 6% 민다(광학 정렬). */
internal class PlayGlyph(c: Context, private val ink: Int) : View(c) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = ink; pathEffect = CornerPathEffect(3f) }
    override fun onDraw(c: Canvas) {
        val w = width.toFloat(); val h = height.toFloat(); val dx = w * 0.06f
        c.drawPath(Path().apply {
            moveTo(w * 0.24f + dx, h * 0.14f); lineTo(w * 0.84f + dx, h * 0.5f); lineTo(w * 0.24f + dx, h * 0.86f); close()
        }, p)
    }
}

/** 뒤로(←). Pleos Page Navigation Depth 형 */
internal class BackGlyph(c: Context, private val ink: Int) : View(c) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = ink; style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
    }
    override fun onDraw(c: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        p.strokeWidth = w * 0.09f
        c.drawLine(w * 0.18f, h * 0.5f, w * 0.84f, h * 0.5f, p)
        c.drawPath(Path().apply { moveTo(w * 0.46f, h * 0.2f); lineTo(w * 0.18f, h * 0.5f); lineTo(w * 0.46f, h * 0.8f) }, p)
    }
}

/** 더보기(⋯) — 항목 고치기·지우기 진입 */
internal class MoreGlyph(c: Context, private val ink: Int) : View(c) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = ink }
    override fun onDraw(c: Canvas) {
        val w = width.toFloat(); val h = height.toFloat(); val r = w * 0.08f
        for (i in 0..2) c.drawCircle(w * (0.22f + 0.28f * i), h * 0.5f, r, p)
    }
}

internal class PhoneGlyph(c: Context, private val ink: Int) : View(c) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = ink; style = Paint.Style.STROKE }
    override fun onDraw(c: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        p.strokeWidth = w * 0.08f
        c.drawRoundRect(RectF(w * 0.26f, h * 0.08f, w * 0.74f, h * 0.92f), w * 0.1f, w * 0.1f, p)
        c.drawLine(w * 0.44f, h * 0.78f, w * 0.56f, h * 0.78f, p)
    }
}

/**
 * 경로 도식 + 핀. 지도가 아니라 도식이다.
 * - 정체 구간은 점선 + 굵기로(색만으로 구분하지 않는다), 옆에 "정체" 글자.
 * - 핀은 감정 색(2개 항목이면 반반) + 바깥 바탕색 링 + 번호(1, 2). 번호가 목록의 같은 번호와 짝이다.
 * - 출발·도착은 시각과 장소를 글자로 붙인다.
 */
internal class RouteCanvas(
    c: Context,
    private val t: Tone,
    private val ground: Int,
    private val inset: Float = 0.12f,
    private val labelSize: Float = 22f,
    private val empty: Boolean = false, // 발화 0개 세션: 핀·정체 없이 길만
) : View(c) {
    private val d = resources.displayMetrics.density
    private val road = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
        pathEffect = CornerPathEffect(80 * d)
    }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = labelSize * d; typeface = face(500); fontFeatureSettings = "tnum" }
    private val num = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 22 * d; typeface = face(700); textAlign = Paint.Align.CENTER }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        val ix = w * inset; val iy = h * inset
        // 경로 외곽을 판 안쪽(inset)까지 채운다. 가로세로는 따로 늘린다 — 지도가 아니라 도식이다.
        val x0 = D.ROUTE.minOf { it.first }; val x1 = D.ROUTE.maxOf { it.first }
        val y0 = D.ROUTE.minOf { it.second }; val y1 = D.ROUTE.maxOf { it.second }
        val pts = D.ROUTE.map { (x, y) -> ix + (x - x0) / (x1 - x0) * (w - 2 * ix) to iy + (y - y0) / (y1 - y0) * (h - 2 * iy) }
        val path = Path().apply { moveTo(pts[0].first, pts[0].second); pts.drop(1).forEach { lineTo(it.first, it.second) } }
        val pm = PathMeasure(path, false); val len = pm.length
        fun at(f: Float): Pair<Float, Float> { val a = FloatArray(2); pm.getPosTan(len * f, a, null); return a[0] to a[1] }
        /** 진행 방향의 왼쪽 위 법선(라벨이 선을 가리지 않게 선 밖으로 민다) */
        fun normal(f: Float): Pair<Float, Float> {
            val p = FloatArray(2); val tan = FloatArray(2); pm.getPosTan(len * f, p, tan)
            var nx = tan[1]; var ny = -tan[0]
            if (ny > 0) { nx = -nx; ny = -ny }
            return nx to ny
        }

        road.color = mix(t.faint, t.sub, 0.5f); road.strokeWidth = 6 * d; road.pathEffect = CornerPathEffect(80 * d) // 경로는 의미 있는 그림 → 3:1 이상
        c.drawPath(path, road)
        // 정체 구간: 굵은 점선(모양으로 구분) + 선 반대쪽에 "정체 N분"
        if (!empty) {
        val jam = Path(); pm.getSegment(len * D.JAM_FROM, len * D.JAM_TO, jam, true)
        road.color = t.sub; road.strokeWidth = 12 * d
        road.pathEffect = DashPathEffect(floatArrayOf(4 * d, 16 * d), 0f)
        c.drawPath(jam, road)
        val jf = (D.JAM_FROM + D.JAM_TO) / 2
        val jm = at(jf); val jn = normal(jf)
        label.color = t.sub; label.textAlign = if (-jn.first >= 0) Paint.Align.LEFT else Paint.Align.RIGHT
        c.drawText("정체 ${D.JAM_MIN}분", jm.first - jn.first * 36 * d, jm.second - jn.second * 36 * d + 10 * d, label)
        }

        // 출발·도착
        val s = pts.first(); val e = pts.last()
        fill.color = ground; c.drawCircle(s.first, s.second, 14 * d, fill)
        fill.color = t.sub; c.drawCircle(s.first, s.second, 9 * d, fill)
        fill.color = ground; c.drawCircle(e.first, e.second, 16 * d, fill)
        fill.color = t.fg; c.drawCircle(e.first, e.second, 11 * d, fill)
        // 끝점 라벨은 점의 옆(안쪽)에 둔다 — 위아래로 두면 낮은 판에서 잘린다
        label.textAlign = Paint.Align.LEFT; label.color = t.sub
        c.drawText(if (empty) "19:02 ${D.FROM}" else "${D.T0} ${D.FROM}", s.first + 28 * d, s.second + 8 * d, label)
        label.textAlign = Paint.Align.RIGHT; label.color = t.fg
        c.drawText(if (empty) "19:34 ${D.TO}" else "${D.T1} ${D.TO}", e.first - 28 * d, e.second + 8 * d, label)

        // 핀
        if (!empty) D.UTTS.forEachIndexed { i, u ->
            val (x, y) = at(u.at)
            val r = 22 * d
            fill.color = ground; c.drawCircle(x, y, r + 5 * d, fill)
            val moods = if (u.items.size == 1) listOf(u.items[0].mood) else listOf(u.items.first().mood, u.items.last().mood)
            val o = RectF(x - r, y - r, x + r, y + r)
            if (moods.size == 1) { fill.color = moods[0].color(t); c.drawCircle(x, y, r, fill) } else {
                fill.color = moods[0].color(t); c.drawArc(o, 135f, 180f, true, fill)
                fill.color = moods[1].color(t); c.drawArc(o, -45f, 180f, true, fill)
            }
            num.color = t.bg
            c.drawText("${i + 1}", x, y + 8 * d, num)
            val n = normal(u.at)
            label.textAlign = if (n.first < 0) Paint.Align.RIGHT else Paint.Align.LEFT; label.color = t.fg
            c.drawText(u.time, x + n.first * (r + 22 * d), y + n.second * (r + 22 * d) + 8 * d, label)
        }
    }
}
