package ai.pleos.hello.platform

import ai.pleos.playground.location.fused.FusedLocation
import ai.pleos.playground.location.fused.listener.FusedLocationListener
import ai.pleos.playground.location.fused.listener.ProximityAlertListener
import ai.pleos.playground.location.fused.model.LocationData
import ai.pleos.playground.location.fused.model.ProximityAlertEvent
import ai.pleos.playground.location.fused.model.ProximityAlertRequest
import ai.pleos.playground.location.fused.model.ProximityTransition
import android.content.Context
import android.util.Log

data class LocationFix(val lat: Double, val lng: Double, val timeMillis: Long)

interface LocationSource {
    fun start()
    fun stop()
    fun addListener(l: (LocationFix) -> Unit)
    fun removeListener(l: (LocationFix) -> Unit)

    /** 목적지 반경 진입 알림(FusedLocation 근접 알림). 이전 알림은 교체된다. 직렬 경로에서 호출. */
    fun watchProximity(lat: Double, lng: Double, radiusM: Float, onEnter: () -> Unit)
    fun clearProximity()
}

class PleosLocationSource(context: Context, private val post: Post) : LocationSource {
    private val fused = FusedLocation(context)
    private val listeners = Listeners<(LocationFix) -> Unit>()
    private var started = false
    private var ready = false
    private var watch: Watch? = null
    private var alertId: String? = null
    private var generation = 0

    private class Watch(val lat: Double, val lng: Double, val radiusM: Float, val onEnter: () -> Unit)

    private val sdkListener = object : FusedLocationListener {
        override fun onChanged(location: LocationData?) {
            val data = location ?: return
            val fix = LocationFix(data.latitude, data.longitude, data.timestampMillis)
            post { listeners.forEach { it(fix) } }
        }

        override fun onError(e: Exception) {
            Log.w(TAG, "location error: $e")
        }
    }

    /** initialize + isSupported 로 준비 여부를 정한다. */
    fun init(): SdkState = try {
        fused.initialize()
        if (fused.isSupported()) SdkState.READY.also { ready = true; registerAlert() } else SdkState.FAILED
    } catch (e: Exception) {
        Log.w(TAG, "location init failed: $e")
        SdkState.FAILED
    }

    fun release() {
        stop()
        clearProximity()
        ready = false
        runCatching { fused.release() }
    }

    override fun start() {
        if (started) return
        started = true
        fused.registerFusedLocationCallback(sdkListener)
    }

    override fun stop() {
        if (!started) return
        started = false
        runCatching { fused.unregisterFusedLocationCallback(sdkListener) }
    }

    override fun watchProximity(lat: Double, lng: Double, radiusM: Float, onEnter: () -> Unit) {
        watch = Watch(lat, lng, radiusM, onEnter)
        registerAlert()
    }

    override fun clearProximity() {
        watch = null
        generation++
        removeAlert()
    }

    private fun removeAlert() {
        alertId?.let { id -> runCatching { fused.removeProximityAlert(id) } }
        alertId = null
    }

    /** 위치 SDK 가 준비되기 전에 요청이 오면 watch 만 보관했다가 init 직후 등록한다. */
    private fun registerAlert() {
        val w = watch ?: return
        if (!ready) return
        removeAlert()
        val gen = ++generation
        val request = ProximityAlertRequest(w.lat, w.lng, w.radiusM, setOf(ProximityTransition.ENTER))
        try {
            fused.addProximityAlert(request, object : ProximityAlertListener {
                override fun onRegistered(alertId: String) {
                    post {
                        if (gen == generation) {
                            this@PleosLocationSource.alertId = alertId
                            Log.i(TAG, "proximity alert registered id=$alertId radius=${w.radiusM}m")
                        } else {
                            runCatching { fused.removeProximityAlert(alertId) } // 등록 중에 목적지가 바뀜
                        }
                    }
                }

                override fun onTransition(event: ProximityAlertEvent) {
                    Log.i(TAG, "proximity transition ${event.transition}")
                    if (event.transition == ProximityTransition.ENTER) post { if (gen == generation) w.onEnter() }
                }

                override fun onError(e: Exception) {
                    Log.w(TAG, "proximity alert error: $e")
                }
            })
        } catch (e: Exception) {
            Log.w(TAG, "proximity alert register failed: $e")
        }
    }

    override fun addListener(l: (LocationFix) -> Unit) = listeners.add(l)
    override fun removeListener(l: (LocationFix) -> Unit) = listeners.remove(l)
}

private const val TAG = "PleoryPlatform"
