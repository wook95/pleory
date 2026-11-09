package ai.pleos.hello.platform

import ai.pleos.hello.BuildConfig
import ai.pleos.hello.core.CardDigest
import ai.pleos.hello.core.Digester
import ai.pleos.hello.core.StructureContract
import ai.pleos.hello.core.Structurer
import ai.pleos.hello.core.StructurerResult
import com.anthropic.client.AnthropicClient
import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.core.JsonValue
import com.anthropic.errors.AnthropicIoException
import com.anthropic.errors.AnthropicServiceException
import com.anthropic.models.messages.JsonOutputFormat
import com.anthropic.models.messages.MessageCreateParams
import com.anthropic.models.messages.OutputConfig
import com.anthropic.models.messages.StopReason
import com.anthropic.models.messages.ThinkingConfigDisabled
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.UnknownHostException
import android.os.SystemClock
import java.time.Duration
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.CompletionException
import java.util.concurrent.ExecutionException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.future.await
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Claude 구조화(PRD §4-1, TRD D6 1순위). 공식 Java SDK 로 Messages API 를 부르고, 보내는 것은 발화 텍스트뿐이다.
 * 구조화 출력(output_config.format JSON 스키마)으로 받고, 결과는 호출 쪽이 [StructureContract.parse] 로 다시 검증한다.
 *
 * - 키가 비어 있으면 [available] 이 false — 체인이 건너뛴다.
 * - 재시도하지 않는다(maxRetries 0). 실패하면 체인이 Gemini 로 넘어간다.
 * - 코루틴이 취소되면 진행 중인 요청 future 도 취소된다. 늦은 응답은 버려진다.
 * - 클라이언트는 하나만 만든다. 첫 한마디가 클라이언트 생성·첫 TLS 연결에 4.5초를 다 쓰지 않게 [warmUp] 으로 미리 데운다.
 * - Error 메시지는 로그용 분류만 담는다("claude http 429", "claude refusal" 등). 본문·발화·키는 넣지 않는다.
 */
