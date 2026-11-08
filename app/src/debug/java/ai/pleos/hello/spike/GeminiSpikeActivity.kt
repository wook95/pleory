package ai.pleos.hello.spike

import ai.pleos.hello.BuildConfig
import android.app.Activity
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * LLM(Gemini) 구조화 스파이크. PRD §4-1 계약을 앱 안에서 확인한다.
 * 공모전은 Gemini 로 확정(TRD D6). 플랫폼 LLM(Gleo AI)이 서드파티에 열리면 Pleos LLM SDK 로 전환한다
 * — 2026-11-08 기준 우리 프로젝트 토큰에는 LLM 엔드포인트 권한이 없다(gateway e:cloud:gateway:00401).
 * 보내는 것은 발화 텍스트뿐(원칙 4).
 * adb shell am start -n ai.pleos.hello/.spike.GeminiSpikeActivity ; adb logcat -s PleorySpike:I
 */
class GeminiSpikeActivity : Activity() {

    private lateinit var out: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        out = TextView(this).apply { textSize = 22f; setPadding(32, 32, 32, 32) }
        setContentView(out)
        Thread {
            listOf("오늘 팀장 때문에 진짜 힘들었다", "내일 거래처 서류 다시 보내야 해, 아 노을 예쁘다", "어 그 뭐냐 음")
                .forEach { utt ->
                    val t0 = System.currentTimeMillis()
                    val result = runCatching { structure(utt) }.getOrElse { "ERR ${it.javaClass.simpleName} ${it.message}" }
                    log("gemini +${System.currentTimeMillis() - t0}ms '$utt' -> $result")
                }
        }.start()
    }

    private fun structure(text: String): String {
        val body = JSONObject()
            .put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", SYSTEM))))
            .put("contents", JSONArray().put(JSONObject().put("role", "user")
                .put("parts", JSONArray().put(JSONObject().put("text", text)))))
            .put("generationConfig", JSONObject()
                .put("temperature", 0.2)
                .put("responseMimeType", "application/json")
                .put("responseSchema", JSONObject(SCHEMA))
                .put("thinkingConfig", JSONObject().put("thinkingBudget", 0)))
        val conn = (URL("https://generativelanguage.googleapis.com/v1beta/models/$MODEL:generateContent")
            .openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 8_000
            readTimeout = 8_000 // PRD §4-1 타임아웃
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("x-goog-api-key", BuildConfig.GEMINI_API_KEY)
        }
        conn.outputStream.use { it.write(body.toString().toByteArray()) }
        if (conn.responseCode != 200) return "HTTP ${conn.responseCode}"
        val resp = JSONObject(conn.inputStream.bufferedReader().readText())
        return resp.getJSONArray("candidates").getJSONObject(0).getJSONObject("content")
            .getJSONArray("parts").getJSONObject(0).getString("text")
    }

    private fun log(msg: String) {
        Log.i("PleorySpike", msg)
        runOnUiThread { out.append("$msg\n\n") }
    }

    private companion object {
        const val MODEL = "gemini-2.5-flash" // flash-lite 는 빈말에 없는 문장을 지어냈다(규칙 4 위반)
        const val SYSTEM = """당신은 운전자가 운전 중에 떠오르는 대로 말한 한마디를 정리한다.
규칙:
1. 여러 생각이 섞여 있으면 항목으로 나눈다. 한 항목은 한 문장. 최대 5개.
2. 각 항목의 type은 thought(생각) / todo(할 일) / emotion(감정) / person(사람) 중 하나.
3. emotion은 down / neutral / up 중 하나. 할 일은 보통 neutral.
4. 말투는 다듬되 뜻을 바꾸지 않는다. 없는 내용을 추가하지 않는다.
5. 정리할 수 없으면 원문을 그대로 1개 항목(type=raw, emotion=neutral, keywords=[])으로 돌려준다.
JSON만 출력한다. 코드펜스·설명 금지."""
        const val SCHEMA = """{"type":"OBJECT","properties":{"items":{"type":"ARRAY","items":{"type":"OBJECT","properties":{
"text":{"type":"STRING"},"type":{"type":"STRING","enum":["thought","todo","emotion","person","raw"]},
"emotion":{"type":"STRING","enum":["down","neutral","up"]},"keywords":{"type":"ARRAY","items":{"type":"STRING"}}},
"required":["text","type","emotion","keywords"]}}},"required":["items"]}"""
    }
}
