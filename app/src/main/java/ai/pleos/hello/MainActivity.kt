package ai.pleos.hello

import ai.pleos.hello.core.CardFormat
import ai.pleos.hello.core.CardModel
import ai.pleos.hello.core.QrPayload
import ai.pleos.hello.core.Screen
import ai.pleos.hello.core.ScreenGate
import ai.pleos.hello.core.SessionEvent
import ai.pleos.hello.core.SessionState
import ai.pleos.hello.data.RoomPleoryStore
import ai.pleos.hello.gleo.GleoActionReceiver
import ai.pleos.hello.platform.Gear
import ai.pleos.hello.ui.ConsentView
import ai.pleos.hello.ui.QrView
import ai.pleos.hello.ui.S0DriveView
import ai.pleos.hello.ui.S1CardView
import ai.pleos.hello.ui.S2HistoryView
import ai.pleos.hello.ui.SettingsView
import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.FrameLayout
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * S0 주행 화면과 S1 카드를 겹쳐 두고 컨트롤러 상태(CARD 인지)로 하나만 보인다.
 * S0 탭은 TalkTapped 로 넘기고, 듣는 중 표시는 컨트롤러 상태를 따라간다.
 */
class MainActivity : Activity() {

    private val app get() = application as PleoryApp
    private lateinit var view: S0DriveView
    private lateinit var card: S1CardView
    private lateinit var consentView: ConsentView
    private lateinit var settingsView: SettingsView
    private lateinit var historyView: S2HistoryView
    private lateinit var qrView: QrView

    /** 지금 카드에 보이는 내용(QR 평문의 원본). 카드를 다시 읽을 때마다 바뀐다. */
    private var cardModel: CardModel? = null

    /** "폰에서 열기" QR 모달(F10). 카드 위에만 뜨고, 게이트가 닫히거나 카드가 내려가면 닫는다. */
    private var qrOpen = false
    private val historyStore by lazy { RoomPleoryStore(app.db) }
    private var historyLoad: Job? = null
    private val uiScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var cardLoad: Job? = null

    /** 지금 카드 자리에 보이는 것: 지난 기록에서 연 세션 id, 평소 카드면 [LIVE_CARD], 카드가 안 보이면 null. 바뀌면 다시 읽는다. */
    private var shownCard: Long? = null
    private var lastState = SessionState.OFF
    private var settingsOpen = false
    private var consentRequested = false

    /** S2 지난 기록(F11). [historySession] 은 그 안에서 연 지난 카드 — 뒤로 가면 목록으로 돌아간다. */
    private var historyOpen = false
    private var historySession: Long? = null
    private var historyTodoOnly = false

    /** Gleo SHOW_TODAY_CARD 로 연 카드. 도착 전이면 지금 세션, 닫힌 뒤면 마지막 세션의 카드다. 게이트가 닫히면 내린다. */
    private var cardRequested = false

    private val stateListener: (SessionState) -> Unit = { runOnUiThread { render(it) } }

    private val outputsUi = object : PleoryApp.AppOutputs.Ui {
        override fun refreshCard() { runOnUiThread { if (shownCard != null) loadCard() } }
        override fun summaryStarted(text: String) { runOnUiThread { card.setSummary(text) } }
        override fun summaryEnded() { runOnUiThread { card.setSummary(null) } }
    }
    private var permissionAsked = false

    private val gateListener: () -> Unit = { runOnUiThread { render(lastState) } }

