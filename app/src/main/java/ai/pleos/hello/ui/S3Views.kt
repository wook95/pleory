package ai.pleos.hello.ui

import ai.pleos.hello.core.ConsentState
import ai.pleos.hello.core.LayoutMode
import android.content.Context
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.LinearLayout
import android.widget.ScrollView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/*
 * 동의·설정(시안 r2 A). 바탕 위에 가는 선으로 나눈 행만 쓰고, 섹션 제목에 강조색을 쓰지 않는다.
 * 넓은 창은 제목·설명 옆에 버튼(또는 동의 화면의 머리말 옆에 본문), 좁은 창은 위아래로 쌓는다.
 */

/** 폭을 [maxPx] 까지만 쓰는 틀(줄 길이 제한). 창이 좁으면 창 폭을 따른다. */
private class MaxWidth(context: Context, private val maxPx: Int) : android.widget.FrameLayout(context) {
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec).coerceAtMost(maxPx)
        super.onMeasure(MeasureSpec.makeMeasureSpec(w, MeasureSpec.getMode(widthMeasureSpec)), heightMeasureSpec)
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), measuredHeight)
    }
}

/** 배치가 폭에 따라 바뀌는 화면의 공통 뼈대. [render] 가 지금 상태로 내용을 다시 그린다. */
abstract class ResponsiveScreen(context: Context) : LinearLayout(context) {
    protected var wide = true
        private set

    init {
        orientation = VERTICAL
        setBackgroundColor(Tok.BG)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val next = LayoutMode.from((w / resources.displayMetrics.density).toInt()) == LayoutMode.WIDE
        if (next != wide) { wide = next; post { render() } } // 레이아웃 패스 안에서 구조를 바꾸지 않는다
    }

    protected abstract fun render()
}

/**
 * 개인정보 처리 동의(TRD §8-1 1번). "동의" 와 "동의 안 함" 두 버튼만 선택으로 친다 —
 * 뒤로 가기·자동 수락은 동의가 아니다. 호출하는 쪽이 주차(P)·제한 없음일 때만 보이게 한다.
 * 본문은 법적 고지라 그대로 두고 머리말만 짧게 했다.
 */
class ConsentView(context: Context, private val onAgree: () -> Unit, private val onDecline: () -> Unit) : ResponsiveScreen(context) {
    init { render() }

    override fun render() {
        removeAllViews()
        val c = context
        setPadding(c.dp(if (wide) 64 else 40), c.dp(44), c.dp(if (wide) 64 else 40), c.dp(36))
        addView(c.txt(Tok.TITLE_L, Tok.FG, 700, s = "개인정보 처리 동의"))
        addView(c.txt(Tok.BODY_M, Tok.SUB, s = "Pleory가 무엇을 모으고 어디로 보내는지입니다. 동의하지 않아도 기록은 됩니다."), lp().m(t = c.dp(12)))
        val list = c.col().apply {
            LINES.forEach { (head, body) ->
                addView(c.rule(), lp(MATCH_PARENT, c.dp(1)))
                addView((if (wide) c.row().apply { gravity = android.view.Gravity.TOP } else c.col()).apply {
                    setPadding(0, c.dp(24), 0, c.dp(24))
                    addView(c.txt(Tok.BODY_M, Tok.FG, 600, s = head), if (wide) lp(c.dp(300)) else lp())
                    addView(c.txt(Tok.BODY_S, Tok.SUB, s = body), if (wide) lp(0, WRAP_CONTENT, 1f).m(l = c.dp(32)) else lp().m(t = c.dp(8)))
                })
            }
            addView(c.rule(), lp(MATCH_PARENT, c.dp(1)))
        }
        addView(ScrollView(c).apply { isVerticalScrollBarEnabled = false; addView(list) }, lp(MATCH_PARENT, 0, 1f).m(t = c.dp(28)))
        addView(c.row().apply {
            gravity = android.view.Gravity.CENTER_VERTICAL or android.view.Gravity.END
            val w = if (wide) lp(c.dp(260)) else lp(0, WRAP_CONTENT, 1f)
            addView(c.button("동의 안 함", BtnKind.SECONDARY) { onDecline() }, w)
            addView(c.button("동의", BtnKind.PRIMARY) { onAgree() }, LinearLayout.LayoutParams(w).m(l = c.dp(16)))
        }, lp().m(t = c.dp(24)))
    }

