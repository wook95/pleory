package ai.pleos.hello.data

import ai.pleos.hello.core.ConsentPolicy
import ai.pleos.hello.core.ConsentState
import android.content.Context

/** 동의 상태·시각·고지 버전을 SharedPreferences 에 둔다. 전체 삭제(Room)와 무관하게 유지된다. */
class ConsentPrefs(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("consent", Context.MODE_PRIVATE)

    val state: ConsentState
        get() = runCatching { ConsentState.valueOf(prefs.getString(KEY_STATE, null) ?: "UNSET") }.getOrDefault(ConsentState.UNSET)
    val version: Int get() = prefs.getInt(KEY_VERSION, 0)
    val at: Long get() = prefs.getLong(KEY_AT, 0L)

    val effective: ConsentState get() = ConsentPolicy.effective(state, version)
    val llmAllowed: Boolean get() = ConsentPolicy.llmAllowed(state, version)
    val needsPrompt: Boolean get() = ConsentPolicy.needsPrompt(state, version)

    fun grant(now: Long) = set(ConsentState.GRANTED, now)
    fun decline(now: Long) = set(ConsentState.DECLINED, now)
    fun withdraw(now: Long) = set(ConsentState.WITHDRAWN, now)

    private fun set(s: ConsentState, now: Long) {
        prefs.edit().putString(KEY_STATE, s.name).putInt(KEY_VERSION, ConsentPolicy.VERSION).putLong(KEY_AT, now).apply()
    }

    private companion object {
        const val KEY_STATE = "state"
        const val KEY_VERSION = "version"
        const val KEY_AT = "at"
    }
}
