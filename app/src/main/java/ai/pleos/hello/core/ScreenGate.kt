package ai.pleos.hello.core

enum class Screen { S0, CARD, CONSENT, SETTINGS, HISTORY }

/**
 * S0 를 뺀 모든 화면(카드·설정·동의·QR·수정)의 진입 조건(TRD §8-1 5번).
 * 기어 P, DO 제한 없음, 설정·키보드·문자열 길이 제한 비활성이어야 한다. 기어를 모르면(null) 막는다.
 */
object ScreenGate {
    const val LIMIT_STRING_LENGTH = 4
    const val NO_KEYBOARD = 8
    const val NO_SETUP = 64
    private const val BLOCKING = LIMIT_STRING_LENGTH or NO_KEYBOARD or NO_SETUP

    fun allowed(gear: Gear?, distractionOptimizationRequired: Boolean, activeRestrictions: Int) =
        gear == Gear.P && !distractionOptimizationRequired && (activeRestrictions and BLOCKING) == 0

    /**
     * 우선순위: 게이트 닫힘 → S0, 동의 필요 → 동의, 설정 열림 → 설정, 지난 기록 열림 → S2, 카드 상태 또는 카드 요청 → 카드.
     * [cardRequested] 는 Gleo SHOW_TODAY_CARD 로 도착 전에도 지금(또는 마지막) 세션 카드를 여는 경우다.
     * [historyOpen] 은 S2 지난 기록(그 안에서 연 지난 카드 포함). 도착 카드 위에서도 열 수 있다.
     */
    fun resolve(allowed: Boolean, consentNeeded: Boolean, settingsOpen: Boolean, state: SessionState, cardRequested: Boolean = false, historyOpen: Boolean = false): Screen = when {
        !allowed -> Screen.S0
        consentNeeded -> Screen.CONSENT
        settingsOpen -> Screen.SETTINGS
        historyOpen -> Screen.HISTORY
        state == SessionState.CARD || cardRequested -> Screen.CARD
        else -> Screen.S0
    }
}
