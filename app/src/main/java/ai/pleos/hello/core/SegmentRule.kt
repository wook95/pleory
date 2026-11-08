package ai.pleos.hello.core

import ai.pleos.hello.data.Segment

data class GeoPoint(val lat: Double, val lng: Double)

/** PRD §6 구간 판단. 입력은 전부 순수 값. */
object SegmentRule {
    const val PLACE_RADIUS_M = 300.0

    /**
     * @param home 등록된 집 / [work] 등록된 직장 (없으면 null)
     * @param destination 내비 목적지 또는 도착 좌표 (모르면 null)
     * @param destLabel 내비 목적지명
     * @param startHour 출발 시각의 시(0~23), Clock 기준
     *
     * 규칙 1: 집·직장 중 하나라도 등록돼 있고 목적지 좌표가 있으면 반경 300m 안의 장소로 결정
     *   (집 → 퇴근, 직장 → 출근, 둘 다 아니면 외출). 목적지 좌표가 없으면 규칙 2로 넘어간다.
     * 규칙 2: 목적지명 "집"/"회사" → 퇴근/출근. 아니면 출발 시각 [6,10) 출근, [16,21) 퇴근, 그 외 외출.
     */
    fun decide(
        home: GeoPoint?,
        work: GeoPoint?,
        destination: GeoPoint?,
        destLabel: String?,
        startHour: Int,
    ): Segment {
        if ((home != null || work != null) && destination != null) {
            return when {
                home != null && TrackMath.distanceMeters(home, destination) <= PLACE_RADIUS_M -> Segment.COMMUTE_OUT
                work != null && TrackMath.distanceMeters(work, destination) <= PLACE_RADIUS_M -> Segment.COMMUTE_IN
                else -> Segment.OUTING
            }
        }
        when (destLabel?.trim()) {
            "집" -> return Segment.COMMUTE_OUT
            "회사" -> return Segment.COMMUTE_IN
        }
        return when (startHour) {
            in 6..9 -> Segment.COMMUTE_IN
            in 16..20 -> Segment.COMMUTE_OUT
            else -> Segment.OUTING
        }
    }
}
