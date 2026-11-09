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
    private val fakeableNavi = FakeableNaviSource(pleosNavi)
    private val pleosLocation = PleosLocationSource(context, post)
    private val pleosSpeechInput = PleosSpeechInput(context, post)
    private val pleosSpeechOutput = PleosSpeechOutput(context, post)
    private val carUx = CarUxRestrictionsSource(post)

    val gear: GearSource = fakeableGear
    val navi: NaviSource = fakeableNavi
    val location: LocationSource = pleosLocation
    val speechInput: SpeechInput = pleosSpeechInput
    val speechOutput: SpeechOutput = pleosSpeechOutput
    val uxRestrictions: UxRestrictions = carUx

    private var vehicle: Vehicle? = null
    private var car: Car? = null

    /** 디버그 전용: 가짜 기어를 내보낸다. null 이면 실제 기어로 돌아간다. */
    fun setGearOverride(g: Gear?) {
        handler.post { fakeableGear.setOverride(g) }
    }

    /** 디버그 전용: UX 제한 상태를 강제한다(true=제한, false=없음, null=실제 값). */
    fun setUxOverride(required: Boolean?) {
        handler.post { carUx.setOverride(required) }
    }

    /** 디버그 전용: 가짜 목적지를 내보낸다. null 이면 실제 내비로 돌아간다. */
    fun setNaviOverride(s: DestinationState?) {
        handler.post { fakeableNavi.setOverride(s) }
    }

    /** 디버그 전용: 내비 도착 이벤트를 강제로 내보낸다. */
    fun emitFakeArrived() {
        handler.post { fakeableNavi.emitArrived() }
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
            // 수명 주기 리스너를 준다: 없으면 차량 서비스가 다시 뜰 때 Car 가 이 앱 프로세스를 죽인다
            // (에뮬레이터 부하에서 CarServiceHelper 연결 실패로 차량 서비스가 재시작되며 앱이 함께 죽었다, 2026-11-09).
            // 서비스가 돌아오면 ready=true 로 다시 불리고 UX 제한 관리자를 새로 붙인다.
            car = Car.createCar(context, handler, Car.CAR_WAIT_TIMEOUT_DO_NOT_WAIT) { c, ready ->
                if (ready) {
                    try {
                        val m = c.getCarManager(Car.CAR_UX_RESTRICTION_SERVICE) as CarUxRestrictionsManager
                        carUx.attach(m)
                        _uxRestrictions.value = SdkState.READY
                        Log.i(TAG, "car ux manager acquired: ${m.currentCarUxRestrictions}")
                    } catch (e: Exception) {
                        _uxRestrictions.value = SdkState.FAILED
                        Log.w(TAG, "car ux manager failed: $e")
                    }
                } else {
                    carUx.markDisconnected()
                    Log.w(TAG, "car service disconnected; restrictions assumed until it comes back")
                }
            }
        } catch (e: Exception) {
            _uxRestrictions.value = SdkState.FAILED
            Log.w(TAG, "car ux manager failed: $e")
        }
        // 위치는 권한을 받은 뒤 initLocation() 에서 초기화한다(권한 전 initialize 는 콜백이 오지 않는다)
        pleosNavi.init { _navi.value = it; Log.i(TAG, "navi $it") }
        pleosSpeechInput.init { _stt.value = it; Log.i(TAG, "stt $it") }
        pleosSpeechOutput.init { _tts.value = it; Log.i(TAG, "tts $it") }
    }

    /** 위치 권한이 있을 때 한 번 초기화하고, 준비되면 구독을 시작한다. 직렬 경로에서 호출한다. */
    fun initLocation() {
        if (_location.value != SdkState.READY) {
            _location.value = pleosLocation.init()
            Log.i(TAG, "location ${_location.value}")
        }
        if (_location.value == SdkState.READY) pleosLocation.start()
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
