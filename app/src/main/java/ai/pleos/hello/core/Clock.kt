package ai.pleos.hello.core

/** 앱의 모든 시각(startedAt, Utterance.at, durationMin, 구간 판단)은 이 시계로 계산한다(TRD D7). */
interface Clock {
    fun nowMillis(): Long
}

class SystemClock : Clock {
    override fun nowMillis(): Long = System.currentTimeMillis()
}

/**
 * 데모 시계. 생성 시점에 [baseMillis](GPX 시작 타임스탬프)에서 출발해 실제 시간의 [speed]배로 흐른다.
 * [wallMillis] 는 테스트에서 가짜 실시간을 주입하기 위한 것.
 */
class DemoClock(
    private val baseMillis: Long,
    private val speed: Double = 1.0,
    private val wallMillis: () -> Long = System::currentTimeMillis,
) : Clock {
    private val wallStart = wallMillis()

    override fun nowMillis(): Long = baseMillis + ((wallMillis() - wallStart) * speed).toLong()
}
