package ai.pleos.hello.ui

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
import android.graphics.drawable.StateListDrawable
import android.graphics.text.LineBreakConfig
import android.os.Build
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.LinearLayout
import android.widget.TextView

/**
 * 화면 공용 토큰과 조립 도구 — 시안 r2 A "일지"(prototypes/2026-11-08-pleory-card/rounds/r2).
 * 색은 역할 이름으로만 쓴다. 강조색이 따로 없고, 색은 감정 셋에만 남는다(주 버튼은 밝은 무채색).
 * 글자 단계는 Pleos Typography(size to line-height), 밀도 160 이라 1dp = 1px.
 */
internal object Tok {
    // 색 — 대비는 바탕 #161412 기준
    val BG = Color.parseColor("#161412")
    val PLATE = Color.parseColor("#1D1A17")      // 경로 판
    val RAISED = Color.parseColor("#26221E")     // 보조 버튼·눌림 면
    val LINE = Color.parseColor("#2E2A25")       // 구분선(구조용)
    val FG = Color.parseColor("#EFE9DF")         // 15.2:1
    val SUB = Color.parseColor("#ADA597")        // 7.5:1
    val FAINT = Color.parseColor("#4A443D")      // 글자 아닌 연결선 전용
    val ROUTE = Color.parseColor("#7B746A")      // 경로선 — 의미 있는 그림이라 3:1 이상
    val RING = Color.parseColor("#6E665B")       // S0 기본 버튼 테두리 3.2:1
    val PRIMARY = FG                             // 주 행동 면
    val ON_PRIMARY = BG
    val DOWN = Color.parseColor("#E8817A")       // 6.9:1
    val NEUTRAL = Color.parseColor("#D9A957")    // 8.5:1
    val UP = Color.parseColor("#56BDD6")         // 8.4:1

    // 글자 (sp, 줄 높이 dp)
    val HEAD_M = 56f to 72      // S0 말하기(주행 중 유일한 글자 조작)
    val HEAD_S = 48f to 64
    val TITLE_L = 40f to 52
    val BODY_L = 30f to 38
    val BODY_M = 28f to 36
    val BODY_S = 26f to 34
    val LABEL_M = 24f to 28
    val LABEL_S = 20f to 24

    // 치수 (dp)
    const val EDGE = 48
    const val BTN_H = 88          // 터치 80dp 이상
    const val BTN_R = 20
    const val PLATE_R = 28
    const val PRESS_MS = 150L
    const val ICON_BTN = 72       // 항목 ⋯

    fun emotionColor(e: Emotion) = when (e) {
        Emotion.DOWN -> DOWN
        Emotion.NEUTRAL -> NEUTRAL
        Emotion.UP -> UP
    }

    /** 색만으로 감정을 나르지 않는다 — 글리프 옆에 붙는 낱말 */
    fun emotionWord(e: Emotion) = when (e) {
        Emotion.DOWN -> "가라앉음"
        Emotion.NEUTRAL -> "그냥"
        Emotion.UP -> "올라옴"
    }
}

internal fun Context.dp(v: Int) = (v * resources.displayMetrics.density).toInt()
internal fun Context.dpf(v: Float) = v * resources.displayMetrics.density

internal fun face(weight: Int): Typeface = Typeface.create(Typeface.DEFAULT, weight, false)

/** 시스템 글꼴 TextView. tnum = 시각·거리처럼 자리가 맞아야 하는 숫자. */
internal fun Context.txt(style: Pair<Float, Int>, color: Int, weight: Int = 400, tnum: Boolean = false, s: CharSequence = "") =
    TextView(this).apply {
        text = s
        textSize = style.first
        setTextColor(color)
        typeface = face(weight)
        includeFontPadding = false
        lineHeight = dp(style.second)
        if (tnum) fontFeatureSettings = "tnum"
        if (Build.VERSION.SDK_INT >= 33) lineBreakWordStyle = LineBreakConfig.LINE_BREAK_WORD_STYLE_PHRASE // 한글 단어 중간 줄바꿈 방지
    }

internal fun Context.col() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
internal fun Context.row() = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
internal fun Context.rule() = View(this).apply { setBackgroundColor(Tok.LINE) }
internal fun lp(w: Int = ViewGroup.LayoutParams.MATCH_PARENT, h: Int = WRAP_CONTENT, weight: Float = 0f) = LinearLayout.LayoutParams(w, h, weight)
internal fun LinearLayout.LayoutParams.m(l: Int = 0, t: Int = 0, r: Int = 0, b: Int = 0) = apply { setMargins(l, t, r, b) }

/** 부모에서 떼어 낸다(배치 전환 때 같은 뷰를 다른 줄에 다시 붙이기 위해). */
internal fun View.detach(): View = apply { (parent as? ViewGroup)?.removeView(this) }

