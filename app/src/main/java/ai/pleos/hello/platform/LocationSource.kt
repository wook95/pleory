package ai.pleos.hello.platform

import ai.pleos.playground.location.fused.FusedLocation
import ai.pleos.playground.location.fused.listener.FusedLocationListener
import ai.pleos.playground.location.fused.model.LocationData
import android.content.Context
import android.util.Log

data class LocationFix(val lat: Double, val lng: Double, val timeMillis: Long)

interface LocationSource {
    fun start()
    fun stop()
    fun addListener(l: (LocationFix) -> Unit)
    fun removeListener(l: (LocationFix) -> Unit)
}

class PleosLocationSource(context: Context, private val post: Post) : LocationSource {
    private val fused = FusedLocation(context)
    private val listeners = Listeners<(LocationFix) -> Unit>()
    private var started = false

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
        if (fused.isSupported()) SdkState.READY else SdkState.FAILED
    } catch (e: Exception) {
        Log.w(TAG, "location init failed: $e")
        SdkState.FAILED
    }

    fun release() {
        stop()
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

    override fun addListener(l: (LocationFix) -> Unit) = listeners.add(l)
    override fun removeListener(l: (LocationFix) -> Unit) = listeners.remove(l)
}

private const val TAG = "PleoryPlatform"
