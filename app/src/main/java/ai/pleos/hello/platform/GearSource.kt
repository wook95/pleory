package ai.pleos.hello.platform

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.constant.control.VehicleGear
import ai.pleos.playground.vehicle.listener.GearSelectionListener
import android.util.Log

enum class Gear { P, D, R, N, OTHER }

interface GearSource {
    /**
     * 기어 이벤트를 받을 수 있는가. Vehicle SDK 의 Gear API 에는 check*Capabilities 가 없어서(javap 확인)
     * 리스너 등록 실패나 차량 없음으로 판단한다. 첫 조회(current) 한 번의 실패는 영향을 주지 않는다 —
     * 리스너가 살아 있으면 이후 이벤트가 온다. false 면 속도·UX 기반 대체 판정을 쓴다.
     */
    val supported: Boolean
    /** 현재 기어. 조회 실패면 null. */
    fun current(onResult: (Gear?) -> Unit)
    fun addListener(l: (Gear) -> Unit)
    fun removeListener(l: (Gear) -> Unit)
}

private fun VehicleGear?.toGear(): Gear = when (this) {
    VehicleGear.GEAR_PARK -> Gear.P
    VehicleGear.GEAR_DRIVE -> Gear.D
    VehicleGear.GEAR_REVERSE -> Gear.R
    VehicleGear.GEAR_NEUTRAL -> Gear.N
    else -> Gear.OTHER
}

class PleosGearSource(private val post: Post) : GearSource {
    private val listeners = Listeners<(Gear) -> Unit>()
    private var vehicle: Vehicle? = null

    override var supported: Boolean = true
        private set

    private val sdkListener = object : GearSelectionListener {
        override fun onSelected(gear: VehicleGear?) {
            val g = gear.toGear()
            post { listeners.forEach { it(g) } }
        }

        override fun onFailed(e: Exception) {
            Log.w(TAG, "gear listener failed: $e")
        }
    }

    fun attach(v: Vehicle) {
        vehicle = v
        try {
            v.getGear().registerGearSelection(sdkListener)
        } catch (e: Exception) {
            supported = false
            Log.w(TAG, "gear unsupported: register failed: $e")
        }
    }

    fun detach() {
        runCatching { vehicle?.getGear()?.unregisterGearSelection(sdkListener) }
        vehicle = null
    }

    override fun current(onResult: (Gear?) -> Unit) {
        val v = vehicle ?: return post { supported = false; Log.w(TAG, "gear unsupported: no vehicle"); onResult(null) }
        v.getGear().getCurrentGearSelectionState(
            { g -> post { Log.i(TAG, "gear supported: current=$g"); onResult(g.toGear()) } },
            { e ->
                Log.w(TAG, "gear current failed: $e")
                post { onResult(null) }
            },
        )
    }

    override fun addListener(l: (Gear) -> Unit) = listeners.add(l)
    override fun removeListener(l: (Gear) -> Unit) = listeners.remove(l)
}

/** 디버그용 덮어쓰기. override 가 있으면 실제 기어 이벤트는 흘리지 않는다. 직렬 경로에서만 호출. */
class FakeableGearSource(private val real: GearSource) : GearSource {
    private val listeners = Listeners<(Gear) -> Unit>()
    private var fake: Gear? = null

    override val supported: Boolean get() = real.supported

    init {
        real.addListener { g -> if (fake == null) emit(g) }
    }

    fun setOverride(g: Gear?) {
        fake = g
        if (g != null) emit(g)
    }

    private fun emit(g: Gear) = listeners.forEach { it(g) }

    override fun current(onResult: (Gear?) -> Unit) {
        val f = fake
        if (f != null) onResult(f) else real.current(onResult)
    }

    override fun addListener(l: (Gear) -> Unit) = listeners.add(l)
    override fun removeListener(l: (Gear) -> Unit) = listeners.remove(l)
}

private const val TAG = "PleoryPlatform"
