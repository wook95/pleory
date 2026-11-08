package ai.pleos.hello.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CardTriggerTest {
    /** 세션에서 한 번 주행(D)한 뒤 [gear] 로 바꾼 상태. */
    private fun trigger(dest: DestinationState, gear: Gear = Gear.P) = CardTrigger().apply {
        onDestination(dest)
        onGear(Gear.D)
        onGear(gear)
    }

    @Test fun 주행_전_주차_상태에서는_P_5초여도_발동하지_않음() {
        val t = CardTrigger().apply { onDestination(DestinationState.None); onGear(Gear.P) }
        t.onParkHeld5s()
        assertFalse(t.fired())
    }

    @Test fun 새_세션은_주행_여부를_다시_요구() {
        val t = trigger(DestinationState.None)
        t.resetFlags()
        t.onParkHeld5s()
        assertFalse(t.fired())
    }

    @Test fun 내비_도착_알림은_P에서_발동() {
        val t = trigger(DestinationState.Known)
        t.onArrived()
        assertTrue(t.fired())
    }

    @Test fun 도착_알림이어도_D에서는_발동하지_않음() {
        val t = trigger(DestinationState.Known, Gear.D)
        t.onArrived()
        assertFalse(t.fired())
    }

    @Test fun 알려진_목적지_300m_이내_P에서_발동() {
        val t = trigger(DestinationState.Known)
        t.onLocationNearDest()
        assertTrue(t.fired())
    }

    @Test fun 목적지_없음_확정_P_5초에서_발동() {
        val t = trigger(DestinationState.None)
        t.onParkHeld5s()
        assertTrue(t.fired())
    }

    @Test fun 목적지_미확인은_P_5초로_발동하지_않음() {
        val t = trigger(DestinationState.Unknown)
        t.onParkHeld5s()
        assertFalse(t.fired())
    }

    @Test fun 목적지_알려진_상태의_P_5초는_발동하지_않음() {
        val t = trigger(DestinationState.Known)
        t.onParkHeld5s()
        assertFalse(t.fired())
    }

    @Test fun 목적지_없음에서는_근접_신호로_발동하지_않음() {
        val t = trigger(DestinationState.None)
        t.onLocationNearDest()
        assertFalse(t.fired())
    }

    @Test fun 기어_D_복귀하면_초기화되어_P로_돌아와도_발동하지_않음() {
        val t = trigger(DestinationState.Known)
        t.onArrived()
        t.onGear(Gear.D)
        t.onGear(Gear.P)
        assertFalse(t.fired())
    }

    @Test fun 목적지_변경하면_초기화() {
        val t = trigger(DestinationState.Known)
        t.onArrived()
        t.onDestination(DestinationState.Known)
        assertFalse(t.fired())
    }

    @Test fun P가_아닐_때의_5초_통지는_무시() {
        val t = trigger(DestinationState.None, Gear.D)
        t.onParkHeld5s()
        t.onGear(Gear.P)
        assertFalse(t.fired())
    }
}
