package ai.pleos.hello

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

/** S0 자리표시 화면. 버튼은 아직 동작하지 않는다. */
class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val title = TextView(this).apply {
            text = "퇴근길 · 기록 중"
            textSize = 36f
            setTextColor(Color.parseColor("#E8ECF2"))
            gravity = Gravity.CENTER
        }
        val speak = Button(this).apply {
            text = "말하기"
            textSize = 32f
            setTextColor(Color.parseColor("#0B1220"))
            setBackgroundColor(Color.parseColor("#00AAD2"))
            setPadding(120, 60, 120, 60)
        }
        setContentView(
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setBackgroundColor(Color.parseColor("#0B1220"))
                addView(title)
                addView(speak, LinearLayout.LayoutParams(-2, -2).apply { topMargin = 64 })
            },
        )
    }
}