    private companion object {
        val LINES = listOf(
            "말한 내용" to "말한 내용은 차 안에서 글자로 바꾸고, 그 글자만 정리용 외부 AI로 보냅니다. 먼저 Anthropic(Claude)로 보내고, 그쪽이 실패하면 Google(Gemini)로 보냅니다. 음성, 위치, 이름은 보내지 않습니다.",
            "위치" to "현재 좌표는 날씨와 주소(장소 이름)를 찾는 데 씁니다. 지금 시험판은 고정값을 써서 좌표를 밖으로 보내지 않습니다. 경로와 거리는 차 안에서 계산합니다.",
            "Gleo AI로 남긴 메모" to "Gleo AI에게 말해 남긴 메모는 Pleos 플랫폼(Gleo AI)이 처리한 뒤 이 앱으로 전달합니다.",
            "앱이 모으는 것" to "말한 내용의 글자, 말한 시각과 위치·장소 이름·주행 거리·속도, 주행 경로, 정리된 항목(유형·감정·키워드), 동의 상태와 시각.",
            "저장 장소와 기간" to "기록은 이 차에만 암호화해 저장하고, 직접 지울 때까지 보관합니다.",
            "동의하지 않으면" to "기록은 그대로 됩니다. 다만 AI 정리 없이 말한 원문 그대로 남습니다.",
            "철회와 삭제" to "설정에서 언제든 동의를 철회할 수 있고, 그때부터 외부 AI로 보내지 않습니다. 기록은 설정의 전체 삭제로 모두 지울 수 있습니다.",
        )
    }
}

