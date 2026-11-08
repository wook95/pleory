package ai.pleos.hello.platform

import ai.pleos.playground.navi.constants.NaviErrorCode
import ai.pleos.playground.navi.data.DestinationArrivedInfo
import ai.pleos.playground.navi.data.DestinationInfo
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import android.content.Context
import android.util.Log

/** D9: 미확인 / 없음 / 있음 을 구분한다. */
sealed interface DestinationState {
    data object Unknown : DestinationState
    data object None : DestinationState
    data class Known(val lat: Double, val lng: Double, val name: String?) : DestinationState
}

interface NaviListener {
    fun onDestination(state: DestinationState) {}
    fun onArrived() {}
}

interface NaviSource {
    val destination: DestinationState
    fun refresh()
    fun addListener(l: NaviListener)
    fun removeListener(l: NaviListener)
}

class PleosNaviSource(context: Context, private val post: Post) : NaviSource {
    private val navi = NaviHelper(context)
    private val listeners = Listeners<NaviListener>()
    private var ready = false

    override var destination: DestinationState = DestinationState.Unknown
        private set

    private val sdkListener = object : NaviHelperEventListener {
        override fun onDestinationInfo(destinationInfo: DestinationInfo) {
            // 경로가 없으면 빈 값이 온다(기획 명세). 0,0 을 "없음" 으로 본다.
            val s = if (destinationInfo.latitude == 0.0 && destinationInfo.longitude == 0.0) {
                DestinationState.None
            } else {
                DestinationState.Known(destinationInfo.latitude, destinationInfo.longitude, destinationInfo.name)
            }
            post { setState(s) }
        }

        override fun onDestinationChanged() {
            post {
                setState(DestinationState.Unknown)
                refresh()
            }
        }

        override fun onDestinationArrived(destinationArrivedInfo: DestinationArrivedInfo) {
            post { listeners.forEach { it.onArrived() } }
        }

        override fun onError(errorCode: NaviErrorCode) {
            Log.w(TAG, "navi error $errorCode")
        }
    }

    fun init(onState: (SdkState) -> Unit) {
        navi.initialize(
            {
                post {
                    navi.addListener(sdkListener)
                    ready = true
                    onState(SdkState.READY)
                    refresh()
                }
            },
            { e ->
                Log.w(TAG, "navi init failed: $e")
                post { onState(SdkState.FAILED) }
            },
        )
    }

    fun release() {
        runCatching { navi.removeListener(sdkListener) }
        runCatching { navi.release() }
        ready = false
    }

    override fun refresh() {
        if (ready) runCatching { navi.getDestinationInfo() }.onFailure { Log.w(TAG, "navi refresh failed: $it") }
    }

    private fun setState(s: DestinationState) {
        destination = s
        listeners.forEach { it.onDestination(s) }
    }

    override fun addListener(l: NaviListener) = listeners.add(l)
    override fun removeListener(l: NaviListener) = listeners.remove(l)
}

private const val TAG = "PleoryPlatform"
