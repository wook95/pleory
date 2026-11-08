package ai.pleos.hello.core

/** 목적지 상태(TRD D9). 미확인을 "없음"으로 취급하면 안 된다. */
sealed interface DestinationState {
    data object Unknown : DestinationState
    data object None : DestinationState
    data object Known : DestinationState
}

enum class Gear { P, R, N, D }

/**
 * 카드 표시 조건(TRD D9): 기어 P AND (내비 도착 OR 알려진 목적지 300m 이내 OR 목적지 없음 확정 + P 5초).
 * 목적지 변경이나 기어 P 이탈 때 도착 관련 플래그를 초기화한다. 목적지 미확인은 5초 규칙으로 발동하지 않는다.
 */
class CardTrigger {
    private var gear: Gear = Gear.D
    private var destination: DestinationState = DestinationState.Unknown
    private var arrived = false
    private var nearDestination = false
    private var parkHeld5s = false

    fun onGear(g: Gear) {
        gear = g
        if (g != Gear.P) clearFlags() else parkHeld5s = false
    }

    fun onDestination(d: DestinationState) {
        destination = d
        clearFlags()
    }

    fun onArrived() { arrived = true }

    fun onLocationNearDest() { nearDestination = true }

    /** P 상태가 5초 지속됐다는 통지. P가 아니면 무시. */
    fun onParkHeld5s() { if (gear == Gear.P) parkHeld5s = true }

    fun fired(): Boolean = gear == Gear.P && (
        arrived ||
            (nearDestination && destination == DestinationState.Known) ||
            (parkHeld5s && destination == DestinationState.None)
        )

    /** 새 세션 시작 시. 기어와 목적지는 현재 값을 유지하고 도착 플래그만 비운다. */
    fun resetFlags() = clearFlags()

    private fun clearFlags() {
        arrived = false
        nearDestination = false
        parkHeld5s = false
    }
}
