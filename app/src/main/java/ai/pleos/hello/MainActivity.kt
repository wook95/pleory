package ai.pleos.hello

import ai.pleos.hello.core.CardFormat
import ai.pleos.hello.core.ConsentState
import ai.pleos.hello.core.Screen
import ai.pleos.hello.core.ScreenGate
import ai.pleos.hello.core.SessionEvent
import ai.pleos.hello.core.SessionState
import ai.pleos.hello.gleo.GleoActionReceiver
import ai.pleos.hello.platform.Gear
import ai.pleos.hello.ui.ConsentView
import ai.pleos.hello.ui.S0DriveView
import ai.pleos.hello.ui.S1CardView
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
    private val uiScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var cardLoad: Job? = null
    private var cardShown = false
    private var lastState = SessionState.OFF
    private var settingsOpen = false
    private var consentRequested = false

    /** Gleo SHOW_TODAY_CARD 로 연 카드. 도착 전이면 지금 세션, 닫힌 뒤면 마지막 세션의 카드다. 게이트가 닫히면 내린다. */
    private var cardRequested = false

    /** 첫 주차에 동의 화면이 카드를 가려서 요약을 아직 못 들려줬다. 동의 답 뒤 카드가 뜨면 다시 읽는다. */
    private var summaryOwed = false

    private val stateListener: (SessionState) -> Unit = { runOnUiThread { render(it) } }

    private val outputsUi = object : PleoryApp.AppOutputs.Ui {
        override fun refreshCard() { runOnUiThread { if (cardShown) loadCard() } }
        override fun summaryStarted(text: String) { runOnUiThread { card.setSummary(text) } }
        override fun summaryEnded() { runOnUiThread { card.setSummary(null) } }
    }
    private var permissionAsked = false

    private val gateListener: () -> Unit = { runOnUiThread { render(lastState) } }

    private val gearListener: (Gear) -> Unit = { if (it == Gear.P) requestPermissionsIfParked() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        view = S0DriveView(this, { app.controller.post(SessionEvent.TalkTapped) }, ::openSettings)
        card = S1CardView(
            this,
            onReplay = { app.controller.replaySummary() },
            onSkip = { app.controller.skipSummary() },
            onQr = { Log.i(PleoryApp.FLOW_TAG, "tap 폰에서 열기 (P1)") },
            onSettings = ::openSettings,
            // 카드는 주차·제한 없음일 때만 보이지만(ScreenGate), 탭과 기어 변경이 엇갈려도 주행 중에는 지우지 않는다
            onDeleteItem = { id -> if (app.gateAllowed()) app.controller.deleteItem(id) },
        ).apply { visibility = View.GONE }
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
        }
        val screen = ScreenGate.resolve(allowed, app.consent.needsPrompt || consentRequested, settingsOpen, state, cardRequested)
        // 동의 화면이 카드를 가리는 동안에는 요약을 막고, 답한 뒤 카드가 보이면 한 번만 읽는다
        app.controller.holdSummary(state == SessionState.CARD && screen == Screen.CONSENT)
        if (state != SessionState.CARD) summaryOwed = false
        else if (screen == Screen.CONSENT) summaryOwed = true
        else if (screen != Screen.CARD) app.controller.skipSummary() // 카드가 닫히면 요약 음성도 멈춘다
        view.visibility = if (screen == Screen.S0) View.VISIBLE else View.GONE
        card.visibility = if (screen == Screen.CARD) View.VISIBLE else View.GONE
        consentView.visibility = if (screen == Screen.CONSENT) View.VISIBLE else View.GONE
        settingsView.visibility = if (screen == Screen.SETTINGS) View.VISIBLE else View.GONE
        view.setListening(state == SessionState.LISTENING)
        view.setSegmentName(CardFormat.segmentName(app.sessionSegment))
        view.setSettingsVisible(allowed)
        val isCard = screen == Screen.CARD
        if (isCard && !cardShown) {
            loadCard()
            if (summaryOwed) { summaryOwed = false; app.controller.replaySummary() } // 재생 중이면 컨트롤러가 무시한다
        }
        cardShown = isCard
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

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        when {
            settingsOpen -> closeSettings()
            cardRequested && lastState != SessionState.CARD -> { cardRequested = false; render(lastState) }
            else -> super.onBackPressed()
        }
    }

    /** 카드 내용을 DB 에서 다시 읽는다. 종료값 저장이 끝난 뒤에 읽는다(컨트롤러가 보장). */
    private fun loadCard() {
        cardLoad?.cancel()
        val requested = cardRequested && lastState != SessionState.CARD
        cardLoad = uiScope.launch {
            val m = if (requested) app.controller.loadTodayCard() else app.controller.loadCard()
            if (m != null) card.setModel(m) else card.clearModel() // 전체 삭제 뒤에는 세션 행이 없다
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
    }
}
