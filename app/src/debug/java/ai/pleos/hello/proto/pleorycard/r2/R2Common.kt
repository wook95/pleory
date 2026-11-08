package ai.pleos.hello.proto.pleorycard.r2

import android.app.Activity
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView

/*
 * 설정·동의 — 세 시안이 같은 뼈대를 쓰고 면 처리만 다르다.
 * grouped = true(B): 한 덩어리 면 안에 행 + 안쪽 구분선. false(A·C): 바탕 위 행 + 가는 선.
 * 섹션 제목에 강조색을 쓰지 않는다(강조색은 행동에만). 닫기는 Pleos Page Navigation Depth 형(← + 제목).
 */
internal class Frame(val t: Tone, val ground: Int, val grouped: Boolean)

internal val FRAME_A get() = Frame(TONE_A, TONE_A.bg, false)
internal val FRAME_B get() = Frame(TONE_B, TONE_B.bg, true)
internal val FRAME_C get() = Frame(TONE_C, TONE_C.plate, false)

private fun Activity.topBar(f: Frame, title: String, back: Boolean) = row {
    gravity = Gravity.CENTER_VERTICAL
    if (back) addView(FrameLayout(this@topBar).apply {
        background = pressable(shape(android.graphics.Color.TRANSPARENT, pxf(20f)), shape(f.t.raised, pxf(20f)))
        isClickable = true
        contentDescription = "카드로 돌아가기"
        addView(BackGlyph(context, f.t.fg), FrameLayout.LayoutParams(px(40), px(40), Gravity.CENTER))
    }, LinearLayout.LayoutParams(px(80), px(80)).m(r = px(12)))
    addView(txt(title, T.TITLE_L, f.t.fg, 700), lp(WRAP_CONTENT))
}

/** 설정 행: 제목·설명(왼쪽) + 행동 버튼(오른쪽). 좁으면 버튼을 아래로 내린다. */
private fun Activity.settingRow(f: Frame, title: String, desc: List<Pair<String, Int>>, action: View?, narrow: Boolean) =
    (if (narrow) col() else row { gravity = Gravity.CENTER_VERTICAL }).apply {
        setPadding(px(if (f.grouped) 32 else 0), px(28), px(if (f.grouped) 24 else 0), px(28))
        addView(col {
            addView(txt(title, T.BODY_M, f.t.fg, 600))
            desc.forEach { (s, c) -> addView(txt(s, T.BODY_S, c, tnum = true), lp().m(t = px(8))) }
        }, if (narrow) lp() else lp(0, WRAP_CONTENT, 1f))
        action?.let { addView(it, if (narrow) lp(WRAP_CONTENT).m(t = px(20)) else lp(WRAP_CONTENT).m(l = px(32))) }
    }

internal fun r2Settings(a: Activity, f: Frame, narrow: Boolean, confirming: Boolean = false): View {
    val t = f.t
    val rows = listOf(
        a.settingRow(f, "개인정보 처리 동의", listOf("동의함 · 11월 8일 15:17" to t.sub), a.btn(t, "동의 철회", 1), narrow),
        a.settingRow(
            f, "기록 모두 지우기",
            listOf((if (confirming) "정말 모두 지울까요? 이 차에 저장된 기록이 모두 사라지고 되돌릴 수 없어요." else "이 차에 저장된 기록을 모두 지웁니다. 되돌릴 수 없어요.") to (if (confirming) t.fg else t.sub)),
            if (confirming) a.row {
                addView(a.btn(t, "취소", 1), lp(WRAP_CONTENT))
                addView(a.btn(t, "모두 지우기", 0, fg = t.onPrimary).apply { background = shape(t.down, a.pxf(20f)) }, lp(WRAP_CONTENT).m(l = a.px(12)))
            } else a.btn(t, "모두 지우기", 1, fg = t.down),
            narrow,
        ),
        a.settingRow(
            f, "문의·신고",
            listOf("AI가 정리한 내용이 이상하거나 앱에 문제가 있으면 알려 주세요." to t.sub, "pleory.team@example.com" to t.fg),
            null, narrow,
        ),
    )
    val body = a.col {
        rows.forEachIndexed { i, r ->
            if (i > 0 || !f.grouped) addView(a.rule(t.line), lp(MATCH_PARENT, a.px(1)).m(l = if (f.grouped) a.px(32) else 0))
            addView(r)
        }
        if (!f.grouped) addView(a.rule(t.line), lp(MATCH_PARENT, a.px(1)))
        if (f.grouped) background = shape(t.plate, a.pxf(28f))
    }
    val root = a.col {
        setBackgroundColor(f.ground)
        setPadding(a.px(if (narrow) 24 else 40), a.px(32), a.px(if (narrow) 40 else 56), a.px(40))
        addView(a.topBar(f, "설정", back = true))
        addView(ScrollView(a).apply { isVerticalScrollBarEnabled = false; addView(body) },
            lp(if (narrow) MATCH_PARENT else a.px(1120), 0, 1f).m(l = a.px(if (narrow) 16 else 92), t = a.px(28)))
    }
    return if (narrow) a.narrowFrame(root) else root
}

