package ai.pleos.hello.platform

import android.car.drivingstate.CarUxRestrictions
import android.car.drivingstate.CarUxRestrictionsManager

interface UxRestrictions {
    val isDistractionOptimizationRequired: Boolean

    /** CarUxRestrictions.getActiveRestrictions() 비트마스크(NO_SETUP=64, NO_KEYBOARD=8, LIMIT_STRING_LENGTH=4 등). */
    val activeRestrictions: Int
    fun addListener(l: (Boolean) -> Unit)
    fun removeListener(l: (Boolean) -> Unit)
}

class CarUxRestrictionsSource(private val post: Post) : UxRestrictions {
    private val listeners = Listeners<(Boolean) -> Unit>()
    private var manager: CarUxRestrictionsManager? = null

    private var realRequired = false
    private var realActive = 0
    private var override: Boolean? = null

    override val isDistractionOptimizationRequired: Boolean get() = override ?: realRequired
    override val activeRestrictions: Int get() = if (override == true) DEBUG_ACTIVE else if (override == false) 0 else realActive

    fun attach(m: CarUxRestrictionsManager) {
        manager = m
        m.currentCarUxRestrictions?.let {
            realRequired = it.isRequiresDistractionOptimization
            realActive = it.activeRestrictions
        }
        m.registerListener { r: CarUxRestrictions ->
            val required = r.isRequiresDistractionOptimization
            val active = r.activeRestrictions
            post {
                realRequired = required
                realActive = active
                notifyListeners()
            }
        }
    }

    /** 디버그 전용: 에뮬레이터가 기어에 따라 제한을 바꾸지 않을 때 제한 상태를 강제한다. null 이면 실제 값. 직렬 경로에서 호출. */
    fun setOverride(required: Boolean?) {
        override = required
        notifyListeners()
    }

    private fun notifyListeners() {
        val required = isDistractionOptimizationRequired
        listeners.forEach { it(required) }
    }

    fun detach() {
        runCatching { manager?.unregisterListener() }
        manager = null
    }

    override fun addListener(l: (Boolean) -> Unit) = listeners.add(l)
    override fun removeListener(l: (Boolean) -> Unit) = listeners.remove(l)

    private companion object {
        const val DEBUG_ACTIVE = 4 or 8 or 64
    }
}
