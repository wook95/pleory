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
 * 기어 P 이탈이나 새 목적지(Known) 때 도착 관련 플래그를 초기화한다. 목적지 미확인은 5초 규칙으로 발동하지 않는다.
 * 도착(내비 도착·알려진 목적지 근접)은 래치다 — 도착 뒤 내비가 안내를 끝내 목적지가 없음·미확인으로 바뀌어도 유지된다.
 * 세션에서 한 번도 주행(D)하지 않았으면 발동하지 않는다 — 주차 상태로 앱을 켜자마자 빈 카드가 뜨는 것을 막는다.
 */
class CardTrigger {
    private var gear: Gear? = null // 첫 기어 이벤트 전에는 미확인
    private var destination: DestinationState = DestinationState.Unknown
    private var arrivedLatch = false // 이번 구간에 도착했다. 목적지 없음·미확인으로는 지워지지 않는다
    private var parkHeld5s = false
    private var droveThisSession = false

    fun onGear(g: Gear) {
        gear = g
        if (g == Gear.D) droveThisSession = true
        if (g != Gear.P) clearFlags() else parkHeld5s = false
    }

    fun onDestination(d: DestinationState) {
        destination = d
        if (d == DestinationState.Known) clearFlags() else parkHeld5s = false
    }

    fun onArrived() { arrivedLatch = true }

    fun onLocationNearDest() { if (destination == DestinationState.Known) arrivedLatch = true }

    /** P 상태가 5초 지속됐다는 통지. P가 아니면 무시. */
    fun onParkHeld5s() { if (gear == Gear.P) parkHeld5s = true }

    fun fired(): Boolean = gear == Gear.P && droveThisSession && (
        arrivedLatch ||
            (parkHeld5s && destination == DestinationState.None)
        )

    /** [fired] 가 목적지 도착(내비 도착 또는 알려진 목적지 근접)으로 참인가. 대체 판정(P 5초)만으로는 false. */
    fun firedByArrival(): Boolean = fired() && arrivedLatch

    /** 새 세션 시작 시. 기어와 목적지는 현재 값을 유지하고 도착 플래그와 주행 여부를 비운다. */
    fun resetFlags() {
        clearFlags()
        droveThisSession = gear == Gear.D
    }

    private fun clearFlags() {
        arrivedLatch = false
        parkHeld5s = false
    }
}
