package ai.pleos.hello.ui

import ai.pleos.hello.core.LayoutMode
import android.content.Context
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.LinearLayout

/**
 * S0 주행 화면(PRD §5, 시안 r2 A). 실시간 데이터 바인딩 없음 — 제목은 세션 시작 때 정해진 구간 이름이고
 * 버튼만 두 상태다. 기본은 낮은 면 + 테두리(차분), 듣는 중은 밝은 잉크로 채워 가장 눈에 띄게 한다.
 * 주행 중이므로 아무것도 움직이지 않는다(눌림 축소 없음, 색·문구만 바뀜). 말하기 버튼은 창 폭 전체의 아래 띠이고
 * 높이는 창의 3분의 1이다([LayoutMode]). 아이콘·글자는 운전석 쪽(왼쪽)에 둔다.
 */
class S0DriveView(context: Context, private val onTalk: () -> Unit, onSettings: () -> Unit = {}, onHistory: () -> Unit = {}) : FrameLayout(context) {

    private val segment = context.txt(Tok.TITLE_L, Tok.FG, 700, s = "기록 중")
    private val button = LinearLayout(context)
    private val glyph = MicGlyph(context)
    private val label = context.txt(Tok.HEAD_M, Tok.FG, 700)
    private val hint = context.txt(Tok.BODY_L, Tok.ON_PRIMARY, s = "한 번 더 누르면 취소")
    private val settings = context.row().apply {
        addView(context.button("지난 기록", BtnKind.TEXT, padH = 28, scale = false) { onHistory() }, lp(LayoutParams.WRAP_CONTENT))
        addView(context.button("설정", BtnKind.TEXT, padH = 28, scale = false) { onSettings() }, lp(LayoutParams.WRAP_CONTENT).m(l = context.dp(8)))
    }
    private var listening: Boolean? = null
    private var shortButton = false

    private companion object {
        /** 말하기 줄(72) + 간격(12) + 안내 줄(38) + 위아래 숨 — 이보다 낮은 띠에서는 안내를 뺀다 */
        const val HINT_MIN_BUTTON_DP = 180
    }

    init {
        setBackgroundColor(Tok.BG)
        val e = context.dp(Tok.EDGE)
        addView(context.col().apply {
            addView(segment)
            addView(context.txt(Tok.BODY_M, Tok.SUB, s = "기록하고 있어요"), lp().m(t = context.dp(10)))
        }, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.START).apply { setMargins(e, e, 0, 0) })

        button.apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(context.dp(64), 0, context.dp(48), 0)
            addView(glyph, LinearLayout.LayoutParams(context.dp(96), context.dp(96)).apply { rightMargin = context.dp(32) })
            addView(context.col().apply {
                addView(label)
                addView(hint, lp().m(t = context.dp(12)))
            }, lp(0, LayoutParams.WRAP_CONTENT, 1f))
            setOnClickListener { onTalk() }
        }
        val be = context.dp(LayoutMode.S0_EDGE_DP)
        // 크기는 onMeasure 가 창에 맞춰 정한다
        addView(button, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, Gravity.BOTTOM or Gravity.START).apply { setMargins(be, 0, 0, be) })
        settings.visibility = GONE
        addView(settings, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.END).apply { setMargins(0, e - context.dp(16), e - context.dp(12), 0) })
        setListening(false)
    }

    /** 세션 시작 때 정해진 구간 이름(출근길·퇴근길·외출). 주행 중 값이 바뀌지 않는다. */
    fun setSegmentName(name: String) {
        segment.text = name
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val d = resources.displayMetrics.density
        val wDp = (MeasureSpec.getSize(widthMeasureSpec) / d).toInt()
        val hDp = (MeasureSpec.getSize(heightMeasureSpec) / d).toInt()
        button.layoutParams.width = context.dp(LayoutMode.s0ButtonWidthDp(wDp))
        val bh = LayoutMode.s0ButtonHeightDp(hDp)
        button.layoutParams.height = context.dp(bh)
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val short = LayoutMode.s0ButtonHeightDp((h / resources.displayMetrics.density).toInt()) < HINT_MIN_BUTTON_DP
        if (short != shortButton) { shortButton = short; post { applyHint() } } // 레이아웃 패스 밖에서 바꾼다
    }

    /**
     * 안내 줄은 듣는 중에만 보인다. 띠가 충분히 높으면 숨길 때도 자리를 남겨(INVISIBLE) "말하기" ↔ "듣고 있어요" 가
     * 같은 자리에 있게 한다 — 주행 중 탭할 때 글자가 위아래로 움직이지 않는다. 낮은 띠에서는 자리째 뺀다(GONE).
     */
    private fun applyHint() {
        hint.visibility = when {
            shortButton -> GONE
            listening == true -> VISIBLE
            else -> INVISIBLE
        }
    }

    /** 지난 기록·설정 진입. 주차(P)·제한 없음일 때만 보인다(TRD §8-1 5번). */
    fun setSettingsVisible(visible: Boolean) {
        settings.visibility = if (visible) VISIBLE else GONE
    }

    fun setListening(listening: Boolean) {
        if (this.listening == listening) return
        this.listening = listening
        val ink = if (listening) Tok.ON_PRIMARY else Tok.FG
        val r = context.dpf(36f)
        button.background = if (listening) shape(Tok.PRIMARY, r) else shape(Tok.RAISED, r, context.dp(2), Tok.RING)
        glyph.ink = ink
        label.setTextColor(ink)
        label.text = if (listening) "듣고 있어요" else "말하기"
        applyHint()
        button.contentDescription = if (listening) "듣는 중. 누르면 취소" else "말하기"
    }

}