    private val gearListener: (Gear) -> Unit = { if (it == Gear.P) requestPermissionsIfParked() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        view = S0DriveView(this, { app.controller.post(SessionEvent.TalkTapped) }, ::openSettings, ::openHistory)
        card = S1CardView(
            this,
            onReplay = { app.controller.replaySummary() },
            onSkip = { app.controller.skipSummary() },
            onQr = ::openQr,
            onSettings = ::openSettings,
            // 카드는 주차·제한 없음일 때만 보이지만(ScreenGate), 탭과 기어 변경이 엇갈려도 주행 중에는 지우지 않는다
            onDeleteItem = { id -> if (app.gateAllowed()) app.controller.deleteItem(id) },
            onHistory = ::openHistory,
            onReminderDone = { id -> if (app.gateAllowed()) app.controller.markReminderDone(id) },
            onItemDone = { id, done -> if (app.gateAllowed()) app.controller.setItemDone(id, done) },
            trace = { Log.i("PleoryFlow", it) },
        ).apply { visibility = View.GONE }
        historyView = S2HistoryView(
            this,
            onBack = ::closeHistory,
            onOpen = ::openHistoryCard,
            onFilter = { historyTodoOnly = it; loadHistory() },
        ).apply { visibility = View.GONE }
        qrView = QrView(this, onClose = ::closeQr).apply { visibility = View.GONE }
        // 동의는 두 버튼만 인정한다. 뒤로 가기·자동 수락은 동의로 치지 않는다.
        consentView = ConsentView(this, onAgree = { answer { it.grant(app.clock.nowMillis()) }; app.warmLlm() }, onDecline = { answer { it.decline(app.clock.nowMillis()) } })
            .apply { visibility = View.GONE }
        settingsView = SettingsView(
            this,
            onWithdraw = { app.consent.withdraw(app.clock.nowMillis()); Log.i(PleoryApp.FLOW_TAG, "consent withdrawn"); settingsView.bind(app.consent.effective, app.consent.at); render(lastState) },
            onConsent = { consentRequested = true; render(lastState) },
            onDeleteAll = { app.controller.deleteAllData { runOnUiThread { settingsView.showDeleted() } } },
            onClose = { closeSettings() },
        ).apply { visibility = View.GONE }
        setContentView(FrameLayout(this).apply {
            addView(view)
            addView(card)
            addView(historyView)
            addView(qrView)
            addView(settingsView)
            addView(consentView)
        })
        handleGleo(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleGleo(intent)
    }

    /**
     * Gleo SHOW_TODAY_CARD(component=activity). 주차 중이면 카드를 열고 success, 주행 중이면 화면을 바꾸지 않고 fail 로 회신한다.
     * 기어는 콜드 스타트 직후 아직 모를 수 있어 현재 기어를 한 번 읽은 뒤(직렬 경로) 판단한다.
     */
    private fun handleGleo(intent: Intent?) {
        if (!GleoActionReceiver.isShowTodayCard(intent?.action, packageName)) return
        app.sdkHub.gear.current {
            runOnUiThread {
                val parked = app.gateAllowed()
                val (status, message) = GleoActionReceiver.showTodayCardReply(parked)
                Log.i(PleoryApp.FLOW_TAG, "gleo ${GleoActionReceiver.FN_SHOW_TODAY_CARD} parked=$parked")
                if (parked) {
                    settingsOpen = false
                    historyOpen = false
                    historySession = null
                    cardRequested = true
                    render(lastState)
                }
                GleoActionReceiver.respond(this, GleoActionReceiver.FN_SHOW_TODAY_CARD, status, message)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        app.controller.onStateChanged = stateListener
        app.outputs.ui = outputsUi
        app.onGateChanged = gateListener
        render(app.controller.state)
        card.setSummary(app.controller.summaryText)
        app.sdkHub.gear.addListener(gearListener)
        app.sdkHub.gear.current { if (it == Gear.P) requestPermissionsIfParked() }
    }

    override fun onStop() {
        app.sdkHub.gear.removeListener(gearListener)
        // 다른 인스턴스가 이미 등록했다면(이전 Activity 의 onStop 이 늦게 오는 경우) 건드리지 않는다.
        if (app.controller.onStateChanged === stateListener) app.controller.onStateChanged = null
        if (app.outputs.ui === outputsUi) app.outputs.ui = null
        if (app.onGateChanged === gateListener) app.onGateChanged = null
        super.onStop()
    }

    override fun onDestroy() {
        uiScope.cancel()
        super.onDestroy()
    }

    /** 컨트롤러 상태와 화면 게이트(주차·제한 없음)·동의·설정 열림을 합쳐 보일 화면 하나를 고른다. */
    private fun render(state: SessionState) {
        lastState = state
        val allowed = app.gateAllowed()
        if (!allowed) {
            settingsOpen = false
            consentRequested = false
            cardRequested = false
            historyOpen = false // 주행 중에는 지난 기록을 닫는다(F11)
            historySession = null
        }
        val screen = ScreenGate.resolve(allowed, app.consent.needsPrompt || consentRequested, settingsOpen, state, cardRequested, historyOpen)
        // 지금 세션 카드가 안 보이면(동의·설정·지난 기록) 요약을 막는다. 읽는 중이면 멈추고, 못 읽은 요약은 카드로 돌아오면 한 번 읽는다
        app.controller.holdSummary(state == SessionState.CARD && screen != Screen.CARD)
        val browsing = if (screen == Screen.HISTORY) historySession else null
        val cardNow = browsing ?: if (screen == Screen.CARD) LIVE_CARD else null
        view.visibility = if (screen == Screen.S0) View.VISIBLE else View.GONE
        card.visibility = if (cardNow != null) View.VISIBLE else View.GONE
        historyView.visibility = if (screen == Screen.HISTORY && browsing == null) View.VISIBLE else View.GONE
        consentView.visibility = if (screen == Screen.CONSENT) View.VISIBLE else View.GONE
        settingsView.visibility = if (screen == Screen.SETTINGS) View.VISIBLE else View.GONE
        // QR 은 보이는 카드 위에서만. 주행이 시작되거나(게이트) 카드가 바뀌거나 내려가면 닫는다
        if (qrOpen && (cardNow == null || cardNow != shownCard)) closeQrSilently("gate=$allowed screen=$screen")
        qrView.visibility = if (qrOpen) View.VISIBLE else View.GONE
        view.setListening(state == SessionState.LISTENING)
        view.setSegmentName(CardFormat.segmentName(app.sessionSegment))
        view.setSettingsVisible(allowed)
        if (cardNow != null && cardNow != shownCard) {
            // 지난 기록에서 연 카드는 요약을 자동으로 읽지 않는다(F11) — 요약은 지금 세션 것이다
            card.setBrowsing(if (browsing != null) ::closeHistoryCard else null)
            loadCard()
        }
        shownCard = cardNow
    }

    /** S1 의 "폰에서 열기". 주차·제한 없음이고 카드 내용을 읽은 뒤에만 연다. */
    private fun openQr() {
        val m = cardModel
        if (!app.gateAllowed() || m == null || shownCard == null) return
        val p = QrPayload.build(m)
        Log.i(PleoryApp.FLOW_TAG, "qr open bytes=${p.bytes} items=${p.items} truncated=${p.truncated} dropped=${p.dropped}")
        qrView.setPayload(p)
        qrOpen = true
        render(lastState)
    }

    private fun closeQr() {
        qrOpen = false
        Log.i(PleoryApp.FLOW_TAG, "qr close")
        render(lastState)
    }

    private fun closeQrSilently(reason: String) {
        qrOpen = false
        Log.i(PleoryApp.FLOW_TAG, "qr close auto $reason")
    }

    private fun answer(record: (ai.pleos.hello.data.ConsentPrefs) -> Unit) {
        record(app.consent)
        consentRequested = false
        Log.i(PleoryApp.FLOW_TAG, "consent ${app.consent.state}")
        if (settingsOpen) settingsView.bind(app.consent.effective, app.consent.at)
        render(lastState)
    }

    private fun openSettings() {
        if (!app.gateAllowed()) return
        settingsOpen = true
        settingsView.bind(app.consent.effective, app.consent.at)
        render(lastState)
    }

    private fun closeSettings() {
        settingsOpen = false
        render(lastState)
    }

    /** S0·S1 의 "지난 기록". 주차·제한 없음일 때만 연다. */
    private fun openHistory() {
        if (!app.gateAllowed()) return
        historyOpen = true
        historySession = null
        render(lastState)
        loadHistory()
    }

    private fun closeHistory() {
        historyOpen = false
        historySession = null
        historyLoad?.cancel()
        render(lastState)
    }

    private fun openHistoryCard(sessionId: Long) {
        if (!app.gateAllowed()) return
        Log.i(PleoryApp.FLOW_TAG, "history open card session=$sessionId")
        historySession = sessionId
        render(lastState)
    }

    /** 지난 카드에서 목록으로. 그 카드에서 항목을 지웠을 수 있으니 목록을 다시 읽는다. */
    private fun closeHistoryCard() {
        historySession = null
        render(lastState)
        loadHistory()
    }

    private fun loadHistory() {
        historyLoad?.cancel()
        val todoOnly = historyTodoOnly
        historyLoad = uiScope.launch {
            val rows = historyStore.history(app.profileId(), app.clock.nowMillis(), todoOnly)
            Log.i(PleoryApp.FLOW_TAG, "history rows=${rows.size} todoOnly=$todoOnly")
            historyView.setRows(rows, todoOnly)
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        when {
            qrOpen -> closeQr()
            settingsOpen -> closeSettings()
            historySession != null -> closeHistoryCard()
            historyOpen -> closeHistory()
            cardRequested && lastState != SessionState.CARD -> { cardRequested = false; render(lastState) }
            else -> super.onBackPressed()
        }
    }

    /** 카드 내용을 DB 에서 다시 읽는다. 종료값 저장이 끝난 뒤에 읽는다(컨트롤러가 보장). */
    private fun loadCard() {
        cardLoad?.cancel()
        cardModel = null // 읽는 동안 QR 이 지난 카드 내용을 담지 않게
        val requested = cardRequested && lastState != SessionState.CARD
        val past = historySession
        cardLoad = uiScope.launch {
            val m = when {
                past != null -> historyStore.loadCard(past)
                requested -> app.controller.loadTodayCard()
                else -> app.controller.loadCard()
            }
            // 리마인드 띠(F15)는 도착 카드에만. Gleo 나 지난 기록으로 연 카드에는 붙이지 않는다
            card.setReminders(if (requested || past != null) emptyList() else app.controller.loadReminders(), app.clock.nowMillis())
            cardModel = m
            if (m != null) card.setModel(m) else card.clearModel() // 전체 삭제 뒤에는 세션 행이 없다
            if (qrOpen && m != null) qrView.setPayload(QrPayload.build(m)) // 열린 QR 도 다시 읽은 내용으로
        }
    }

    /** 권한 팝업은 주차(P) 중에만, 한 번만 띄운다. */
    private fun requestPermissionsIfParked() {
        val missing = (arrayOf(Manifest.permission.RECORD_AUDIO) + PleoryApp.LOCATION_PERMISSIONS)
            .filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isEmpty() || permissionAsked) return
        permissionAsked = true
        requestPermissions(missing.toTypedArray(), REQUEST_PERMISSIONS)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        app.startLocationIfPermitted()
    }

    private companion object {
        const val REQUEST_PERMISSIONS = 1
        const val LIVE_CARD = -1L
    }
}
