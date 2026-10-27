package ai.pleos.hello

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.area.DoorArea
import ai.pleos.playground.vehicle.constant.area.DoorValue
import android.app.Activity
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.widget.TextView

class MainActivity : Activity() {

    private lateinit var vehicle: Vehicle
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        status = TextView(this).apply {
            textSize = 32f
            gravity = Gravity.CENTER
            text = "운전석 문 위치 조회 중…"
        }
        setContentView(status)

        vehicle = Vehicle(this)
        vehicle.initialize()
        vehicle.getDoor().getCurrentDoorPosition(
            area = DoorArea(DoorValue.ROW_1_LEFT),
            onComplete = { pos: Int? -> show("운전석 문 위치: $pos") },
            onFailed = { e: Exception -> show("조회 실패: ${e.javaClass.simpleName} ${e.message}") },
        )
    }

    private fun show(message: String) {
        Log.i(TAG, message)
        runOnUiThread { status.text = message }
    }

    override fun onDestroy() {
        vehicle.release()
        super.onDestroy()
    }

    private companion object {
        const val TAG = "PleosHello"
    }
}