class ClaudeStructurer(
    private val apiKey: String = BuildConfig.CLAUDE_API_KEY,
    private val timeoutMs: Long = 4_500,
) : Structurer, Digester {

    val available: Boolean get() = apiKey.isNotBlank()

    /** 한 번만 만든다. 데우는 중에 구조화가 오면 같은 초기화를 기다린다(lazy 동기화). */
    private val client: AnthropicClient by lazy {
        AnthropicOkHttpClient.builder()
            .apiKey(apiKey)
            .maxRetries(0)
            .timeout(Duration.ofMillis(timeoutMs))
            .build()
    }

    private val warming = AtomicBoolean(false)

    /**
     * 클라이언트를 만들고 과금되지 않는 모델 조회(models.retrieve, 발화 없음)로 연결을 미리 연다. 블로킹하지 않고 한 번만 돈다.
     * 동의가 있을 때만 부른다(호출 쪽 책임). 실패하면 다음 호출에서 다시 데울 수 있다.
     */
    fun warmUp(scope: CoroutineScope, log: (String) -> Unit) {
        if (!available || !warming.compareAndSet(false, true)) return
        scope.launch(Dispatchers.IO) {
            val t0 = SystemClock.elapsedRealtime()
            try {
                client.models().retrieve(MODEL)
                log("claude warm ok ${SystemClock.elapsedRealtime() - t0}ms")
            } catch (e: CancellationException) {
                warming.set(false)
                throw e
            } catch (e: Exception) {
                warming.set(false)
                val r = classify(unwrap(e))
                log("claude warm failed ${(r as? StructurerResult.Error)?.message ?: "network unavailable"}")
            }
        }
    }

    override suspend fun structure(text: String): StructurerResult = call(StructureContract.SYSTEM_PROMPT, SCHEMA, text)

    /** 카드 대표 문장(TRD §8-11). 보내는 것은 정리된 항목 글·유형·감정뿐이다. */
    override suspend fun digest(input: String): StructurerResult = call(CardDigest.SYSTEM_PROMPT, DIGEST_SCHEMA, input)

    private suspend fun call(system: String, schema: JsonOutputFormat.Schema, text: String): StructurerResult {
        if (!available) return StructurerResult.Error("claude unavailable")
        return try {
            // 클라이언트 생성(첫 호출)이 무거워 IO 에서 만든다
            val async = withContext(Dispatchers.IO) { client.async() }
            val message = async.messages().create(params(system, schema, text)).await()
            when (message.stopReason().orElse(null)) {
                StopReason.REFUSAL -> return StructurerResult.Error("claude refusal")
                StopReason.MAX_TOKENS -> return StructurerResult.Error("claude max_tokens")
                else -> Unit
            }
            val json = message.content().firstNotNullOfOrNull { it.text().orElse(null)?.text() }
                ?: return StructurerResult.Error("claude response parse failure")
            StructurerResult.Ok(json)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            classify(unwrap(e))
        }
    }

    private fun params(system: String, schema: JsonOutputFormat.Schema, text: String): MessageCreateParams = MessageCreateParams.builder()
        .model(MODEL)
        .maxTokens(MAX_TOKENS)
        .system(system)
        // 지연을 줄이려고 생각은 끄고 effort 는 low(Haiku 5.5 는 effort high 이하에서 생각 끄기를 허용)
        .thinking(ThinkingConfigDisabled.builder().build())
        .outputConfig(
            OutputConfig.builder()
                .effort(OutputConfig.Effort.LOW)
                .format(JsonOutputFormat.builder().schema(schema).build())
                .build(),
        )
        .addUserMessage(text)
        .build()

    private fun unwrap(e: Throwable): Throwable =
        if ((e is CompletionException || e is ExecutionException) && e.cause != null) unwrap(e.cause!!) else e

    private fun classify(e: Throwable): StructurerResult = when {
        e is AnthropicServiceException -> StructurerResult.Error("claude http ${e.statusCode()}")
        e is AnthropicIoException && e.hasCause<UnknownHostException>() -> StructurerResult.NetworkUnavailable
        e is AnthropicIoException && e.hasCause<ConnectException>() -> StructurerResult.NetworkUnavailable
        e is AnthropicIoException && e.hasCause<InterruptedIOException>() -> StructurerResult.Error("claude timeout")
        e is AnthropicIoException -> StructurerResult.Error("claude network error")
        else -> StructurerResult.Error("claude error ${e.javaClass.simpleName}")
    }

    private inline fun <reified T : Throwable> Throwable.hasCause(): Boolean =
        generateSequence(this) { it.cause.takeIf { c -> c !== it } }.any { it is T }

    private companion object {
        const val MODEL = "claude-haiku-5-5"
        const val MAX_TOKENS = 1_024L

        /** PRD §4-1 응답 모양. 구조화 출력은 객체마다 additionalProperties=false 가 필요하다. */
        val SCHEMA: JsonOutputFormat.Schema = JsonOutputFormat.Schema.builder()
            .putAdditionalProperty("type", JsonValue.from("object"))
            .putAdditionalProperty(
                "properties",
                JsonValue.from(
                    mapOf(
                        "items" to mapOf(
                            "type" to "array",
                            "items" to mapOf(
                                "type" to "object",
                                "properties" to mapOf(
                                    "text" to mapOf("type" to "string"),
                                    "type" to mapOf("type" to "string", "enum" to listOf("thought", "todo", "emotion", "raw")),
                                    "emotion" to mapOf("type" to "string", "enum" to listOf("down", "neutral", "up")),
                                    "keywords" to mapOf("type" to "array", "items" to mapOf("type" to "string")),
                                ),
                                "required" to listOf("text", "type", "emotion", "keywords"),
                                "additionalProperties" to false,
                            ),
                        ),
                    ),
                ),
            )
            .putAdditionalProperty("required", JsonValue.from(listOf("items")))
            .putAdditionalProperty("additionalProperties", JsonValue.from(false))
            .build()

        /** 카드 대표 문장 응답 모양(CardDigest). */
        val DIGEST_SCHEMA: JsonOutputFormat.Schema = JsonOutputFormat.Schema.builder()
            .putAdditionalProperty("type", JsonValue.from("object"))
            .putAdditionalProperty(
                "properties",
                JsonValue.from(listOf("emotion", "work", "idea").associateWith { mapOf("type" to "string") }),
            )
            .putAdditionalProperty("required", JsonValue.from(listOf("emotion", "work", "idea")))
            .putAdditionalProperty("additionalProperties", JsonValue.from(false))
            .build()
    }
}
