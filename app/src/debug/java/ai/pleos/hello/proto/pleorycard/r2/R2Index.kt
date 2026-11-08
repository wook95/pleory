package ai.pleos.hello.proto.pleorycard.r2

import android.app.Activity
import android.view.View

/** R2 시안 등록. 키 = pleorycard/r2/<a|b|c>/<화면>[-n]. -n 은 세로(분할, 840dp) 창. */
object R2Index {
    fun register(into: MutableMap<String, (Activity) -> View>) {
        val s0 = mapOf<String, (Activity, Boolean, Boolean) -> View>("a" to ::r2aS0, "b" to ::r2bS0, "c" to ::r2cS0)
        val s1 = mapOf<String, (Activity, Boolean, Boolean, Boolean) -> View>("a" to ::r2aS1, "b" to ::r2bS1, "c" to ::r2cS1)
        val frame = mapOf("a" to { FRAME_A }, "b" to { FRAME_B }, "c" to { FRAME_C })
        for (v in listOf("a", "b", "c")) {
            for (n in listOf(false, true)) {
                val sfx = if (n) "-n" else ""
                into["pleorycard/r2/$v/s0$sfx"] = { a -> s0.getValue(v)(a, false, n) }
                into["pleorycard/r2/$v/s0-listen$sfx"] = { a -> s0.getValue(v)(a, true, n) }
                into["pleorycard/r2/$v/s1$sfx"] = { a -> s1.getValue(v)(a, n, false, false) }
                into["pleorycard/r2/$v/s1-summary$sfx"] = { a -> s1.getValue(v)(a, n, true, false) }
                into["pleorycard/r2/$v/s1-empty$sfx"] = { a -> s1.getValue(v)(a, n, false, true) }
                into["pleorycard/r2/$v/settings$sfx"] = { a -> r2Settings(a, frame.getValue(v)(), n) }
                into["pleorycard/r2/$v/settings-confirm$sfx"] = { a -> r2Settings(a, frame.getValue(v)(), n, confirming = true) }
                into["pleorycard/r2/$v/consent$sfx"] = { a -> r2Consent(a, frame.getValue(v)(), n) }
            }
        }
    }
}
