package ai.pleos.hello.ui

import ai.pleos.hello.core.ConsentState
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.text.LineBreakConfig
import android.os.Build
import android.view.Gravity
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 동의·설정 화면이 같이 쓰는 위젯 조각. 색은 S0·S1 과 같다(bg #0B1220, text #E8ECF2, accent #00AAD2). */
private object Kit {
    const val EDGE = 56
    const val TOUCH = 88
    const val TITLE_SP = 40f
    const val BODY_SP = 26f
    val BG = Color.parseColor("#0B1220")
    val SURFACE = Color.parseColor("#141F33")
    val CHIP = Color.parseColor("#1D2A42")
    val FG = Color.parseColor("#E8ECF2")
    val SUB = Color.parseColor("#9AA8BC")
    val ACCENT = Color.parseColor("#00AAD2")
    val ON_ACCENT = Color.parseColor("#04121A")
    val DANGER = Color.parseColor("#E05A6A")

    fun dp(c: Context, v: Int) = (v * c.resources.displayMetrics.density).toInt()

    fun text(c: Context, sp: Float, color: Int, weight: Int = 400) = TextView(c).apply {
        textSize = sp
        setTextColor(color)
        typeface = Typeface.create(Typeface.DEFAULT, weight, false)
        includeFontPadding = false
        if (Build.VERSION.SDK_INT >= 33) lineBreakWordStyle = LineBreakConfig.LINE_BREAK_WORD_STYLE_PHRASE // 좁은 폭에서 한 글자만 남는 줄바꿈 방지
    }

    fun button(c: Context, label: String, fill: Int, fg: Int, onClick: () -> Unit) = text(c, BODY_SP, fg, 700).apply {
        text = label
        gravity = Gravity.CENTER
        minHeight = dp(c, TOUCH)
        setPadding(dp(c, 40), 0, dp(c, 40), 0)
        background = GradientDrawable().apply { cornerRadius = dp(c, 16).toFloat(); setColor(fill) }
        setOnClickListener { onClick() }
    }

    fun col(c: Context) = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL }
    fun row(c: Context) = LinearLayout(c).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
    fun lp(w: Int = MATCH_PARENT, h: Int = WRAP_CONTENT, weight: Float = 0f) = LinearLayout.LayoutParams(w, h, weight)
    fun LinearLayout.LayoutParams.m(l: Int = 0, t: Int = 0, r: Int = 0, b: Int = 0) = apply { setMargins(l, t, r, b) }
}

/**
 * 개인정보 처리 동의(TRD §8-1 1번). "동의" 와 "동의 안 함" 두 버튼만 선택으로 친다 —
 * 뒤로 가기·자동 수락은 동의가 아니다. 호출하는 쪽이 주차(P)·제한 없음일 때만 보이게 한다.
 */
class ConsentView(context: Context, onAgree: () -> Unit, onDecline: () -> Unit) : LinearLayout(context) {
    init {
        orientation = VERTICAL
        setBackgroundColor(Kit.BG)
        val e = Kit.dp(context, Kit.EDGE)
        setPadding(e, e, e, e)
        addView(Kit.text(context, Kit.TITLE_SP, Kit.FG, 700).apply { text = "개인정보 처리 동의" })
        addView(
            ScrollView(context).apply {
                isVerticalScrollBarEnabled = false
                addView(Kit.col(context).apply {
                    LINES.forEachIndexed { i, (head, body) ->
                        addView(Kit.col(context).apply {
                            background = GradientDrawable().apply { cornerRadius = Kit.dp(context, 14).toFloat(); setColor(Kit.SURFACE) }
                            setPadding(Kit.dp(context, 28), Kit.dp(context, 18), Kit.dp(context, 28), Kit.dp(context, 18))
                            addView(Kit.text(context, 24f, Kit.ACCENT, 700).apply { text = head })
                            addView(Kit.text(context, Kit.BODY_SP, Kit.FG).apply { text = body }, Kit.run { lp().m(t = Kit.dp(context, 6)) })
                        }, Kit.run { lp().m(t = if (i == 0) 0 else Kit.dp(context, 12)) })
                    }
                })
            },
            Kit.run { lp(MATCH_PARENT, 0, 1f).m(t = Kit.dp(context, 24)) },
        )
        addView(Kit.row(context).apply {
            addView(Kit.button(context, "동의 안 함", Kit.CHIP, Kit.FG) { onDecline() }, Kit.run { lp(0, WRAP_CONTENT, 1f) })
            addView(Kit.button(context, "동의", Kit.ACCENT, Kit.ON_ACCENT) { onAgree() }, Kit.run { lp(0, WRAP_CONTENT, 1f).m(l = Kit.dp(context, 20)) })
        }, Kit.run { lp().m(t = Kit.dp(context, 24)) })
    }

    private companion object {
        val LINES = listOf(
            "AI로 보내는 것" to "말한 내용의 글자만 정리용 외부 AI(Google Gemini)로 보냅니다. 음성, 위치, 이름은 보내지 않습니다.",
            "위치" to "위치는 차 안에서 경로와 거리를 계산하는 데만 씁니다.",
            "저장 장소와 기간" to "기록은 이 차에만 저장하고, 직접 지울 때까지 보관합니다.",
            "동의하지 않으면" to "기록은 그대로 됩니다. 다만 AI 정리 없이 말한 원문 그대로 남습니다.",
            "철회와 삭제" to "설정에서 언제든 동의를 철회하고 기록을 전체 삭제할 수 있습니다.",
        )
    }
}

