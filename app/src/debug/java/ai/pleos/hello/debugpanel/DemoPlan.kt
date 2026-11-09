package ai.pleos.hello.debugpanel

import java.util.Calendar
import java.util.TimeZone

/** 데모 시나리오 v2 의 시각 계산(순수). video-script.md "파이프라인 요구사항" 공통 2·4. */
object DemoPlan {
    /** 대본의 듣는 중 유지 시간(대사 mp3 길이 + 0.1초). run-demo.sh 가 ffprobe 로 잰 값을 넘기면 그것을 쓴다. */
    /** v3 1일차 혼잣말 하나의 듣는 중 유지 시간(ramble-1.mp3 길이 + 0.1초가 없을 때). */
    val DEFAULT_HOLDS_MS = listOf(21_000L)

    /** 듣는 중을 [holdMs] 유지하면 저장이 그만큼 늦으므로, 앱 시계로 그 [speed] 배만큼 앞당겨 탭한다. */
    fun leadMs(holdMs: Long, speed: Double): Long = (holdMs * speed).toLong()

    /** "3.6,8.4,2.2"(초) → 밀리초. 비었거나 숫자가 아니면 null(기본값을 쓴다). */
    fun parseHolds(s: String?): List<Long>? =
        s?.split(',')?.map { it.trim().toDoubleOrNull() ?: return null }?.takeIf { it.isNotEmpty() }?.map { (it * 1000).toLong() }

    /** [millis] 와 같은 날 [hour]:[minute]:00. */
    fun at(millis: Long, hour: Int, minute: Int, tz: TimeZone = TimeZone.getDefault()): Long = Calendar.getInstance(tz).apply {
        timeInMillis = millis
        set(Calendar.HOUR_OF_DAY, hour); set(Calendar.MINUTE, minute); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    /** 2일차 출발: 1일차 출발 날짜 + 1일 08:10. */
    fun day2Base(d1BaseMillis: Long, tz: TimeZone = TimeZone.getDefault()): Long = Calendar.getInstance(tz).apply {
        timeInMillis = at(d1BaseMillis, 8, 10, tz)
        add(Calendar.DAY_OF_YEAR, 1)
    }.timeInMillis

    /** 2일차 이후(되감기 대상)의 시작: 1일차 다음 날 0시. */
    fun day2Start(d1BaseMillis: Long, tz: TimeZone = TimeZone.getDefault()): Long = Calendar.getInstance(tz).apply {
        timeInMillis = at(d1BaseMillis, 0, 0, tz)
        add(Calendar.DAY_OF_YEAR, 1)
    }.timeInMillis
}