internal fun shape(color: Int, r: Float, stroke: Int = 0, strokeColor: Int = 0) = GradientDrawable().apply {
    cornerRadius = r
    setColor(color)
    if (stroke > 0) setStroke(stroke, strokeColor)
}

internal fun pressable(normal: GradientDrawable, pressed: GradientDrawable) = StateListDrawable().apply {
    addState(intArrayOf(android.R.attr.state_pressed), pressed)
    addState(intArrayOf(), normal)
}

internal fun mix(a: Int, b: Int, t: Float) = Color.rgb(
    (Color.red(a) + (Color.red(b) - Color.red(a)) * t).toInt(),
    (Color.green(a) + (Color.green(b) - Color.green(a)) * t).toInt(),
    (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * t).toInt(),
)

/** 눌리면 0.96 으로 줄었다 돌아온다(150ms). 주차 중 화면에서만 쓴다 — S0 는 움직이지 않는다. false 를 돌려 클릭·접근성 클릭은 그대로 간다. */
@android.annotation.SuppressLint("ClickableViewAccessibility")
internal fun View.pressScale() = setOnTouchListener { v, e ->
    when (e.actionMasked) {
        MotionEvent.ACTION_DOWN -> v.animate().scaleX(0.96f).scaleY(0.96f).setDuration(Tok.PRESS_MS).start()
        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> v.animate().scaleX(1f).scaleY(1f).setDuration(Tok.PRESS_MS).start()
    }
    false
}

internal enum class BtnKind { PRIMARY, SECONDARY, TEXT, DANGER }

/** 버튼 높이 88dp, 모서리 20. 아이콘이 있으면 그쪽 여백을 4dp 덜어 광학 정렬한다. */
internal const val BUTTON_LABEL = "label"

/** [scale] = false 면 눌림 축소를 끈다(S0 처럼 움직이면 안 되는 화면). 글자 뷰는 [BUTTON_LABEL] 태그로 찾는다. */
internal fun Context.button(label: String, kind: BtnKind, glyph: View? = null, padH: Int = 36, fg: Int? = null, scale: Boolean = true, onClick: () -> Unit) =
    LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER
        minimumHeight = dp(Tok.BTN_H)
        setPadding(dp(if (glyph != null) padH - 4 else padH), 0, dp(padH), 0)
        val r = dpf(Tok.BTN_R.toFloat())
        val (n, p) = when (kind) {
            BtnKind.PRIMARY -> Tok.PRIMARY to mix(Tok.PRIMARY, Tok.BG, 0.18f)
            BtnKind.SECONDARY -> Tok.RAISED to mix(Tok.RAISED, Tok.FG, 0.10f)
            BtnKind.TEXT -> Color.TRANSPARENT to Tok.RAISED
            BtnKind.DANGER -> Tok.DOWN to mix(Tok.DOWN, Tok.BG, 0.18f)
        }
        background = pressable(shape(n, r), shape(p, r))
        val color = fg ?: if (kind == BtnKind.PRIMARY || kind == BtnKind.DANGER) Tok.ON_PRIMARY else Tok.FG
        glyph?.let { addView(it, lp(dp(32), dp(32)).m(r = dp(14))) }
        addView(txt(Tok.BODY_S, color, 600, s = label).apply { tag = BUTTON_LABEL }, lp(WRAP_CONTENT))
        contentDescription = label
        setOnClickListener { onClick() }
        if (scale) pressScale()
    }

/** 아이콘만 있는 버튼(72dp). 접근성 이름은 동작으로 쓴다. */
internal fun Context.iconButton(glyph: View, description: String, onClick: () -> Unit) =
    android.widget.FrameLayout(this).apply {
        val r = dpf(16f)
        background = pressable(shape(Color.TRANSPARENT, r), shape(Tok.RAISED, r))
        contentDescription = description
        addView(glyph, android.widget.FrameLayout.LayoutParams(dp(36), dp(36), Gravity.CENTER))
        setOnClickListener { onClick() }
    }

// ── 글리프 (아이콘 한 벌, 색만 바꾼다) ─────────────────────────

internal class MicGlyph(c: Context) : View(c) {
    var ink = Tok.FG
        set(v) { field = v; invalidate() }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }

    override fun onDraw(c: Canvas) {
        fill.color = ink; stroke.color = ink
        val w = width.toFloat(); val h = height.toFloat()
        stroke.strokeWidth = w * 0.085f
        c.drawRoundRect(RectF(w * 0.35f, h * 0.06f, w * 0.65f, h * 0.58f), w * 0.15f, w * 0.15f, fill)
        c.drawArc(RectF(w * 0.20f, h * 0.24f, w * 0.80f, h * 0.74f), 0f, 180f, false, stroke)
        c.drawLine(w * 0.5f, h * 0.74f, w * 0.5f, h * 0.92f, stroke)
    }
}

