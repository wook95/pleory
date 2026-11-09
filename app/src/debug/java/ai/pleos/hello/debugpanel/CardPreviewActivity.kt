package ai.pleos.hello.debugpanel

import ai.pleos.hello.PleoryApp
import ai.pleos.hello.data.RoomPleoryStore
import ai.pleos.hello.ui.S1CardView
import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * 디버그 전용: 마지막으로 끝난 세션의 S1 카드를 정해진 폭(dp)으로 띄운다. 좁은 창(1열) 배치를 에뮬레이터에서 보려고 쓴다
 * (앱 창 폭은 adb 로 바꿀 수 없다). 동작은 본 카드와 같은 S1CardView 다.
 *   adb shell am start --user 10 -n com.pplab.pleory/ai.pleos.hello.debugpanel.CardPreviewActivity --ei width 900 [--es group 아이디어]
 */
class CardPreviewActivity : Activity() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as PleoryApp
        val widthDp = intent.getIntExtra("width", 900)
        val group = intent.getStringExtra("group")
        val card = S1CardView(this, onReplay = {}, onSkip = {}, onQr = {})
        val px = (widthDp * resources.displayMetrics.density).toInt()
        setContentView(FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
            addView(card, FrameLayout.LayoutParams(px, ViewGroup.LayoutParams.MATCH_PARENT))
        })
        scope.launch {
            val store = RoomPleoryStore(app.db)
            val m = store.latestEndedSession(-1)?.let { store.loadCard(it) } ?: return@launch
            card.setModel(m)
            if (group != null) card.postDelayed({ find(card, "$group 자세히")?.performClick() }, 500)
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun find(v: View, desc: String): View? {
        if (v.contentDescription?.toString() == desc) return v
        if (v is ViewGroup) for (i in 0 until v.childCount) find(v.getChildAt(i), desc)?.let { return it }
        return null
    }
}