/** S3 설정(최소): 동의 상태, 동의 철회/동의, 2단계 확인을 거치는 기록 모두 지우기, 문의·신고 연락처. 닫기는 ← (Pleos Page Navigation Depth). */
class SettingsView(
    context: Context,
    private val onWithdraw: () -> Unit,
    private val onConsent: () -> Unit,
    private val onDeleteAll: () -> Unit,
    private val onClose: () -> Unit,
) : ResponsiveScreen(context) {
    private var state = ConsentState.UNSET
    private var at = 0L
    private var confirming = false
    private var deleted = false

    init { render() }

    /** 화면이 보이기 전에 부른다. 삭제 확인 단계는 처음으로 되돌린다. */
    fun bind(state: ConsentState, at: Long) {
        this.state = state
        this.at = at
        confirming = false
        deleted = false
        render()
    }

    fun showDeleted() {
        deleted = true
        confirming = false
        render()
    }

    override fun render() {
        removeAllViews()
        val c = context
        setPadding(c.dp(if (wide) 40 else 24), c.dp(32), c.dp(if (wide) 56 else 40), c.dp(40))
        addView(c.row().apply {
            addView(c.iconButton(BackGlyph(c, Tok.FG), "카드로 돌아가기") { onClose() }, lp(c.dp(80), c.dp(80)).m(r = c.dp(12)))
            addView(c.txt(Tok.TITLE_L, Tok.FG, 700, s = "설정"), lp(WRAP_CONTENT))
        })
        val rows = c.col().apply {
            addView(c.rule(), lp(MATCH_PARENT, c.dp(1)))
            addView(settingRow(
                "개인정보 처리 동의", listOf(statusText() to Tok.SUB),
                if (state == ConsentState.GRANTED) c.button("동의 철회", BtnKind.SECONDARY) { onWithdraw() }
                else c.button("동의하기", BtnKind.PRIMARY) { onConsent() },
            ))
            addView(c.rule(), lp(MATCH_PARENT, c.dp(1)))
            addView(settingRow(
                "기록 모두 지우기",
                listOf(
                    when {
                        deleted -> "모든 기록을 지웠어요." to Tok.FG
                        confirming -> "정말 모두 지울까요? 이 차에 저장된 기록이 모두 사라지고 되돌릴 수 없어요." to Tok.FG
                        else -> "이 차에 저장된 기록을 모두 지웁니다. 되돌릴 수 없어요." to Tok.SUB
                    },
                ),
                // 확인 단계에서는 "취소" 가 첫 탭 자리(행 끝)에 온다 — 두 번 연달아 눌러도 지워지지 않게
                if (confirming) c.row().apply {
                    if (wide) {
                        addView(c.button("모두 지우기", BtnKind.DANGER) { onDeleteAll() }, lp(WRAP_CONTENT))
                        addView(c.button("취소", BtnKind.SECONDARY) { confirming = false; render() }, lp(WRAP_CONTENT).m(l = c.dp(12)))
                    } else {
                        addView(c.button("취소", BtnKind.SECONDARY) { confirming = false; render() }, lp(WRAP_CONTENT))
                        addView(c.button("모두 지우기", BtnKind.DANGER) { onDeleteAll() }, lp(WRAP_CONTENT).m(l = c.dp(12)))
                    }
                } else c.button("모두 지우기", BtnKind.SECONDARY, fg = Tok.DOWN) { confirming = true; deleted = false; render() },
            ))
            addView(c.rule(), lp(MATCH_PARENT, c.dp(1)))
            // 심사 가이드: AI 생성 콘텐츠 앱은 사용자 신고 경로가 필요하다(PRD v4 F9·F12)
            addView(settingRow(
                "문의·신고",
                listOf("AI가 정리한 내용이 부적절하거나 앱에 문제가 있으면 알려 주세요." to Tok.SUB, CONTACT_EMAIL to Tok.FG),
                null,
            ))
            addView(c.rule(), lp(MATCH_PARENT, c.dp(1)))
        }
        addView(MaxWidth(c, c.dp(1120)).apply { addView(ScrollView(c).apply { isVerticalScrollBarEnabled = false; addView(rows) }) },
            lp(MATCH_PARENT, 0, 1f).m(l = c.dp(if (wide) 92 else 16), t = c.dp(28)))
    }

    /** 제목·설명(왼쪽) + 행동(오른쪽). 좁으면 행동을 아래로 내린다. */
    private fun settingRow(title: String, lines: List<Pair<String, Int>>, action: View?): View {
        val c = context
        return (if (wide) c.row() else c.col()).apply {
            setPadding(0, c.dp(28), 0, c.dp(28))
            addView(c.col().apply {
                addView(c.txt(Tok.BODY_M, Tok.FG, 600, s = title))
                lines.forEach { (s, color) -> addView(c.txt(Tok.BODY_S, color, tnum = true, s = s), lp().m(t = c.dp(8))) }
            }, if (wide) lp(0, WRAP_CONTENT, 1f) else lp())
            action?.let { addView(it, if (wide) lp(WRAP_CONTENT).m(l = c.dp(32)) else lp(WRAP_CONTENT).m(t = c.dp(20))) }
        }
    }

    private fun statusText() = when (state) {
        ConsentState.GRANTED -> "동의함 · ${fmt(at)}"
        ConsentState.DECLINED -> "동의 안 함 · ${fmt(at)}. AI 정리 없이 원문으로 기록합니다."
        ConsentState.WITHDRAWN -> "철회함 · ${fmt(at)}. AI 정리 없이 원문으로 기록합니다."
        ConsentState.UNSET -> "아직 답하지 않음. AI 정리 없이 원문으로 기록합니다."
    }

    private fun fmt(at: Long) = SimpleDateFormat("M월 d일 HH:mm", Locale.KOREA).format(Date(at))

    private companion object {
        /** 문의·신고 연락처(README 개인정보 처리와 같은 주소). */
        const val CONTACT_EMAIL = "uclee95@gmail.com"
    }
}
