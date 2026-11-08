package ai.pleos.hello

import ai.pleos.hello.core.SessionEvent
import ai.pleos.hello.core.SessionState
import ai.pleos.hello.platform.Gear
import ai.pleos.hello.ui.S0DriveView
import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Bundle

/** S0 주행 화면. 탭은 TalkTapped 로 컨트롤러에 넘기고, 듣는 중 표시는 컨트롤러 상태를 따라간다. */
class MainActivity : Activity() {

    private val app get() = application as PleoryApp
    private lateinit var view: S0DriveView
    private var permissionAsked = false

    private val gearListener: (Gear) -> Unit = { if (it == Gear.P) requestPermissionsIfParked() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        view = S0DriveView(this) { app.controller.post(SessionEvent.TalkTapped) }
        setContentView(view)
    }

    override fun onStart() {
        super.onStart()
        app.controller.onStateChanged = { runOnUiThread { render(it) } }
        render(app.controller.state)
        app.sdkHub.gear.addListener(gearListener)
        app.sdkHub.gear.current { if (it == Gear.P) requestPermissionsIfParked() }
    }

    override fun onStop() {
        app.sdkHub.gear.removeListener(gearListener)
        app.controller.onStateChanged = null
        super.onStop()
    }

    private fun render(state: SessionState) = view.setListening(state == SessionState.LISTENING)

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
