package ai.pleos.hello.ui

import ai.pleos.hello.core.ArrivalReminder
import ai.pleos.hello.core.CardFormat
import ai.pleos.hello.core.CardGroup
import ai.pleos.hello.core.CardGroupItem
import ai.pleos.hello.core.CardItem
import ai.pleos.hello.core.CardModel
import ai.pleos.hello.core.CardModelBuilder
import ai.pleos.hello.core.GeoPoint
import ai.pleos.hello.core.ItemGroup
import ai.pleos.hello.core.LayoutMode
import ai.pleos.hello.core.ReminderCandidate
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
 * S1 카드(TRD §8-11, 시안 r2 A "일지" 토큰). 맨 위에 감정 / 일 / 아이디어 묶음마다 큰 대표 문장 한 줄,
 * 줄을 누르면 그 묶음 상세(노션 템플릿처럼 제목 → 항목 목록, 일은 체크 상자), 주행 정보는 맨 아래 접힌 "이번 주행".
 *
 * - 넓은 창([LayoutMode.WIDE]): 왼쪽 58% 요약, 오른쪽 42% 판. 판은 평소 경로 도식이고, 묶음을 열면 그 자리에 상세가 온다(왼쪽 줄이 선택 표시).
 * - 좁은 창: 한 열. 묶음을 열면 상세가 요약 자리를 바꿔 차지한다.
 * - 항목 ⋯ → 같은 자리에서 "지우기 / 취소"(취소가 ⋯ 자리에 와서 두 번 눌러도 지워지지 않는다). 지우기는 [onDeleteItem](DB Item id).
 * - 아래 버튼 줄과, 재생 중일 때 그 위의 요약 자막 줄은 스크롤 밖에 고정이다. [setModel] 로 내용을, [setSummary] 로 요약 자막을 바꾼다.
 */