/** 재생 삼각형. 무게중심이 왼쪽으로 쏠려 보여 6% 오른쪽으로 민다. */
internal class PlayGlyph(c: Context, ink: Int) : View(c) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG).also { it.color = ink; it.pathEffect = CornerPathEffect(3f) }
    override fun onDraw(c: Canvas) {
        val w = width.toFloat(); val h = height.toFloat(); val dx = w * 0.06f
        c.drawPath(Path().apply { moveTo(w * 0.24f + dx, h * 0.14f); lineTo(w * 0.84f + dx, h * 0.5f); lineTo(w * 0.24f + dx, h * 0.86f); close() }, p)
    }
}

internal class BackGlyph(c: Context, ink: Int) : View(c) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG).also {
        it.color = ink; it.style = Paint.Style.STROKE; it.strokeCap = Paint.Cap.ROUND; it.strokeJoin = Paint.Join.ROUND
    }
    override fun onDraw(c: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        p.strokeWidth = w * 0.09f
        c.drawLine(w * 0.18f, h * 0.5f, w * 0.84f, h * 0.5f, p)
        c.drawPath(Path().apply { moveTo(w * 0.46f, h * 0.2f); lineTo(w * 0.18f, h * 0.5f); lineTo(w * 0.46f, h * 0.8f) }, p)
    }
}

internal class MoreGlyph(c: Context, ink: Int) : View(c) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG).also { it.color = ink }
    override fun onDraw(c: Canvas) {
        val w = width.toFloat(); val h = height.toFloat(); val r = w * 0.08f
        for (i in 0..2) c.drawCircle(w * (0.22f + 0.28f * i), h * 0.5f, r, p)
    }
}

internal class PhoneGlyph(c: Context, ink: Int) : View(c) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG).also { it.color = ink; it.style = Paint.Style.STROKE }
    override fun onDraw(c: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        p.strokeWidth = w * 0.08f
        c.drawRoundRect(RectF(w * 0.26f, h * 0.08f, w * 0.74f, h * 0.92f), w * 0.1f, w * 0.1f, p)
        c.drawLine(w * 0.44f, h * 0.78f, w * 0.56f, h * 0.78f, p)
    }
}

/** › 또는 ⌄ / ⌃. 묶음 줄 끝과 "이번 주행" 펼침 표시. */
internal class ChevronGlyph(c: Context, ink: Int, private val down: Boolean = false, private val up: Boolean = false) : View(c) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG).also {
        it.color = ink; it.style = Paint.Style.STROKE; it.strokeCap = Paint.Cap.ROUND; it.strokeJoin = Paint.Join.ROUND
    }
    override fun onDraw(c: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        p.strokeWidth = w * 0.09f
        val path = Path()
        when {
            down -> { path.moveTo(w * 0.22f, h * 0.38f); path.lineTo(w * 0.5f, h * 0.66f); path.lineTo(w * 0.78f, h * 0.38f) }
            up -> { path.moveTo(w * 0.22f, h * 0.62f); path.lineTo(w * 0.5f, h * 0.34f); path.lineTo(w * 0.78f, h * 0.62f) }
            else -> { path.moveTo(w * 0.38f, h * 0.2f); path.lineTo(w * 0.66f, h * 0.5f); path.lineTo(w * 0.38f, h * 0.8f) }
        }
        c.drawPath(path, p)
    }
}

/** 할 일 체크 상자. 끝냈으면 채운 상자에 체크. */
internal class CheckGlyph(c: Context, private val checked: Boolean) : View(c) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG).also { it.strokeCap = Paint.Cap.ROUND; it.strokeJoin = Paint.Join.ROUND }
    override fun onDraw(c: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        val box = RectF(w * 0.12f, h * 0.12f, w * 0.88f, h * 0.88f)
        val r = w * 0.18f
        if (checked) {
            p.style = Paint.Style.FILL; p.color = Tok.FG
            c.drawRoundRect(box, r, r, p)
            p.style = Paint.Style.STROKE; p.color = Tok.BG; p.strokeWidth = w * 0.11f
            c.drawPath(Path().apply { moveTo(w * 0.3f, h * 0.52f); lineTo(w * 0.45f, h * 0.67f); lineTo(w * 0.72f, h * 0.36f) }, p)
        } else {
            p.style = Paint.Style.STROKE; p.color = Tok.SUB; p.strokeWidth = w * 0.08f
            c.drawRoundRect(box, r, r, p)
        }
    }
}
