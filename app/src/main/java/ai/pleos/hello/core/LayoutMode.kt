package ai.pleos.hello.core

/**
 * 창 폭에 따른 배치 결정(순수 함수). Pleos Connect 는 앱 창이 1~2칸으로 바뀌므로(landscape/portrait, 분할)
 * 고정 폭 대신 현재 폭(dp)으로 정한다. 폭이 WIDE_MIN_DP 이상이면 좌 경로·우 패널 2열, 아니면 위 경로·아래 패널 1열.
 */
enum class LayoutMode {
    WIDE, NARROW;

    companion object {
        const val WIDE_MIN_DP = 1200
        private const val S0_BUTTON_MAX_WIDTH_DP = 440
        private const val S0_BUTTON_MAX_HEIGHT_DP = 176
        private const val S0_BUTTON_MIN_HEIGHT_DP = 80

        fun from(widthDp: Int) = if (widthDp >= WIDE_MIN_DP) WIDE else NARROW

        /** S0 "말하기" 버튼 폭: 창 폭의 40%, 최대 440dp. */
        fun s0ButtonWidthDp(widthDp: Int) = minOf(S0_BUTTON_MAX_WIDTH_DP, widthDp * 2 / 5)

        /** S0 버튼 높이: 창 높이의 약 14.5%를 80~176dp 로 자른다(1210dp 에서 176dp). */
        fun s0ButtonHeightDp(heightDp: Int) = (heightDp * 29 / 200).coerceIn(S0_BUTTON_MIN_HEIGHT_DP, S0_BUTTON_MAX_HEIGHT_DP)
    }
}
