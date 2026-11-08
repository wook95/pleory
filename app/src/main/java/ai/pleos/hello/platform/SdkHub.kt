package ai.pleos.hello.platform

import ai.pleos.playground.vehicle.Vehicle
import android.car.Car
import android.car.drivingstate.CarUxRestrictionsManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class SdkState { PENDING, READY, FAILED }

/**
 * Pleos SDK 수명을 Application 범위에서 소유한다(D10).
 * 모든 SDK 콜백은 [handler](메인 루퍼)로 다시 넘겨서 직렬로 처리한다(D3).
 */
class SdkHub(private val context: Context) {

    val handler = Handler(Looper.getMainLooper())
    private val post: Post = { handler.post(it) }

    private val _vehicle = MutableStateFlow(SdkState.PENDING)
    private val _uxRestrictions = MutableStateFlow(SdkState.PENDING)
    private val _navi = MutableStateFlow(SdkState.PENDING)
    private val _location = MutableStateFlow(SdkState.PENDING)
    private val _stt = MutableStateFlow(SdkState.PENDING)
    private val _tts = MutableStateFlow(SdkState.PENDING)
    val vehicleState: StateFlow<SdkState> = _vehicle
    val uxRestrictionsState: StateFlow<SdkState> = _uxRestrictions
    val naviState: StateFlow<SdkState> = _navi
    val locationState: StateFlow<SdkState> = _location
    val sttState: StateFlow<SdkState> = _stt
    val ttsState: StateFlow<SdkState> = _tts

    private val pleosGear = PleosGearSource(post)
    private val fakeableGear = FakeableGearSource(pleosGear)
    private val pleosNavi = PleosNaviSource(context, post)
    private val pleosLocation = PleosLocationSource(context, post)
    private val pleosSpeechInput = PleosSpeechInput(context, post)
    private val pleosSpeechOutput = PleosSpeechOutput(context, post)
    private val carUx = CarUxRestrictionsSource(post)

    val gear: GearSource = fakeableGear
    val navi: NaviSource = pleosNavi
    val location: LocationSource = pleosLocation
    val speechInput: SpeechInput = pleosSpeechInput
    val speechOutput: SpeechOutput = pleosSpeechOutput
    val uxRestrictions: UxRestrictions = carUx

    private var vehicle: Vehicle? = null
    private var car: Car? = null
    var uxManager: CarUxRestrictionsManager? = null
        private set

    /** 디버그 전용: 가짜 기어를 내보낸다. null 이면 실제 기어로 돌아간다. */
    fun setGearOverride(g: Gear?) {
        handler.post { fakeableGear.setOverride(g) }
    }

    fun start() {
        try {
            vehicle = Vehicle(context).also {
                it.initialize()
                pleosGear.attach(it)
            }
            _vehicle.value = SdkState.READY
            Log.i(TAG, "vehicle initialized")
        } catch (e: Exception) {
            _vehicle.value = SdkState.FAILED
            Log.w(TAG, "vehicle init failed: $e")
        }
        try {
            val c = Car.createCar(context)
            car = c
            val m = c.getCarManager(Car.CAR_UX_RESTRICTION_SERVICE) as CarUxRestrictionsManager
            uxManager = m
            carUx.attach(m)
            _uxRestrictions.value = SdkState.READY
            Log.i(TAG, "car ux manager acquired: ${m.currentCarUxRestrictions}")
        } catch (e: Exception) {
            _uxRestrictions.value = SdkState.FAILED
            Log.w(TAG, "car ux manager failed: $e")
        }
        _location.value = pleosLocation.init()
        Log.i(TAG, "location ${_location.value}")
        pleosNavi.init { _navi.value = it; Log.i(TAG, "navi $it") }
        pleosSpeechInput.init { _stt.value = it; Log.i(TAG, "stt $it") }
        pleosSpeechOutput.init { _tts.value = it; Log.i(TAG, "tts $it") }
    }

    fun release() {
        pleosGear.detach()
        carUx.detach()
        pleosNavi.release()
        pleosLocation.release()
        pleosSpeechInput.release()
        pleosSpeechOutput.release()
        runCatching { vehicle?.release() }
        runCatching { car?.disconnect() }
        vehicle = null
        car = null
        uxManager = null
        _vehicle.value = SdkState.FAILED
        _uxRestrictions.value = SdkState.FAILED
        _navi.value = SdkState.FAILED
        _location.value = SdkState.FAILED
        _stt.value = SdkState.FAILED
        _tts.value = SdkState.FAILED
    }

    private companion object {
        const val TAG = "PleoryPlatform"
    }
}
