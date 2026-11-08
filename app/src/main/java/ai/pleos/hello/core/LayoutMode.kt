package ai.pleos.hello.core

/**
 * 창 폭에 따른 배치 결정(순수 함수). Pleos Connect 는 앱 창이 1~2칸으로 바뀌므로(landscape/portrait, 분할)
 * 고정 폭 대신 현재 폭(dp)으로 정한다. 폭이 WIDE_MIN_DP 이상이면 좌 경로·우 패널 2열, 아니면 위 경로·아래 패널 1열.
 */
enum class LayoutMode {
    WIDE, NARROW;

    companion object {
        const val WIDE_MIN_DP = 1200
        private const val S0_EDGE_DP = 48
        private const val S0_BUTTON_MAX_HEIGHT_DP = 400
        private const val S0_BUTTON_MIN_HEIGHT_DP = 120

        fun from(widthDp: Int) = if (widthDp >= WIDE_MIN_DP) WIDE else NARROW

        /**
         * S0 "말하기" 버튼 폭: 창 폭 전체를 쓰는 아래 띠(양옆 여백만 뺀다). 주행 중 유일한 조작이라 어디를 눌러도 맞게 한다(PRD §5 "하단 대형 버튼 1개").
         * 글자·아이콘은 운전석 쪽(왼쪽)에 둔다 — 좌핸들에서 앱 창의 왼쪽 끝이 운전석에서 가장 가깝다.
         */
        fun s0ButtonWidthDp(widthDp: Int) = widthDp - 2 * S0_EDGE_DP

        /** S0 버튼 높이: 창 높이의 3분의 1을 120~400dp 로 자른다(1210dp 에서 400dp). */
        fun s0ButtonHeightDp(heightDp: Int) = (heightDp / 3).coerceIn(S0_BUTTON_MIN_HEIGHT_DP, S0_BUTTON_MAX_HEIGHT_DP)
    }
}
