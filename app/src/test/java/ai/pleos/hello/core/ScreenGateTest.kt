package ai.pleos.hello.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenGateTest {
    private fun allowed(gear: Gear? = Gear.P, required: Boolean = false, active: Int = 0) =
        ScreenGate.allowed(gear, required, active)

    @Test fun parkedAndUnrestricted_allowed() = assertTrue(allowed())

    @Test fun notParked_blocked() {
        listOf(Gear.D, Gear.R, Gear.N, null).forEach { assertFalse("$it", allowed(gear = it)) }
    }

    @Test fun distractionOptimizationRequired_blocked() = assertFalse(allowed(required = true))

    @Test fun eachBlockingRestrictionBlocksEvenWhenNotRequired() {
        assertFalse(allowed(active = ScreenGate.NO_SETUP))
        assertFalse(allowed(active = ScreenGate.NO_KEYBOARD))
        assertFalse(allowed(active = ScreenGate.LIMIT_STRING_LENGTH))
        assertFalse(allowed(active = ScreenGate.NO_SETUP or ScreenGate.NO_KEYBOARD or ScreenGate.LIMIT_STRING_LENGTH))
    }

    @Test fun unrelatedRestrictionBits_doNotBlock() {
        // NO_DIALPAD(1), NO_FILTERING(2), NO_VIDEO(16), LIMIT_CONTENT(32), NO_TEXT_MESSAGE(128)
        assertTrue(allowed(active = 1 or 2 or 16 or 32 or 128))
    }

    @Test fun values_matchAndroidCarConstants() {
        assertEquals(4, ScreenGate.LIMIT_STRING_LENGTH)
        assertEquals(8, ScreenGate.NO_KEYBOARD)
        assertEquals(64, ScreenGate.NO_SETUP)
    }

    @Test fun resolve_closedGate_alwaysS0() {
        SessionState.values().forEach { st ->
            assertEquals(Screen.S0, ScreenGate.resolve(false, consentNeeded = true, settingsOpen = true, state = st))
        }
    }

    @Test fun resolve_priority_consentThenSettingsThenCard() {
        assertEquals(Screen.CONSENT, ScreenGate.resolve(true, true, true, SessionState.CARD))
        assertEquals(Screen.SETTINGS, ScreenGate.resolve(true, false, true, SessionState.CARD))
        assertEquals(Screen.CARD, ScreenGate.resolve(true, false, false, SessionState.CARD))
        assertEquals(Screen.S0, ScreenGate.resolve(true, false, false, SessionState.SESSION_OPEN))
    }

    @Test fun resolve_cardRequested_opensCardBeforeArrival_butNotOverConsentOrClosedGate() {
        assertEquals(Screen.CARD, ScreenGate.resolve(true, false, false, SessionState.SESSION_OPEN, cardRequested = true))
        assertEquals(Screen.CARD, ScreenGate.resolve(true, false, false, SessionState.CLOSED, cardRequested = true))
        assertEquals(Screen.CONSENT, ScreenGate.resolve(true, true, false, SessionState.SESSION_OPEN, cardRequested = true))
        assertEquals(Screen.SETTINGS, ScreenGate.resolve(true, false, true, SessionState.SESSION_OPEN, cardRequested = true))
        assertEquals(Screen.S0, ScreenGate.resolve(false, false, false, SessionState.SESSION_OPEN, cardRequested = true))
    }

    @Test fun resolve_history_overCard_butNotWhileDrivingOrOverConsentSettings() {
        assertEquals(Screen.HISTORY, ScreenGate.resolve(true, false, false, SessionState.SESSION_OPEN, historyOpen = true))
        assertEquals(Screen.HISTORY, ScreenGate.resolve(true, false, false, SessionState.CARD, historyOpen = true))
        assertEquals(Screen.SETTINGS, ScreenGate.resolve(true, false, true, SessionState.CARD, historyOpen = true))
        assertEquals(Screen.CONSENT, ScreenGate.resolve(true, true, false, SessionState.CARD, historyOpen = true))
        assertEquals(Screen.S0, ScreenGate.resolve(false, false, false, SessionState.SESSION_OPEN, historyOpen = true))
    }
}
