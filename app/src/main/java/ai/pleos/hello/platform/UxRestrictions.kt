package ai.pleos.hello.platform

import android.car.drivingstate.CarUxRestrictions
import android.car.drivingstate.CarUxRestrictionsManager

interface UxRestrictions {
    val isDistractionOptimizationRequired: Boolean
    fun addListener(l: (Boolean) -> Unit)
    fun removeListener(l: (Boolean) -> Unit)
}

class CarUxRestrictionsSource(private val post: Post) : UxRestrictions {
    private val listeners = Listeners<(Boolean) -> Unit>()
    private var manager: CarUxRestrictionsManager? = null

    override var isDistractionOptimizationRequired: Boolean = false
        private set

    fun attach(m: CarUxRestrictionsManager) {
        manager = m
        isDistractionOptimizationRequired = m.currentCarUxRestrictions?.isRequiresDistractionOptimization ?: false
        m.registerListener { r: CarUxRestrictions ->
            val required = r.isRequiresDistractionOptimization
            post {
                isDistractionOptimizationRequired = required
                listeners.forEach { it(required) }
            }
        }
    }

    fun detach() {
        runCatching { manager?.unregisterListener() }
        manager = null
    }

    override fun addListener(l: (Boolean) -> Unit) = listeners.add(l)
    override fun removeListener(l: (Boolean) -> Unit) = listeners.remove(l)
}
