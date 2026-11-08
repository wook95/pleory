package ai.pleos.hello.proto.kairy

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

// 가짜 시나리오 — 테마 '하루 회고', 5문항 중 3번째, 운전자 답변을 듣는 중
private const val THEME = "하루 회고"
private const val QUESTION = "오늘 가장 오래 머릿속에 남은 장면은 뭐예요?"
private const val STATE = "듣고 있어요"
private const val STEP = 3
private const val STEPS = 5

private val AMBIENT = Color.parseColor("#FFA14A") // 테마 조명 색 (상수 — 승격 때 SDK 값)
private val BG = Color.parseColor("#0E0F12")
private val FG = Color.WHITE
private val DIM = Color.parseColor("#8A8F98")

private fun Activity.px(dp: Int) = (dp * resources.displayMetrics.density).toInt()

private fun Activity.label(s: String, sp: Float, color: Int = FG, bold: Boolean = false) =
    TextView(this).apply {
        text = s
        textSize = sp
        setTextColor(color)
        if (bold) typeface = Typeface.DEFAULT_BOLD
        setLineSpacing(0f, 1.2f)
    }

private fun circle(color: Int) = GradientDrawable().apply {
    shape = GradientDrawable.OVAL
    setColor(color)
}

private fun withAlpha(color: Int, a: Int) = Color.argb(a, Color.red(color), Color.green(color), Color.blue(color))

private fun Activity.pill(s: String): TextView = label("●  $s", 32f, BG, bold = true).apply {
    background = GradientDrawable().apply {
        cornerRadius = px(48).toFloat()
        setColor(AMBIENT)
    }
    setPadding(px(48), px(20), px(48), px(20))
}

/** A. 몰입형 — 조명 색이 화면 전체에 번지고, 질문 한 줄과 상태 알약만 둔다 */
fun r1a(a: Activity): View = FrameLayout(a).apply {
    background = GradientDrawable(
        GradientDrawable.Orientation.TOP_BOTTOM,
        intArrayOf(withAlpha(AMBIENT, 150), BG, BG),
    )
    addView(a.label("$THEME · $STEP/$STEPS", 28f, FG).apply { alpha = 0.7f },
        FrameLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT, Gravity.TOP or Gravity.START).apply {
            setMargins(a.px(72), a.px(64), 0, 0)
        })
    addView(a.label(QUESTION, 64f, bold = true).apply { gravity = Gravity.CENTER },
        FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT, Gravity.CENTER).apply {
            setMargins(a.px(120), 0, a.px(120), 0)
        })
    addView(a.pill(STATE),
        FrameLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply {
            bottomMargin = a.px(96)
        })
}

/** B. 오브형 — 숨 쉬는 빛 덩어리 하나가 상태를 말하고, 질문은 그 아래 */
fun r1b(a: Activity): View = LinearLayout(a).apply {
    orientation = LinearLayout.VERTICAL
    gravity = Gravity.CENTER
    setBackgroundColor(BG)
    val glow = FrameLayout(a).apply {
        background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            gradientType = GradientDrawable.RADIAL_GRADIENT
            gradientRadius = a.px(260).toFloat()
            colors = intArrayOf(withAlpha(AMBIENT, 200), withAlpha(AMBIENT, 0))
        }
        addView(View(a).apply { background = circle(AMBIENT) },
            FrameLayout.LayoutParams(a.px(180), a.px(180), Gravity.CENTER))
    }
    addView(glow, LinearLayout.LayoutParams(a.px(520), a.px(520)))
    addView(a.label(STATE, 40f, AMBIENT, bold = true).apply { gravity = Gravity.CENTER },
        LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { topMargin = a.px(8) })
    addView(a.label(QUESTION, 44f).apply { gravity = Gravity.CENTER },
        LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply {
            setMargins(a.px(160), a.px(40), a.px(160), 0)
        })
}

