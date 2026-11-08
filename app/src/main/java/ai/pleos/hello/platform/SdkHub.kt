package ai.pleos.hello.platform

import ai.pleos.playground.vehicle.Vehicle
import android.car.Car
import android.car.drivingstate.CarUxRestrictionsManager
import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class SdkState { READY, FAILED }

/** Pleos SDK 수명을 Application 범위에서 소유한다(D10). */
class SdkHub(private val context: Context) {

    private val _vehicle = MutableStateFlow(SdkState.FAILED)
    private val _uxRestrictions = MutableStateFlow(SdkState.FAILED)
    val vehicleState: StateFlow<SdkState> = _vehicle
    val uxRestrictionsState: StateFlow<SdkState> = _uxRestrictions

    private var vehicle: Vehicle? = null
    private var car: Car? = null
    var uxManager: CarUxRestrictionsManager? = null
        private set

    fun start() {
        try {
            vehicle = Vehicle(context).also { it.initialize() }
            _vehicle.value = SdkState.READY
            Log.i(TAG, "vehicle initialized")
        } catch (e: Exception) {
            Log.w(TAG, "vehicle init failed: $e")
        }
        try {
            val c = Car.createCar(context)
            car = c
            val m = c.getCarManager(Car.CAR_UX_RESTRICTION_SERVICE) as CarUxRestrictionsManager
            uxManager = m
            _uxRestrictions.value = SdkState.READY
            Log.i(TAG, "car ux manager acquired: ${m.currentCarUxRestrictions}")
        } catch (e: Exception) {
            Log.w(TAG, "car ux manager failed: $e")
        }
    }

    fun release() {
        runCatching { vehicle?.release() }
        runCatching { car?.disconnect() }
        vehicle = null
        car = null
        uxManager = null
        _vehicle.value = SdkState.FAILED
        _uxRestrictions.value = SdkState.FAILED
    }

    private companion object {
        const val TAG = "PleoryHub"
    }
}
