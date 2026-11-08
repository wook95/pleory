package ai.pleos.hello

import ai.pleos.hello.core.SessionEvent
import ai.pleos.hello.core.SessionState
import ai.pleos.hello.platform.Gear
import ai.pleos.hello.ui.S0DriveView
import ai.pleos.hello.ui.S1CardView
import android.Manifest
import android.app.Activity
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
    private val uiScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var cardLoad: Job? = null
    private var cardShown = false

    private val stateListener: (SessionState) -> Unit = { runOnUiThread { render(it) } }

    private val outputsUi = object : PleoryApp.AppOutputs.Ui {
        override fun refreshCard() { runOnUiThread { if (cardShown) loadCard() } }
        override fun summaryStarted(text: String) { runOnUiThread { card.setSummary(text) } }
        override fun summaryEnded() { runOnUiThread { card.setSummary(null) } }
    }
    private var permissionAsked = false

    private val gearListener: (Gear) -> Unit = { if (it == Gear.P) requestPermissionsIfParked() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        view = S0DriveView(this) { app.controller.post(SessionEvent.TalkTapped) }
        card = S1CardView(
            this,
            onReplay = { app.controller.replaySummary() },
            onSkip = { app.controller.skipSummary() },
            onEdit = { Log.i(PleoryApp.FLOW_TAG, "tap 수정 (P1)") },
            onQr = { Log.i(PleoryApp.FLOW_TAG, "tap 폰에서 열기 (P1)") },
        ).apply { visibility = View.GONE }
        setContentView(FrameLayout(this).apply {
            addView(view)
            addView(card)
        })
    }

    override fun onStart() {
        super.onStart()
        app.controller.onStateChanged = stateListener
        app.outputs.ui = outputsUi
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
        super.onStop()
    }

    override fun onDestroy() {
        uiScope.cancel()
        super.onDestroy()
    }

    private fun render(state: SessionState) {
        val isCard = state == SessionState.CARD
        view.visibility = if (isCard) View.GONE else View.VISIBLE
        card.visibility = if (isCard) View.VISIBLE else View.GONE
        view.setListening(state == SessionState.LISTENING)
        if (isCard && !cardShown) loadCard()
        cardShown = isCard
    }

    /** 카드 내용을 DB 에서 다시 읽는다. 종료값 저장이 끝난 뒤에 읽는다(컨트롤러가 보장). */
    private fun loadCard() {
        cardLoad?.cancel()
        cardLoad = uiScope.launch { app.controller.loadCard()?.let { card.setModel(it) } }
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
