package ai.pleos.hello.spike

import android.app.Activity
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import ai.pleos.hello.core.StructureContract
import ai.pleos.hello.platform.GeminiStructurer
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull

/**
 * LLM(Gemini) 구조화 스파이크. PRD §4-1 계약을 앱 안에서 확인한다.
 * 공모전은 Gemini 로 확정(TRD D6). 플랫폼 LLM(Gleo AI)이 서드파티에 열리면 Pleos LLM SDK 로 전환한다
 * — 2026-11-08 기준 우리 프로젝트 토큰에는 LLM 엔드포인트 권한이 없다(gateway e:cloud:gateway:00401).
 * 보내는 것은 발화 텍스트뿐(원칙 4).
 * adb shell am start -n com.pplab.pleory/ai.pleos.hello.spike.GeminiSpikeActivity ; adb logcat -s PleorySpike:I
 */
class GeminiSpikeActivity : Activity() {

    private lateinit var out: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        out = TextView(this).apply { textSize = 22f; setPadding(32, 32, 32, 32) }
        setContentView(out)
        Thread {
            val normal = GeminiStructurer()
            listOf("오늘 팀장 때문에 진짜 힘들었다", "내일 거래처 서류 다시 보내야 해, 아 노을 예쁘다", "어 그 뭐냐 음")
                .forEach { utt -> run("normal", normal, utt) }
            // 실패 경로: 예산 1ms → timeout Error, 바깥 취소 50ms → 컨트롤러 8초 타임아웃과 같은 경로
            run("budget1ms", GeminiStructurer(budgetMs = 1), "내일 거래처 서류 다시 보내야 해")
            val t0 = System.currentTimeMillis()
            val r = runBlocking { withTimeoutOrNull(50) { normal.structure("내일 거래처 서류 다시 보내야 해") } }
            log("gemini[cancel50ms] +${System.currentTimeMillis() - t0}ms -> ${r ?: "cancelled(null)"}")
        }.start()
    }

    private fun run(tag: String, s: GeminiStructurer, utt: String) {
        val t0 = System.currentTimeMillis()
        val r = runBlocking { s.structure(utt) }
        val parsed = (r as? ai.pleos.hello.core.StructurerResult.Ok)?.let { StructureContract.parse(it.rawJsonText) }
        log("gemini[$tag] +${System.currentTimeMillis() - t0}ms '$utt' -> $r | parse=$parsed")
    }

    private fun log(msg: String) {
        Log.i("PleorySpike", msg)
        runOnUiThread { out.append("$msg\n\n") }
    }
}
