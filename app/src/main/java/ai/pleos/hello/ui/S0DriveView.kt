package ai.pleos.hello.ui

import ai.pleos.hello.core.LayoutMode
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

/**
 * S0 주행 화면(PRD §5, 시안 r1 S0-B). 실시간 데이터 바인딩 없음 — 상태 텍스트는 세션 시작 때 정해진 구간 이름이고
 * 버튼만 두 상태(기본/듣는 중). 바깥 여백 40dp. 말하기 버튼은 창 크기에 비례한다([LayoutMode]).
 */
class S0DriveView(context: Context, private val onTalk: () -> Unit, onSettings: () -> Unit = {}) : FrameLayout(context) {

    private val button = LinearLayout(context)
    private val glyph = MicGlyph(context)
    private val label = TextView(context)
    private val settings = TextView(context)
    private val status = TextView(context)

    init {
        setBackgroundColor(BG)
        addView(
            LinearLayout(context).apply {
                gravity = Gravity.CENTER_VERTICAL
                addView(View(context).apply { background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(ACCENT) } }, LinearLayout.LayoutParams(dp(14), dp(14)).apply { rightMargin = dp(16) })
                addView(status.apply {
                    text = "기록 중"
                    textSize = 40f
                    setTextColor(FG)
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                })
            },
            LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.START).apply { setMargins(dp(EDGE), dp(EDGE), 0, 0) },
        )
        button.apply {
            gravity = Gravity.CENTER
            addView(glyph, LinearLayout.LayoutParams(dp(64), dp(64)))
            label.apply {
                textSize = 40f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            addView(label, LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply { leftMargin = dp(20) })
            setOnClickListener { onTalk() }
        }
        addView(button, LayoutParams(dp(440), dp(176), Gravity.BOTTOM or Gravity.START).apply { setMargins(dp(EDGE), 0, 0, dp(EDGE)) })
        settings.apply {
            text = "설정"
            textSize = 26f
            setTextColor(FG)
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            gravity = Gravity.CENTER
            minHeight = dp(80)
            minWidth = dp(160)
            background = GradientDrawable().apply { cornerRadius = dp(16).toFloat(); setColor(Color.parseColor("#1D2A42")) }
            visibility = GONE
            setOnClickListener { onSettings() }
        }
        addView(settings, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.END).apply { setMargins(0, dp(EDGE), dp(EDGE), 0) })
        setListening(false)
    }

    /** 세션 시작 때 정해진 구간 이름(출근길·퇴근길·외출). 주행 중 값이 바뀌지 않는다. */
    fun setSegmentName(name: String) {
        status.text = "$name · 기록 중"
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val d = resources.displayMetrics.density
        val wDp = (MeasureSpec.getSize(widthMeasureSpec) / d).toInt()
        val hDp = (MeasureSpec.getSize(heightMeasureSpec) / d).toInt()
        button.layoutParams.width = dp(LayoutMode.s0ButtonWidthDp(wDp))
        button.layoutParams.height = dp(LayoutMode.s0ButtonHeightDp(hDp))
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    /** 설정 진입 링크. 주차(P)·제한 없음일 때만 보인다(TRD §8-1 5번). */
    fun setSettingsVisible(visible: Boolean) {
        settings.visibility = if (visible) VISIBLE else GONE
    }

    fun setListening(listening: Boolean) {
        val fg = if (listening) ACCENT else ON_ACCENT
        button.background = GradientDrawable().apply {
            cornerRadius = dp(36).toFloat()
            if (listening) { setColor(Color.parseColor("#0E2F3C")); setStroke(dp(8), ACCENT) } else setColor(ACCENT)
        }
        glyph.color = fg
        label.setTextColor(fg)
        label.text = if (listening) "듣는 중" else "말하기"
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private class MicGlyph(c: Context) : View(c) {
        var color = Color.WHITE
            set(v) { field = v; invalidate() }
        private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
        private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }

        override fun onDraw(c: Canvas) {
            fill.color = color
            stroke.color = color
            val w = width.toFloat(); val h = height.toFloat()
            stroke.strokeWidth = w * 0.09f
            c.drawRoundRect(RectF(w * 0.34f, h * 0.06f, w * 0.66f, h * 0.60f), w * 0.16f, w * 0.16f, fill)
            c.drawArc(RectF(w * 0.18f, h * 0.22f, w * 0.82f, h * 0.74f), 0f, 180f, false, stroke)
            c.drawLine(w * 0.5f, h * 0.74f, w * 0.5f, h * 0.92f, stroke)
            c.drawLine(w * 0.32f, h * 0.92f, w * 0.68f, h * 0.92f, stroke)
        }
    }

    private companion object {
        const val EDGE = 40
        val BG = Color.parseColor("#0B1220")
        val FG = Color.parseColor("#E8ECF2")
        val ACCENT = Color.parseColor("#00AAD2")
        val ON_ACCENT = Color.parseColor("#04121A")
    }
}
