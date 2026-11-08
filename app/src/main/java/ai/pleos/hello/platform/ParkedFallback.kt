package ai.pleos.hello.platform

/**
 * 기어 API 를 못 쓰는 차량용 대체 판정(TRD §8-1 4번).
 * 속도가 [PARKED_KMH] 이하이고 UX 제한(DO)이 없는 상태가 [holdMs] 이어지면 P, [MOVING_KMH] 이상이면 D.
 * 바뀐 순간에만 기어를 돌려주고, 나머지는 null. 직렬 경로에서만 호출한다.
 */
class ParkedFallback(private val holdMs: Long = 5_000L) {
    private var speedKmh = 0.0
    private var uxRequired = false
    private var nowMs = 0L
    private var parkedSince: Long? = null
    private var emitted: Gear? = null

    fun onSpeed(atMillis: Long, kmh: Double): Gear? {
        nowMs = atMillis
        speedKmh = kmh
        return evaluate()
    }

    fun onUx(required: Boolean): Gear? {
        uxRequired = required
        return evaluate()
    }

    private fun evaluate(): Gear? {
        if (speedKmh >= MOVING_KMH) {
            parkedSince = null
            return emit(Gear.D)
        }
        if (speedKmh > PARKED_KMH || uxRequired) {
            parkedSince = null
            return null
        }
        val since = parkedSince ?: nowMs.also { parkedSince = it }
        return if (nowMs - since >= holdMs) emit(Gear.P) else null
    }

    private fun emit(g: Gear): Gear? = if (emitted == g) null else g.also { emitted = it }

    companion object {
        /** 기어 첫 조회가 비었을 때 대체 판정을 켤지: 리스너 등록이 실패했거나 차량이 없을 때만(true). */
        fun shouldStart(current: Gear?, listenerSupported: Boolean): Boolean = current == null && !listenerSupported

        const val PARKED_KMH = 2.0
        const val MOVING_KMH = 5.0
    }
}