private val CONSENT = listOf(
    "말한 내용" to "말한 내용은 차 안에서 글자로 바꾸고, 그 글자만 정리용 외부 AI(Google Gemini)로 보냅니다. 음성, 위치, 이름은 보내지 않습니다.",
    "위치" to "현재 좌표는 날씨와 주소(장소 이름)를 찾는 데 씁니다. 지금 시험판은 고정값을 써서 좌표를 밖으로 보내지 않습니다. 경로와 거리는 차 안에서 계산합니다.",
    "Gleo AI로 남긴 메모" to "Gleo AI에게 말해 남긴 메모는 Pleos 플랫폼(Gleo AI)이 처리한 뒤 이 앱으로 전달합니다.",
    "앱이 모으는 것" to "말한 내용의 글자, 말한 시각과 위치·장소 이름·주행 거리·속도, 주행 경로, 정리된 항목(유형·감정·키워드), 동의 상태와 시각.",
    "저장 장소와 기간" to "기록은 이 차에만 암호화해 저장하고, 직접 지울 때까지 보관합니다.",
    "동의하지 않으면" to "기록은 그대로 됩니다. 다만 AI 정리 없이 말한 원문 그대로 남습니다.",
    "철회와 삭제" to "설정에서 언제든 동의를 철회할 수 있고, 그때부터 외부 AI로 보내지 않습니다. 기록은 설정의 전체 삭제로 모두 지울 수 있습니다.",
)

internal fun r2Consent(a: Activity, f: Frame, narrow: Boolean): View {
    val t = f.t
    val list = a.col {
        CONSENT.forEachIndexed { i, (head, body) ->
            if (i > 0 || !f.grouped) addView(a.rule(t.line), lp(MATCH_PARENT, a.px(1)).m(l = if (f.grouped) a.px(32) else 0))
            addView((if (narrow) a.col() else a.row()).apply {
                setPadding(a.px(if (f.grouped) 32 else 0), a.px(24), a.px(if (f.grouped) 32 else 0), a.px(24))
                addView(a.txt(head, T.BODY_M, t.fg, 600), if (narrow) lp() else lp(a.px(300)))
                addView(a.txt(body, T.BODY_S, t.sub), if (narrow) lp().m(t = a.px(8)) else lp(0, WRAP_CONTENT, 1f).m(l = a.px(32)))
            })
        }
        if (!f.grouped) addView(a.rule(t.line), lp(MATCH_PARENT, a.px(1)))
        if (f.grouped) background = shape(t.plate, a.pxf(28f))
    }
    val buttons = a.row {
        gravity = Gravity.CENTER_VERTICAL or Gravity.END
        val w = if (narrow) 0 else a.px(260)
        addView(a.btn(t, "동의 안 함", 1), if (narrow) lp(0, WRAP_CONTENT, 1f) else lp(w))
        addView(a.btn(t, "동의", 0), (if (narrow) lp(0, WRAP_CONTENT, 1f) else lp(w)).m(l = a.px(16)))
    }
    val root = a.col {
        setBackgroundColor(f.ground)
        setPadding(a.px(if (narrow) 40 else 64), a.px(44), a.px(if (narrow) 40 else 64), a.px(36))
        addView(a.topBar(f, "개인정보 처리 동의", back = false))
        addView(a.txt("Pleory가 무엇을 모으고 어디로 보내는지입니다. 동의하지 않아도 기록은 됩니다.", T.BODY_M, t.sub), lp().m(t = a.px(12)))
        addView(ScrollView(a).apply { isVerticalScrollBarEnabled = false; addView(list) }, lp(MATCH_PARENT, 0, 1f).m(t = a.px(28)))
        addView(buttons, lp().m(t = a.px(24)))
    }
    return if (narrow) a.narrowFrame(root) else root
}
