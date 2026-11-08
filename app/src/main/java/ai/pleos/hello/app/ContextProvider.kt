package ai.pleos.hello.app

import ai.pleos.hello.core.EndInfo
import ai.pleos.hello.data.Segment

/** 발화 시점의 맥락 스냅샷(PRD §3 Utterance). 실제 소스 연결은 T6/T8. */
data class ContextSnapshot(
    val lat: Double? = null,
    val lng: Double? = null,
    val placeLabel: String? = null,
    val odometerKm: Double = 0.0,
    val speedKmh: Int = 0,
    val congested: Boolean = false,
)

interface ContextProvider {
    fun profileId(): String
    fun segment(): Segment
    fun snapshot(): ContextSnapshot

    /** 새 세션이 시작될 때 불린다. 세션 단위 누적(궤적·거리·정체)을 비운다. */
    fun onSessionStart() {}

    /** 카드를 띄우는 시점의 세션 종료 재료(T8). */
    fun endInfo(): EndInfo = EndInfo()
}

/** 고정값을 돌려주는 가짜. 실제 소스가 붙기 전까지 쓴다. */
class FixedContextProvider(
    private val profileId: String = "local",
    private val segment: Segment = Segment.OUTING,
    private val snapshot: ContextSnapshot = ContextSnapshot(),
) : ContextProvider {
    override fun profileId() = profileId
    override fun segment() = segment
    override fun snapshot() = snapshot
}