class S1CardView(
    context: Context,
    private val onReplay: () -> Unit,
    private val onSkip: () -> Unit,
    private val onQr: () -> Unit,
    private val onSettings: () -> Unit = {},
    private val onDeleteItem: (Long) -> Unit = {},
    private val onHistory: () -> Unit = {},
    private val onReminderDone: (Long) -> Unit = {},
    /** 묶음 상세의 체크 상자(US-13). (항목 id, 끝냈는가) */
    private val onItemDone: (Long, Boolean) -> Unit = { _, _ -> },
    /** 화면 흐름 로그(PleoryFlow). 묶음 열기·이번 주행 펼치기를 남긴다. */
    private val trace: (String) -> Unit = {},
) : LinearLayout(context) {

    /** 도착 리마인드 띠(F15). 글 맨 위에 붙고, 비면 숨는다. [setReminders] 로 채운다. */
    private val reminderStrip = context.col().apply { visibility = GONE }

    private val page = context.col()
    private val detail = context.col()
    private val detailScroll = ScrollView(context).apply { isVerticalScrollBarEnabled = false; addView(detail) }
    private val route = RouteView(context)
    private val routePlate = FrameLayout(context).apply {
        background = shape(Tok.PLATE, context.dpf(Tok.PLATE_R.toFloat()))
        addView(route, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
    }
    private val detailPlate = FrameLayout(context).apply {
        background = shape(Tok.PLATE, context.dpf(Tok.PLATE_R.toFloat()))
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
    private val qr = context.button("폰에서 열기", BtnKind.SECONDARY, PhoneGlyph(context, Tok.FG)) { onQr() }
    private val history = context.button("지난 기록", BtnKind.TEXT, padH = 28) { onHistory() }
    private val settings = context.button("설정", BtnKind.TEXT, padH = 28) { onSettings() }

    /** 지난 기록에서 연 카드일 때만 보이는 되돌아가기. 그때는 요약·지난 기록·설정 버튼을 숨긴다(요약은 지금 세션 것이라). "폰에서 열기"는 카드마다라 남긴다. */
    private var onBrowseBack: (() -> Unit)? = null
    private val back = context.button("지난 기록으로", BtnKind.SECONDARY, BackGlyph(context, Tok.FG)) { onBrowseBack?.invoke() }
        .apply { visibility = GONE }
    private val actions = context.row().apply {
        addView(back, lp(WRAP_CONTENT))
        addView(primary, lp(WRAP_CONTENT))
        addView(qr, lp(WRAP_CONTENT).m(l = context.dp(16)))
        addView(View(context), lp(0, 1, 1f))
        addView(history, lp(WRAP_CONTENT))
        addView(settings, lp(WRAP_CONTENT).m(l = context.dp(8)))
    }
    private var mode: LayoutMode? = null
    private var model: CardModel? = null

    /** 열어 둔 묶음 상세. null 이면 요약. */
    private var openGroup: ItemGroup? = null

    /** 지금 뼈대가 상세를 담고 짜였는가([applyMode] 가 정한다). */
    private var layoutOpen = false

    /** "이번 주행"을 펼쳤는가. */
    private var tripOpen = false

    /** "지우기 / 취소" 를 펼친 항목. 카드를 다시 읽어 그 항목이 사라지면 접는다. */
    private var confirmingId: Long? = null

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

    /** 폭과 열린 묶음에 따라 뼈대만 다시 짠다. 같은 뷰를 옮겨 붙이므로 내용과 요약 상태가 남는다. */
    private fun applyMode(next: LayoutMode) {
        mode = next
        removeAllViews()
        listOf(page, routePlate, detailPlate, detailScroll, actions, summaryBlock).forEach { it.detach() }
        detail.detach(); detailScroll.addView(detail)
        val c = context
        val scroll = ScrollView(c).apply { isVerticalScrollBarEnabled = false }
        val open = openGroup != null
        layoutOpen = open
        if (next == LayoutMode.WIDE) {
            orientation = HORIZONTAL
            scroll.addView(page)
            addView(c.col().apply {
                setPadding(c.dp(64), c.dp(56), c.dp(48), c.dp(40))
                addView(scroll, lp(MATCH_PARENT, 0, 1f))
                addView(summaryBlock, lp().m(t = c.dp(24)))
                addView(actions, lp().m(t = c.dp(24)))
            }, lp(0, MATCH_PARENT, if (open) 46f else 58f))
            val plate = if (open) detailPlate.also {
                detailScroll.setPadding(c.dp(48), c.dp(40), c.dp(40), c.dp(40))
                it.addView(detailScroll, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
            } else routePlate
            addView(plate, lp(0, MATCH_PARENT, if (open) 54f else 42f).m(t = c.dp(24), r = c.dp(24), b = c.dp(24)))
        } else {
            orientation = VERTICAL
            scroll.addView(c.col().apply {
                setPadding(c.dp(40), c.dp(44), c.dp(40), c.dp(32))
                if (open) {
                    detailScroll.setPadding(0, 0, 0, 0)
                    detail.detach(); addView(detail)
                } else {
                    addView(page)
                    addView(routePlate, lp(MATCH_PARENT, c.dp(380)).m(t = c.dp(36)))
                }
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
        if (m.items.none { it.id != null && it.id == confirmingId }) confirmingId = null
        if (openGroup != null && m.groups.none { it.group == openGroup }) openGroup = null
        render()
        route.setModel(m)
    }

    /** 요약(왼쪽)과 상세(열려 있으면)를 다시 그리고, 열림 상태가 바뀌었으면 뼈대를 다시 짠다. */
    private fun render() {
        val m = model ?: return
        renderPage(m)
        renderDetail(m)
        if ((openGroup != null) != layoutOpen) mode?.let(::applyMode)
    }

    private fun renderPage(m: CardModel) {
        val c = context
        page.removeAllViews()
        page.addView(reminderStrip.detach())
        page.addView(c.txt(Tok.LABEL_M, Tok.SUB, tnum = true, s = m.date))
        page.addView(c.txt(Tok.HEAD_S, Tok.FG, 700, s = m.title), lp().m(t = c.dp(12)))
        // 한마디가 없을 때만 빈 카드 문구. 항목을 모두 지운 경우는 아래 안내로 간다
        if (m.utterances.isEmpty()) {
            page.addView(c.txt(Tok.BODY_M, Tok.FG, 600, s = "남긴 한마디가 없어요"), lp().m(t = c.dp(40)))
            page.addView(c.txt(Tok.BODY_S, Tok.SUB, s = "다음에는 운전 중에 말하기 버튼을 누르고 떠오르는 대로 말해 보세요."), lp().m(t = c.dp(8)))
        } else if (m.groups.isEmpty()) {
            page.addView(c.txt(Tok.BODY_M, Tok.FG, 600, s = "정리한 항목을 모두 지웠어요"), lp().m(t = c.dp(40)))
            page.addView(c.txt(Tok.BODY_S, Tok.SUB, s = "말한 원문은 지난 기록에 남아 있어요."), lp().m(t = c.dp(8)))
        } else {
            page.addView(c.txt(Tok.LABEL_M, Tok.SUB, s = "말한 걸 이렇게 정리했어요"), lp().m(t = c.dp(36), b = c.dp(4)))
            m.groups.forEach { page.addView(groupRow(it), lp().m(t = c.dp(16))) }
        }
        page.addView(c.rule(), lp(MATCH_PARENT, c.dp(1)).m(t = c.dp(40)))
        page.addView(tripRow(m))
        if (tripOpen) {
            page.addView(c.txt(Tok.BODY_S, Tok.SUB, tnum = true, s = m.sentence), lp().m(t = c.dp(4)))
            val felt = m.items.filter { it.type == ItemType.EMOTION }
            if (felt.isNotEmpty()) page.addView(c.txt(Tok.BODY_S, Tok.FG, 600, s = moodLine(felt)), lp().m(t = c.dp(20)))
            page.addView(c.txt(Tok.LABEL_M, Tok.SUB, s = "한마디 ${m.utterances.size}개에서 항목 ${m.items.size}개"), lp().m(t = c.dp(20), b = c.dp(8)))
        }
    }

    /** 묶음 한 줄: 라벨·개수(위), 대표 문장(크게), 끝에 ›. 줄 전체가 버튼이다. 열려 있으면 선택 면. */
    private fun groupRow(g: CardGroup) = context.row().apply {
        val c = context
        val selected = openGroup == g.group
        minimumHeight = c.dp(120)
        setPadding(c.dp(32), c.dp(24), c.dp(20), c.dp(24))
        val r = c.dpf(Tok.PLATE_R.toFloat())
        background = pressable(shape(if (selected) Tok.RAISED else Tok.PLATE, r), shape(Tok.RAISED, r))
        addView(c.col().apply {
            addView(c.txt(Tok.LABEL_M, Tok.SUB, 600, s = groupMeta(g)))
            addView(c.txt(Tok.TITLE_L, Tok.FG, 600, s = g.headline).apply { maxLines = 2; ellipsize = android.text.TextUtils.TruncateAt.END }, lp().m(t = c.dp(10)))
        }, lp(0, WRAP_CONTENT, 1f))
        addView(ChevronGlyph(c, Tok.SUB), lp(c.dp(36), c.dp(36)).m(l = c.dp(16)))
        contentDescription = "${g.group.label} 자세히"
        setOnClickListener { openGroup(if (selected && mode == LayoutMode.WIDE) null else g.group) }
        pressScale()
    }

    /** "감정 · ↓ ↑ ↓", "일 · 3개 중 1개 했어", "아이디어 · 2개". 감정은 글리프·색으로 흐름을 함께 보인다. */
    private fun groupMeta(g: CardGroup): CharSequence = SpannableStringBuilder(g.group.label).apply {
        append("  ·  ")
        when (g.group) {
            ItemGroup.EMOTION -> g.items.forEachIndexed { i, it ->
                if (i > 0) append(" ")
                val s = length; append(CardFormat.emotionGlyph(it.item.emotion))
                setSpan(ForegroundColorSpan(Tok.emotionColor(it.item.emotion)), s, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            ItemGroup.WORK -> {
                val done = g.items.count { it.item.done }
                append(if (done > 0) "${g.items.size}개 중 ${done}개 했어" else "${g.items.size}개")
            }
            ItemGroup.IDEA -> append("${g.items.size}개")
        }
    }

    private fun tripRow(m: CardModel) = context.row().apply {
        val c = context
        minimumHeight = c.dp(Tok.BTN_H)
        val summary = "${m.startLabel} → ${m.endLabel} · ${m.durationMin}분 · ${CardModelBuilder.km(m.distanceKm)}km"
        addView(c.txt(Tok.BODY_S, Tok.FG, 600, s = "이번 주행"), lp(WRAP_CONTENT))
        addView(c.txt(Tok.BODY_S, Tok.SUB, tnum = true, s = summary), lp(0, WRAP_CONTENT, 1f).m(l = c.dp(20)))
        addView(ChevronGlyph(c, Tok.SUB, down = !tripOpen, up = tripOpen), lp(c.dp(32), c.dp(32)))
        contentDescription = if (tripOpen) "이번 주행 접기" else "이번 주행 펼치기"
        setOnClickListener {
            tripOpen = !tripOpen
            trace("trip ${if (tripOpen) "open" else "close"}")
            render()
        }
    }

    /** 묶음 상세: 탭 3개 + 요약으로, 묶음 제목·대표 문장, 항목 목록. */
    private fun renderDetail(m: CardModel) {
        val c = context
        detail.removeAllViews()
        val g = m.groups.firstOrNull { it.group == openGroup } ?: return
        detail.addView(c.row().apply {
            ItemGroup.entries.forEach { e ->
                val has = m.groups.any { it.group == e }
                val b = c.button(e.label, if (e == g.group) BtnKind.PRIMARY else BtnKind.SECONDARY, padH = 28) { if (has) openGroup(e) }
                b.contentDescription = "${e.label} 보기"
                b.isEnabled = has
                b.alpha = if (has) 1f else 0.4f
                addView(b, lp(WRAP_CONTENT).m(r = c.dp(12)))
            }
            addView(View(c), lp(0, 1, 1f))
            addView(c.button("요약으로", BtnKind.TEXT, BackGlyph(c, Tok.FG), padH = 24) { openGroup(null) }, lp(WRAP_CONTENT))
        })
        detail.addView(c.txt(Tok.HEAD_S, Tok.FG, 700, s = g.group.label), lp().m(t = c.dp(36)))
        detail.addView(c.txt(Tok.BODY_L, Tok.SUB, 500, s = g.headline), lp().m(t = c.dp(10)))
        detail.addView(c.rule(), lp(MATCH_PARENT, c.dp(1)).m(t = c.dp(28)))
        g.items.forEach { gi ->
            detail.addView(detailRow(g.group, gi))
            detail.addView(c.rule(), lp(MATCH_PARENT, c.dp(1)))
        }
    }

    private fun openGroup(g: ItemGroup?) {
        if (g == openGroup) return
        openGroup = g
        confirmingId = null
        detailScroll.scrollTo(0, 0)
        trace(if (g == null) "group close" else "group open ${g.name}")
        render()
    }

    /** 상세 한 줄: 앞 표시(일 = 체크 상자, 감정 = 글리프, 아이디어 = 점) · 글 · 시각·장소·원문 · ⋯. */
    private fun detailRow(group: ItemGroup, gi: CardGroupItem) = context.row().apply {
        val c = context
        val it = gi.item
        gravity = android.view.Gravity.TOP
        setPadding(0, c.dp(20), 0, c.dp(16))
        val id = it.id
        val lead: View = when (group) {
            ItemGroup.WORK -> c.iconButton(CheckGlyph(c, it.done), if (it.done) "‘${it.text}’ 안 한 걸로" else "‘${it.text}’ 했어 표시") {
                if (id != null) onItemDone(id, !it.done)
            }.apply { isEnabled = id != null }
            ItemGroup.EMOTION -> c.txt(Tok.BODY_M, Tok.emotionColor(it.emotion), 700, s = CardFormat.emotionGlyph(it.emotion)).apply { gravity = android.view.Gravity.CENTER }
            ItemGroup.IDEA -> c.txt(Tok.BODY_M, Tok.SUB, 700, s = "•").apply { gravity = android.view.Gravity.CENTER }
        }
        addView(lead, lp(c.dp(Tok.ICON_BTN), c.dp(if (group == ItemGroup.WORK) Tok.ICON_BTN else 44)).m(r = c.dp(12)))
        addView(c.col().apply {
            addView(c.txt(Tok.BODY_M, if (it.done) Tok.SUB else Tok.FG, 500, s = it.text).apply {
                if (it.done) paintFlags = paintFlags or android.graphics.Paint.STRIKE_THRU_TEXT_FLAG
            }, lp().m(t = c.dp(if (group == ItemGroup.WORK) 18 else 4)))
            val meta = listOfNotNull(gi.time, gi.place, if (it.type == ItemType.RAW) "원문" else null).joinToString(" · ")
            addView(c.txt(Tok.LABEL_S, Tok.SUB, tnum = true, s = meta), lp().m(t = c.dp(8)))
            if (gi.quote.isNotBlank() && gi.quote != it.text) addView(c.txt(Tok.LABEL_S, Tok.SUB, s = "“${quote(gi.quote)}”"), lp().m(t = c.dp(4)))
        }, lp(0, WRAP_CONTENT, 1f))
        if (id == null) return@apply // 구조화 전 원문 한 줄은 아직 지울 행이 없다
        if (confirmingId == id) {
            // 지우기는 바로 접는다(두 번째 탭이 같은 요청을 또 보내지 않게). 목록은 저장 뒤 refreshCard 로 다시 읽는다
            addView(c.col().apply {
                addView(c.button("지우기", BtnKind.DANGER, padH = 28) { confirmingId = null; onDeleteItem(id); render() }
                    .apply { contentDescription = "‘${it.text}’ 지우기" })
                val cancel = c.button("취소", BtnKind.SECONDARY, padH = 28) { confirmingId = null; render() }
                    .apply { contentDescription = "지우기 취소" }
                addView(cancel, lp().m(t = c.dp(8)))
                cancel.post { cancel.sendAccessibilityEvent(android.view.accessibility.AccessibilityEvent.TYPE_VIEW_FOCUSED) } // 다시 그린 뒤 초점을 취소에
            }, lp(WRAP_CONTENT).m(l = c.dp(12)))
        } else {
            addView(c.iconButton(MoreGlyph(c, Tok.SUB), "‘${it.text}’ 지울지 묻기") { confirmingId = id; render() },
                lp(c.dp(Tok.ICON_BTN), c.dp(Tok.ICON_BTN)).m(l = c.dp(8)))
        }
    }

    /** 원문 인용은 앞 24자만(TRD §8-11). 긴 혼잣말 전체를 항목마다 되풀이하지 않는다. */
    private fun quote(q: String) = if (q.length <= QUOTE_MAX) q else q.take(QUOTE_MAX).trimEnd() + "…"

    /**
     * 읽을 카드가 없을 때(전체 삭제 뒤 같은 세션 카드를 다시 열면 세션 행이 없다). 지난 내용과 지우기 버튼을 남기지 않는다.
     */
    fun clearModel() {
        model = null
        confirmingId = null
        openGroup = null
        tripOpen = false
        page.removeAllViews()
        detail.removeAllViews()
        page.addView(context.txt(Tok.BODY_M, Tok.FG, 600, s = "기록을 모두 지웠어요"))
        page.addView(context.txt(Tok.BODY_S, Tok.SUB, s = "다음 주행부터 다시 쌓여요."), lp().m(t = context.dp(8)))
        route.clear()
        mode?.let(::applyMode)
    }

    /** 카드가 가려지면(주행 재개·설정 열기) 펼쳐 둔 "지우기 / 취소"와 상세를 접는다 — 다시 보일 때 요약부터 보이게. */
    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        if (changedView === this && visibility != VISIBLE && (confirmingId != null || openGroup != null || tripOpen)) {
            confirmingId = null
            openGroup = null
            tripOpen = false
            render()
        }
    }
    /**
     * 지난 기록(S2)에서 연 카드면 [onBack] 을 준다 — 아래 줄이 "지난 기록으로"·"폰에서 열기" 가 되고 요약 자막도 뜨지 않는다.
     * null 이면 평소 카드(도착·Gleo)로 돌아간다.
     */
    fun setBrowsing(onBack: (() -> Unit)?) {
        onBrowseBack = onBack
        val browsing = onBack != null
        back.visibility = if (browsing) VISIBLE else GONE
        listOf(primary, history, settings).forEach { it.visibility = if (browsing) GONE else VISIBLE }
        if (browsing) summaryBlock.visibility = GONE
    }

    /**
     * 지난 주행에서 말하고 아직 안 한 할 일(F15). 항목마다 "했어"(88dp) — 누르면 띠에서 바로 빼고 [onReminderDone] 으로 끝낸 것으로 저장한다.
     * [nowMillis] 는 "어제 퇴근길" 같은 상대 날짜를 셀 기준 시각.
     */
    fun setReminders(list: List<ReminderCandidate>, nowMillis: Long) {
        val c = context
        reminderStrip.removeAllViews()
        reminderStrip.visibility = if (list.isEmpty()) GONE else VISIBLE
        if (list.isEmpty()) return
        reminderStrip.addView(c.txt(Tok.LABEL_M, Tok.SUB, s = "지난번에 말한 할 일"))
        list.forEach { r ->
            reminderStrip.addView(c.row().apply {
                minimumHeight = c.dp(80)
                addView(c.col().apply {
                    addView(c.txt(Tok.BODY_M, Tok.FG, 500, s = ArrivalReminder.itemText(r.text)))
                    val whenText = "${ArrivalReminder.relativeDay(r.spokenAt, nowMillis)} ${CardFormat.segmentName(r.segment)}"
                    addView(c.txt(Tok.LABEL_S, Tok.SUB, s = whenText), lp().m(t = c.dp(6)))
                }, lp(0, WRAP_CONTENT, 1f))
                addView(c.button("했어", BtnKind.SECONDARY, padH = 28) {
                    onReminderDone(r.itemId)
                    setReminders(list.filter { it.itemId != r.itemId }, nowMillis)
                }.apply { contentDescription = "‘${ArrivalReminder.itemText(r.text)}’ 했어" }, lp(WRAP_CONTENT).m(l = c.dp(16)))
            }, lp().m(t = c.dp(12)))
        }
        reminderStrip.addView(c.rule(), lp(MATCH_PARENT, c.dp(1)).m(t = c.dp(24), b = c.dp(32)))
    }

    /** 요약 재생 중이면 자막과 "건너뛰기" 를 보이고, null 이면 숨긴다(PRD §4-2). 재생 중에는 주 버튼이 "다시 듣기" 다. */
    fun setSummary(text: String?) {
        summaryBlock.visibility = if (text == null || onBrowseBack != null) GONE else VISIBLE
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

        fun clear() {
            model = null
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
                if (u.items.isEmpty()) return@forEach // 항목을 모두 지운 한마디는 감정이 없으므로 핀도 없다
                val near = idx.indices.minBy { TrackMath.distanceMeters(m.route[idx[it]], at) }
                val (x, y) = pts[near]
                val r = 16 * d
                fill.color = Tok.PLATE; c.drawCircle(x, y, r + 5 * d, fill)
                // 원문뿐인 한마디는 감정이 없으므로 무채색 핀
                val moods = CardFormat.pinEmotions(u.items.filter { it.type != ItemType.RAW }).map(Tok::emotionColor)
                val o = RectF(x - r, y - r, x + r, y + r)
                if (moods.size >= 2) {
                    fill.color = moods[0]; c.drawArc(o, 135f, 180f, true, fill)
                    fill.color = moods[1]; c.drawArc(o, -45f, 180f, true, fill)
                } else {
                    fill.color = moods.firstOrNull() ?: Tok.SUB; c.drawCircle(x, y, r, fill)
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

    private companion object {
        const val QUOTE_MAX = 24
    }
}