/** S3 설정(최소): 동의 상태, 동의 철회/동의, 2단계 확인을 거치는 전체 삭제. */
class SettingsView(
    context: Context,
    private val onWithdraw: () -> Unit,
    private val onConsent: () -> Unit,
    private val onDeleteAll: () -> Unit,
    private val onClose: () -> Unit,
) : LinearLayout(context) {
    private val status = Kit.text(context, Kit.BODY_SP, Kit.FG)
    private val consentBtnBox = Kit.col(context)
    private val deleteBox = Kit.col(context)
    private val notice = Kit.text(context, Kit.BODY_SP, Kit.ACCENT, 500)
    private var confirming = false

    init {
        orientation = VERTICAL
        setBackgroundColor(Kit.BG)
        val e = Kit.dp(context, Kit.EDGE)
        setPadding(e, e, e, e)
        addView(Kit.row(context).apply {
            addView(Kit.text(context, Kit.TITLE_SP, Kit.FG, 700).apply { text = "설정" }, Kit.run { lp(0, WRAP_CONTENT, 1f) })
            addView(Kit.button(context, "닫기", Kit.CHIP, Kit.FG) { onClose() }, Kit.run { lp(WRAP_CONTENT, WRAP_CONTENT) })
        })
        addView(Kit.col(context).apply {
            background = GradientDrawable().apply { cornerRadius = Kit.dp(context, 14).toFloat(); setColor(Kit.SURFACE) }
            setPadding(Kit.dp(context, 28), Kit.dp(context, 22), Kit.dp(context, 28), Kit.dp(context, 22))
            addView(Kit.text(context, 24f, Kit.ACCENT, 700).apply { text = "개인정보 동의" })
            addView(status, Kit.run { lp().m(t = Kit.dp(context, 8)) })
            addView(consentBtnBox, Kit.run { lp().m(t = Kit.dp(context, 20)) })
        }, Kit.run { lp().m(t = Kit.dp(context, 28)) })
        addView(Kit.col(context).apply {
            background = GradientDrawable().apply { cornerRadius = Kit.dp(context, 14).toFloat(); setColor(Kit.SURFACE) }
            setPadding(Kit.dp(context, 28), Kit.dp(context, 22), Kit.dp(context, 28), Kit.dp(context, 22))
            addView(Kit.text(context, 24f, Kit.ACCENT, 700).apply { text = "기록 삭제" })
            addView(
                Kit.text(context, Kit.BODY_SP, Kit.FG).apply { text = "이 차에 저장된 모든 기록을 지웁니다. 되돌릴 수 없습니다." },
                Kit.run { lp().m(t = Kit.dp(context, 8)) },
            )
            addView(deleteBox, Kit.run { lp().m(t = Kit.dp(context, 20)) })
        }, Kit.run { lp().m(t = Kit.dp(context, 20)) })
        addView(notice, Kit.run { lp().m(t = Kit.dp(context, 24)) })
    }

    /** 화면이 보이기 전에 부른다. 삭제 확인 단계는 처음으로 되돌린다. */
    fun bind(state: ConsentState, at: Long) {
        confirming = false
        notice.text = ""
        status.text = when (state) {
            ConsentState.GRANTED -> "동의함 (${fmt(at)})"
            ConsentState.DECLINED -> "동의 안 함 (${fmt(at)}). AI 정리 없이 원문으로 기록합니다."
            ConsentState.WITHDRAWN -> "철회함 (${fmt(at)}). AI 정리 없이 원문으로 기록합니다."
            ConsentState.UNSET -> "아직 답하지 않음. AI 정리 없이 원문으로 기록합니다."
        }
        consentBtnBox.removeAllViews()
        consentBtnBox.addView(
            if (state == ConsentState.GRANTED) Kit.button(context, "동의 철회", Kit.CHIP, Kit.FG) { onWithdraw() }
            else Kit.button(context, "동의하기", Kit.ACCENT, Kit.ON_ACCENT) { onConsent() },
            Kit.run { lp(WRAP_CONTENT, WRAP_CONTENT) },
        )
        renderDelete()
    }

    fun showDeleted() {
        notice.text = "모든 기록을 지웠습니다."
        confirming = false
        renderDelete()
    }

    private fun renderDelete() {
        deleteBox.removeAllViews()
        if (!confirming) {
            deleteBox.addView(Kit.button(context, "전체 삭제", Kit.CHIP, Kit.DANGER) {
                confirming = true
                notice.text = ""
                renderDelete()
            }, Kit.run { lp(WRAP_CONTENT, WRAP_CONTENT) })
        } else {
            deleteBox.addView(Kit.text(context, Kit.BODY_SP, Kit.DANGER, 700).apply { text = "정말 모두 지울까요?" })
            deleteBox.addView(Kit.row(context).apply {
                addView(Kit.button(context, "취소", Kit.CHIP, Kit.FG) { confirming = false; renderDelete() }, Kit.run { lp(WRAP_CONTENT, WRAP_CONTENT) })
                addView(Kit.button(context, "삭제", Kit.DANGER, Kit.ON_ACCENT) { onDeleteAll() }, Kit.run { lp(WRAP_CONTENT, WRAP_CONTENT).m(l = Kit.dp(context, 20)) })
            }, Kit.run { lp().m(t = Kit.dp(context, 14)) })
        }
    }

    private fun fmt(at: Long) = SimpleDateFormat("M월 d일 HH:mm", Locale.KOREA).format(Date(at))
}
