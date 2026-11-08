package ai.pleos.hello.platform

import ai.pleos.hello.BuildConfig
import ai.pleos.hello.core.Structurer
import ai.pleos.hello.core.StructurerResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.job
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException

/**
 * Gemini REST 구조화(PRD §4-1). 보내는 것은 발화 텍스트뿐이다(프라이버시 원칙: 위치·궤적 없음).
 * 공모전은 Gemini. 플랫폼 LLM(Gleo AI)이 서드파티에 열리면 PleosLlmStructurer로 전환
 *
 * - 코루틴이 취소되면(컨트롤러 8초 타임아웃 포함) 연결을 닫아 블로킹 읽기를 즉시 끊는다.
 * - 5xx/429 는 [budgetMs] 안에서 1회만 재시도한다.
 * - DNS/연결 실패 = [StructurerResult.NetworkUnavailable], 그 외 = [StructurerResult.Error].
 * - Error 메시지는 로그용 사유 분류만 담는다("gemini http 429" 등). 요청·응답 본문, 발화, 키는 넣지 않는다.
 */
class GeminiStructurer(
    private val apiKey: String = BuildConfig.GEMINI_API_KEY,
    private val budgetMs: Int = 8_000,
) : Structurer {

    override suspend fun structure(text: String): StructurerResult = withContext(Dispatchers.IO) {
        val deadline = System.currentTimeMillis() + budgetMs
        var attempt = 0
        while (true) {
            attempt++
            val r = callOnce(text, (deadline - System.currentTimeMillis()).toInt())
            currentCoroutineContext().ensureActive()
            if (r !is Attempt.Retryable) return@withContext r.toResult()
            val left = deadline - System.currentTimeMillis()
            if (attempt >= 2 || left < MIN_RETRY_MS) return@withContext StructurerResult.Error(r.message)
            delay(RETRY_DELAY_MS)
        }
        @Suppress("UNREACHABLE_CODE") error("unreachable")
    }

    private sealed interface Attempt {
        data class Done(val raw: String) : Attempt
        data class Retryable(val message: String) : Attempt
        data object Offline : Attempt
        data class Fail(val message: String) : Attempt
    }

    private fun Attempt.toResult(): StructurerResult = when (this) {
        is Attempt.Done -> StructurerResult.Ok(raw)
        Attempt.Offline -> StructurerResult.NetworkUnavailable
        is Attempt.Fail -> StructurerResult.Error(message)
        is Attempt.Retryable -> StructurerResult.Error(message)
    }

    @OptIn(InternalCoroutinesApi::class)
    private suspend fun callOnce(text: String, timeoutMs: Int): Attempt {
        if (timeoutMs <= 0) return Attempt.Fail("gemini timeout")
        val conn = (URL("$ENDPOINT/$MODEL:generateContent").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = timeoutMs
            readTimeout = timeoutMs
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("x-goog-api-key", apiKey)
        }
        val handle = currentCoroutineContext().job.invokeOnCompletion(onCancelling = true) { conn.disconnect() }
        return try {
            conn.outputStream.use { it.write(requestBody(text).toByteArray()) }
            val code = conn.responseCode
            when {
                code == 200 -> parseText(conn.inputStream.bufferedReader().readText())
                code == 429 || code >= 500 -> Attempt.Retryable("gemini http $code")
                else -> Attempt.Fail("gemini http $code")
            }
        } catch (e: UnknownHostException) {
            Attempt.Offline
        } catch (e: ConnectException) {
            Attempt.Offline
        } catch (e: SocketTimeoutException) {
            Attempt.Fail("gemini timeout")
        } catch (e: IOException) {
            Attempt.Fail("gemini network error ${e.javaClass.simpleName}")
        } finally {
            handle.dispose()
            conn.disconnect()
        }
    }

    private fun parseText(body: String): Attempt = try {
        Attempt.Done(
            JSONObject(body).getJSONArray("candidates").getJSONObject(0).getJSONObject("content")
                .getJSONArray("parts").getJSONObject(0).getString("text"),
        )
    } catch (e: org.json.JSONException) {
        Attempt.Fail("gemini response parse failure")
    }

    private fun requestBody(text: String): String = JSONObject()
        .put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", SYSTEM))))
        .put("contents", JSONArray().put(JSONObject().put("role", "user")
            .put("parts", JSONArray().put(JSONObject().put("text", text)))))
        .put("generationConfig", JSONObject()
            .put("temperature", 0.2)
            .put("responseMimeType", "application/json")
            .put("responseSchema", JSONObject(SCHEMA))
            .put("thinkingConfig", JSONObject().put("thinkingBudget", 0)))
        .toString()

    private companion object {
        const val ENDPOINT = "https://generativelanguage.googleapis.com/v1beta/models"
        const val MODEL = "gemini-2.5-flash" // flash-lite 는 빈말에 없는 문장을 지어냈다(규칙 4 위반)
        const val RETRY_DELAY_MS = 300L
        const val MIN_RETRY_MS = 1_500L
        const val SYSTEM = """당신은 운전자가 운전 중에 떠오르는 대로 말한 한마디를 정리한다.
규칙:
1. 여러 생각이 섞여 있으면 항목으로 나눈다. 한 항목은 한 문장. 최대 5개.
2. 각 항목의 type은 thought(생각) / todo(할 일) / emotion(감정) / person(사람) 중 하나.
3. emotion은 down / neutral / up 중 하나. 할 일은 보통 neutral.
4. 말투는 다듬되 뜻을 바꾸지 않는다. 없는 내용을 추가하지 않는다.
5. 정리할 수 없으면 원문을 그대로 1개 항목(type=raw, emotion=neutral, keywords=[])으로 돌려준다.
6. 항목 text는 "내일 거래처 서류 다시 보내기", "노을이 예뻤다"처럼 짧고 담백한 메모체로 쓴다. "~합니다" 같은 존댓말·설명체는 쓰지 않는다.
JSON만 출력한다. 코드펜스·설명 금지."""
        const val SCHEMA = """{"type":"OBJECT","properties":{"items":{"type":"ARRAY","items":{"type":"OBJECT","properties":{
"text":{"type":"STRING"},"type":{"type":"STRING","enum":["thought","todo","emotion","person","raw"]},
"emotion":{"type":"STRING","enum":["down","neutral","up"]},"keywords":{"type":"ARRAY","items":{"type":"STRING"}}},
"required":["text","type","emotion","keywords"]}}},"required":["items"]}"""
    }
}
