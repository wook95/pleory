package ai.pleos.hello.ui

import ai.pleos.hello.core.CardFormat
import ai.pleos.hello.core.HistoryRow
import ai.pleos.hello.data.Emotion
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.LinearLayout
import android.widget.ScrollView

/**
 * S2 지난 기록(F11, 시안 r2 A 결). 최근 7일 세션을 최신순으로 한 줄씩 — 날짜·시각, 구간, 시간·거리, 감정 흐름 바, 항목 수.
 * 상자 없이 가는 선으로 행을 나누고, 행 전체가 누르는 자리다(→ 그 세션의 S1 카드). 넓은 창은 2열, 좁은 창은 1열이며 내용은 같다.
 * "할 일만" 은 할 일이 남은 세션만 남기고 행 아래에 할 일을 붙인다. 필터 상태는 호출하는 쪽이 쥔다([setRows]).
 */
class S2HistoryView(
    context: Context,
    private val onBack: () -> Unit,
    private val onOpen: (Long) -> Unit,
    private val onFilter: (Boolean) -> Unit,
) : ResponsiveScreen(context) {

    private var rows: List<HistoryRow>? = null
    private var todoOnly = false

    init { render() }

    /** [rows] 가 null 이면 읽는 중(목록 자리를 비워 둔다). */
    fun setRows(rows: List<HistoryRow>?, todoOnly: Boolean) {
        this.rows = rows
        this.todoOnly = todoOnly
        render()
    }

    override fun render() {
        removeAllViews()
        val c = context
        val side = c.dp(if (wide) 40 else 24)
        setPadding(side, c.dp(32), c.dp(if (wide) 48 else 32), 0)
        addView(c.row().apply {
            addView(c.iconButton(BackGlyph(c, Tok.FG), "돌아가기") { onBack() }, lp(c.dp(80), c.dp(80)).m(r = c.dp(12)))
            addView(c.col().apply {
                addView(c.txt(Tok.TITLE_L, Tok.FG, 700, s = "지난 기록"))
                addView(c.txt(Tok.LABEL_M, Tok.SUB, s = "최근 7일"), lp().m(t = c.dp(6)))
            }, lp(0, WRAP_CONTENT, 1f))
            addView(filter(), lp(WRAP_CONTENT))
        })
        val list = c.col().apply { setPadding(if (wide) c.dp(92) else 0, c.dp(20), 0, c.dp(40)) }
        val rs = rows
        when {
            rs == null -> Unit
            rs.isEmpty() -> list.addView(empty(), lp().m(t = c.dp(24)))
            wide -> rs.chunked(2).forEach { pair ->
                list.addView(c.row().apply {
                    gravity = Gravity.TOP
                    pair.forEachIndexed { i, r -> addView(entry(r), lp(0, WRAP_CONTENT, 1f).m(l = if (i == 1) c.dp(40) else 0)) }
                    if (pair.size == 1) addView(View(c), lp(0, 1, 1f).m(l = c.dp(40)))
                })
            }
            else -> rs.forEach { list.addView(entry(it)) }
        }
        addView(ScrollView(c).apply { isVerticalScrollBarEnabled = false; addView(list) }, lp(MATCH_PARENT, 0, 1f).m(t = c.dp(12)))
    }

    /** 모두 / 할 일만 — 고른 쪽만 면을 깐다. 탭하면 바로 표시를 바꾸고(0.25초 응답) 목록은 다시 읽는다. */
    private fun filter() = context.row().apply {
        val c = context
        fun option(label: String, value: Boolean) = c.button(label, if (todoOnly == value) BtnKind.SECONDARY else BtnKind.TEXT, padH = 28,
            fg = if (todoOnly == value) Tok.FG else Tok.SUB) {
            if (todoOnly != value) { todoOnly = value; render(); onFilter(value) }
        }.apply {
            isSelected = todoOnly == value
            contentDescription = if (todoOnly == value) "$label, 선택됨" else label
        }
        addView(option("모두", false), lp(WRAP_CONTENT))
        addView(option("할 일만", true), lp(WRAP_CONTENT).m(l = c.dp(8)))
    }

    private fun empty() = context.col().apply {
        val c = context
        addView(c.rule(), lp(MATCH_PARENT, c.dp(1)))
        val (title, body) = if (todoOnly) "최근 7일에 남은 할 일이 없어요" to "운전 중에 할 일을 말하면 여기에 모여요."
        else "최근 7일 기록이 없어요" to "운전 중에 말하기 버튼을 누르고 떠오르는 대로 말하면 여기에 날짜별로 쌓여요."
        addView(c.txt(Tok.BODY_M, Tok.FG, 600, s = title), lp().m(t = c.dp(32)))
        addView(c.txt(Tok.BODY_S, Tok.SUB, s = body), lp().m(t = c.dp(8)))
    }

    /** 행 하나 = 위 선 + 날짜·시각 / 구간 ··· 시간·거리 / 흐름 바 / 감정 낱말 ··· 항목 수 (+ 할 일). */
    private fun entry(r: HistoryRow) = context.col().apply {
        val c = context
        addView(c.rule(), lp(MATCH_PARENT, c.dp(1)))
        addView(c.col().apply {
            setPadding(c.dp(20), c.dp(24), c.dp(20), c.dp(24))
            val radius = c.dpf(Tok.BTN_R.toFloat())
            background = pressable(shape(android.graphics.Color.TRANSPARENT, radius), shape(Tok.RAISED, radius))
            addView(c.txt(Tok.LABEL_M, Tok.SUB, tnum = true, s = "${r.day}  ${r.timeRange}"))
            addView(c.row().apply {
                addView(c.txt(Tok.BODY_L, Tok.FG, 700, s = r.segment), lp(0, WRAP_CONTENT, 1f))
                addView(c.txt(Tok.BODY_S, Tok.SUB, tnum = true, s = r.stats), lp(WRAP_CONTENT).m(l = c.dp(16)))
            }, lp().m(t = c.dp(8)))
            addView(MoodBar(c, r.moods), lp(MATCH_PARENT, c.dp(12)).m(t = c.dp(20)))
            addView(c.row().apply {
                addView(c.txt(Tok.LABEL_M, Tok.SUB, s = moodWords(r.moods)), lp(0, WRAP_CONTENT, 1f))
                addView(c.txt(Tok.LABEL_M, Tok.SUB, tnum = true, s = "항목 ${r.itemCount}개"), lp(WRAP_CONTENT).m(l = c.dp(16)))
            }, lp().m(t = c.dp(12)))
            if (todoOnly) r.todos.forEach { todo ->
                addView(c.row().apply {
                    gravity = Gravity.TOP
                    addView(c.txt(Tok.LABEL_M, Tok.SUB, s = "할 일"), lp(c.dp(72)).m(t = c.dp(4)))
                    addView(c.txt(Tok.BODY_S, Tok.FG, 500, s = todo), lp(0, WRAP_CONTENT, 1f))
                }, lp().m(t = c.dp(14)))
            }
            contentDescription = "${r.day} ${r.segment} ${r.timeRange}, ${r.stats}, 항목 ${r.itemCount}개. 카드 열기"
            setOnClickListener { onOpen(r.sessionId) }
            pressScale()
        }, lp())
    }

    /** 흐름의 처음과 끝을 글리프·낱말·색으로(3개 이하면 전부). 바와 함께 색에만 기대지 않는다. */
    private fun moodWords(moods: List<Emotion>): CharSequence {
        if (moods.isEmpty()) return "감정 기록 없음"
        val shown = if (moods.size <= 3) moods else listOf(moods.first(), null, moods.last())
        return SpannableStringBuilder().apply {
            shown.forEachIndexed { i, e ->
                if (i > 0) {
                    val s = length; append("  —  ")
                    setSpan(ForegroundColorSpan(Tok.FAINT), s, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
                if (e == null) { append("…"); return@forEachIndexed }
                val s = length; append("${CardFormat.emotionGlyph(e)} ${Tok.emotionWord(e)}")
                setSpan(ForegroundColorSpan(Tok.emotionColor(e)), s, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }
    }

    /** 감정 흐름 바: 정리된 항목마다 한 칸, 말한 순서. 감정이 없으면 빈 줄(구분선 색)만. */
    private class MoodBar(c: Context, private val moods: List<Emotion>) : View(c) {
        private val p = Paint(Paint.ANTI_ALIAS_FLAG)
        private val d = resources.displayMetrics.density

        override fun onDraw(canvas: Canvas) {
            val w = width.toFloat(); val h = height.toFloat(); val r = h / 2
            if (moods.isEmpty()) {
                p.color = Tok.LINE; canvas.drawRoundRect(RectF(0f, 0f, w, h), r, r, p)
                return
            }
            val gap = 4 * d
            val seg = (w - gap * (moods.size - 1)) / moods.size
            moods.forEachIndexed { i, e ->
                val x = i * (seg + gap)
                p.color = Tok.emotionColor(e)
                canvas.drawRoundRect(RectF(x, 0f, x + seg, h), r, r, p)
            }
        }
    }
}