/** C. 자막형 — 질문이 큰 자막처럼 왼쪽 정렬, 조명은 가장자리 띠, 아래엔 음성 파형 */
fun r1c(a: Activity): View = LinearLayout(a).apply {
    orientation = LinearLayout.HORIZONTAL
    setBackgroundColor(BG)
    addView(View(a).apply { setBackgroundColor(AMBIENT) }, LinearLayout.LayoutParams(a.px(28), MATCH_PARENT))
    val body = LinearLayout(a).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(a.px(96), a.px(120), a.px(120), a.px(96))
        addView(a.label("$THEME  $STEP/$STEPS", 28f, DIM))
        addView(a.label(QUESTION, 76f, bold = true),
            LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f).apply { topMargin = a.px(32) })
        val wave = LinearLayout(a).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            listOf(30, 70, 110, 60, 140, 90, 40, 120, 80, 50, 100, 30).forEach { h ->
                addView(View(a).apply {
                    background = GradientDrawable().apply { cornerRadius = a.px(6).toFloat(); setColor(AMBIENT) }
                }, LinearLayout.LayoutParams(a.px(14), a.px(h)).apply { marginEnd = a.px(12) })
            }
            addView(a.label(STATE, 36f, AMBIENT, bold = true),
                LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { marginStart = a.px(32) })
        }
        addView(wave, LinearLayout.LayoutParams(MATCH_PARENT, a.px(160)))
    }
    addView(body, LinearLayout.LayoutParams(0, MATCH_PARENT, 1f))
}

/** D. 타임라인형 — 오늘 이미 남긴 답이 흐리게 쌓이고, 지금 질문이 맨 아래에서 빛난다 */
fun r1d(a: Activity): View = LinearLayout(a).apply {
    orientation = LinearLayout.VERTICAL
    gravity = Gravity.BOTTOM
    setBackgroundColor(BG)
    setPadding(a.px(120), a.px(80), a.px(120), a.px(96))
    fun entry(dot: Int, q: String, ans: String) = LinearLayout(a).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        alpha = 0.45f
        addView(View(a).apply { background = circle(dot) }, LinearLayout.LayoutParams(a.px(24), a.px(24)))
        addView(a.label("$q\n“$ans”", 28f, FG),
            LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { marginStart = a.px(32) })
    }
    addView(entry(Color.parseColor("#7FA7FF"), "오늘 아침 기분은 어땠어요?", "좀 무거웠어. 회의가 많아서"),
        LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = a.px(40) })
    addView(entry(Color.parseColor("#9BE29B"), "그중 괜찮았던 순간은요?", "점심에 동료랑 산책한 거"),
        LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = a.px(72) })
    addView(View(a).apply { setBackgroundColor(withAlpha(FG, 40)) },
        LinearLayout.LayoutParams(MATCH_PARENT, a.px(2)).apply { bottomMargin = a.px(72) })
    addView(a.label(QUESTION, 60f, bold = true))
    addView(a.pill(STATE), LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { topMargin = a.px(48) })
}

/** E. 분할형 — 왼쪽은 상태(조명 색 패널), 오른쪽은 질문과 진행 점 */
fun r1e(a: Activity): View = LinearLayout(a).apply {
    orientation = LinearLayout.HORIZONTAL
    setBackgroundColor(BG)
    val left = LinearLayout(a).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        background = GradientDrawable(
            GradientDrawable.Orientation.BL_TR,
            intArrayOf(withAlpha(AMBIENT, 230), withAlpha(AMBIENT, 120)),
        )
        addView(View(a).apply { background = circle(withAlpha(BG, 90)) },
            LinearLayout.LayoutParams(a.px(220), a.px(220)))
        addView(a.label(STATE, 48f, BG, bold = true),
            LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { topMargin = a.px(48) })
    }
    addView(left, LinearLayout.LayoutParams(0, MATCH_PARENT, 2f))
    val right = LinearLayout(a).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(a.px(96), 0, a.px(96), 0)
        addView(a.label(THEME, 32f, AMBIENT, bold = true))
        addView(a.label(QUESTION, 56f, bold = true),
            LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = a.px(32) })
        val dots = LinearLayout(a).apply {
            orientation = LinearLayout.HORIZONTAL
            (1..STEPS).forEach { i ->
                addView(View(a).apply { background = circle(if (i <= STEP) AMBIENT else withAlpha(FG, 50)) },
                    LinearLayout.LayoutParams(a.px(22), a.px(22)).apply { marginEnd = a.px(18) })
            }
        }
        addView(dots, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { topMargin = a.px(64) })
    }
    addView(right, LinearLayout.LayoutParams(0, MATCH_PARENT, 3f))
}
