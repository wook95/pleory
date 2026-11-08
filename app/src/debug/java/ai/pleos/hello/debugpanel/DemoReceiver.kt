package ai.pleos.hello.debugpanel

import ai.pleos.hello.PleoryApp
import ai.pleos.hello.core.SessionEvent
import android.app.Activity
import android.app.Application
import android.content.BroadcastReceiver
import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.ViewGroup
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * tools/run-demo.sh v2 의 녹화 중 조작(디버그 전용). 화면을 가리는 Activity 를 띄우지 않으려고 broadcast 로 받는다.
 * adb shell am broadcast --user 10 -n com.pplab.pleory/ai.pleos.hello.debugpanel.DemoReceiver --es cmd <명령> ...
 * - arm --ei day 1|2 [--ef speed 30] [--es holds 3.6,8.4,2.2] : 시나리오 무장(텍스트 모드). day 2 는 먼저 2일차를 되감는다.
 * - tap --es target <이름> [--es text <항목 문구>] : 보이는 버튼을 접근성 이름으로 찾아 누른다. 좌표는 창 크기와 상관없이 뷰에서 잰다.
 *   누르기 직전에 `PleoryDemo tap <이름> x= y=`(화면 좌표, 편집의 손가락 원용)를 남긴다. 못 찾으면 `tap <이름> missing`.
 * - ignition-off : 시동 OFF(1일차 세션을 닫는다).
 */
class DemoReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as PleoryApp
        when (val cmd = intent.getStringExtra("cmd")) {
            "arm" -> {
                val day = intent.getIntExtra("day", 1)
                val speed = intent.getFloatExtra("speed", DemoScenario.DEFAULT_SPEED.toFloat()).toDouble()
                val holds = DemoPlan.parseHolds(intent.getStringExtra("holds"))
                if (day == 2) {
                    val pending = goAsync()
                    CoroutineScope(Dispatchers.IO).launch {
                        runCatching { rewindDay2(app) }.onFailure { Log.e(DemoScenario.TAG, "rewind day2 failed", it) }
                        Handler(Looper.getMainLooper()).post { DemoScenario.arm(app, viaText = true, speed, day, holds); pending.finish() }
                    }
                } else {
                    DemoScenario.arm(app, viaText = true, speed, day, holds)
                }
            }
            "tap" -> tap(intent.getStringExtra("target").orEmpty(), intent.getStringExtra("text"))
            "ignition-off" -> {
                Log.i(DemoScenario.TAG, "ignition off")
                app.controller.post(SessionEvent.IgnitionOff)
            }
            else -> Log.w(DemoScenario.TAG, "demo cmd unknown: $cmd")
        }
    }

    private fun tap(target: String, text: String?) {
        val match: (String) -> Boolean = when (target) {
            "consent" -> { d -> d == "동의" }
            "delete" -> { d -> d == "‘$text’ 지울지 묻기" }
            "delete-confirm" -> { d -> d == "‘$text’ 지우기" }
            "qr" -> { d -> d == "폰에서 열기" }
            "qr-close" -> { d -> d == "닫기" }
            "history" -> { d -> d == "지난 기록" }
            "todo-only" -> { d -> d.startsWith("할 일만") }
            "remind-done" -> { d -> d.endsWith("’ 했어") && (text == null || d == "‘$text’ 했어") }
            else -> { _ -> false }
        }
        val root = DemoTracker.resumed?.window?.decorView
        val view = root?.let { find(it, match) }
        if (view == null) {
            Log.w(DemoScenario.TAG, "tap $target missing")
            return
        }
        val xy = IntArray(2).also(view::getLocationOnScreen)
        Log.i(DemoScenario.TAG, "tap $target x=${xy[0] + view.width / 2} y=${xy[1] + view.height / 2}")
        // 눌림 표시를 잠깐 보인 뒤 누른다(녹화에 버튼 반응이 남게)
        view.isPressed = true
        view.postDelayed({ view.isPressed = false; view.performClick() }, PRESS_MS)
    }

    private fun find(v: View, match: (String) -> Boolean): View? {
        if (!v.isShown) return null
        if (v.isClickable && v.width > 0 && v.contentDescription?.toString()?.let(match) == true) return v
        if (v is ViewGroup) for (i in 0 until v.childCount) find(v.getChildAt(i), match)?.let { return it }
        return null
    }

    /**
     * 2일차를 다시 찍을 수 있게 되돌린다: 1일차 다음 날 이후에 시작한 세션을 지우고, 1일차 세션 할 일의 했어·알림 기록을 지운다.
     * 처음 찍을 때는 지울 것이 없다.
     */
    private suspend fun rewindDay2(app: PleoryApp) {
        val d1 = DemoScenario.d1Base(app) ?: return
        val db = app.db
        val ids = db.query("SELECT id FROM session WHERE startedAt >= ${DemoPlan.day2Start(d1)}", null).use { c ->
            buildList { while (c.moveToNext()) add(c.getLong(0)) }
        }
        ids.forEach { db.sessions().delete(it) }
        // 1일차 세션은 DemoClock 을 켠 직후에 열려 시작 시각이 d1Base 보다 조금 늦다 — 그날 18:40 이후로 찾는다
        db.openHelper.writableDatabase.execSQL(
            "UPDATE item SET done = 0, remindedAt = NULL WHERE utteranceId IN " +
                "(SELECT u.id FROM utterance u JOIN session s ON u.sessionId = s.id WHERE s.startedAt >= $d1 AND s.startedAt < ${DemoPlan.day2Start(d1)})",
        )
        Log.i(DemoScenario.TAG, "rewind day2 removed=${ids.size}")
    }

    private companion object {
        const val PRESS_MS = 120L
    }
}

/** 지금 보이는 Activity 를 기억한다(디버그 전용). Application.onCreate 전에 뜨는 ContentProvider 로 콜백을 건다. */
class DemoTracker : ContentProvider() {
    override fun onCreate(): Boolean {
        (context?.applicationContext as? Application)?.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityResumed(activity: Activity) { resumed = activity }
            override fun onActivityPaused(activity: Activity) { if (resumed === activity) resumed = null }
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
            override fun onActivityStarted(activity: Activity) = Unit
            override fun onActivityStopped(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        })
        return true
    }

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?) = null
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0

    companion object {
        @Volatile
        var resumed: Activity? = null
            private set
    }
}
