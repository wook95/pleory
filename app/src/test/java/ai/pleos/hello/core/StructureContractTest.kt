package ai.pleos.hello.core

import ai.pleos.hello.data.Emotion
import ai.pleos.hello.data.ItemType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StructureContractTest {
    private val prdSample = """{"items":[
 {"text":"내일 거래처 서류 다시 보내기","type":"todo","emotion":"neutral","keywords":["거래처","서류"]},
 {"text":"노을이 예뻤다","type":"emotion","emotion":"up","keywords":["노을"]}
]}"""

    private fun item(i: Int = 1) = """{"text":"t$i","type":"thought","emotion":"neutral","keywords":[]}"""
    private fun failed(s: String) = assertTrue(s, StructureContract.parse(s) is StructureResult.Failed)

    @Test fun parsesPrdSample() {
        val r = StructureContract.parse(prdSample) as StructureResult.Ok
        assertEquals(2, r.items.size)
        assertEquals(StructuredItem("내일 거래처 서류 다시 보내기", ItemType.TODO, Emotion.NEUTRAL, listOf("거래처", "서류")), r.items[0])
        assertEquals(StructuredItem("노을이 예뻤다", ItemType.EMOTION, Emotion.UP, listOf("노을")), r.items[1])
    }

    @Test fun dropsBlankKeywords() {
        val r = StructureContract.parse("""{"items":[{"text":"x","type":"emotion","emotion":"up","keywords":["", " ","노을 "]}]}""")
        assertEquals(listOf("노을"), (r as StructureResult.Ok).items[0].keywords)
    }

    @Test fun allowsSurroundingWhitespace() =
        assertTrue(StructureContract.parse("\n  $prdSample \n") is StructureResult.Ok)

    @Test fun allowsFiveItems() {
        val r = StructureContract.parse("""{"items":[${(1..5).joinToString(",") { item(it) }}]}""")
        assertEquals(5, (r as StructureResult.Ok).items.size)
    }

    @Test fun allTypesAndEmotions() {
        for (t in listOf("thought", "todo", "emotion", "person", "raw")) for (e in listOf("down", "neutral", "up"))
            assertTrue(StructureContract.parse("""{"items":[{"text":"x","type":"$t","emotion":"$e"}]}""") is StructureResult.Ok)
    }

    @Test fun missingKeywordsIsEmpty() {
        val r = StructureContract.parse("""{"items":[{"text":"x","type":"raw","emotion":"neutral"}]}""") as StructureResult.Ok
        assertEquals(emptyList<String>(), r.items[0].keywords)
    }

    @Test fun handlesEscapesAndUnicode() {
        val r = StructureContract.parse("""{"items":[{"text":"a\"b\n한","type":"raw","emotion":"neutral"}]}""") as StructureResult.Ok
        assertEquals("a\"b\n한", r.items[0].text)
    }

    @Test fun failsOnNonJson() = failed("죄송합니다. 정리할 수 없어요")
    @Test fun failsOnEmpty() = failed("")
    @Test fun failsOnTruncatedJson() = failed("""{"items":[{"text":"x","type":"raw""")
    @Test fun failsOnCodeFence() = failed("```json\n$prdSample\n```")
    @Test fun failsOnLeadingText() = failed("결과입니다: $prdSample")
    @Test fun failsOnTrailingText() = failed("$prdSample 이상입니다")
    @Test fun failsOnTopLevelArray() = failed("[${item()}]")
    @Test fun failsOnMissingItems() = failed("""{"result":[]}""")
    @Test fun failsOnEmptyItems() = failed("""{"items":[]}""")
    @Test fun failsOnSixItems() = failed("""{"items":[${(1..6).joinToString(",") { item(it) }}]}""")
    @Test fun failsOnUnknownType() = failed("""{"items":[{"text":"x","type":"memo","emotion":"neutral"}]}""")
    @Test fun failsOnUppercaseType() = failed("""{"items":[{"text":"x","type":"TODO","emotion":"neutral"}]}""")
    @Test fun failsOnUnknownEmotion() = failed("""{"items":[{"text":"x","type":"raw","emotion":"angry"}]}""")
    @Test fun failsOnMissingEmotion() = failed("""{"items":[{"text":"x","type":"raw"}]}""")
    @Test fun failsOnEmptyText() = failed("""{"items":[{"text":"","type":"raw","emotion":"neutral"}]}""")
    @Test fun failsOnBlankText() = failed("""{"items":[{"text":"  ","type":"raw","emotion":"neutral"}]}""")
    @Test fun failsOnMissingText() = failed("""{"items":[{"type":"raw","emotion":"neutral"}]}""")
    @Test fun failsOnOneBadItemAmongGood() = failed("""{"items":[${item()},{"text":"x","type":"zzz","emotion":"up"}]}""")

    @Test fun rawFallback() {
        val f = StructureContract.rawFallback("원문 그대로")
        assertEquals(StructuredItem("원문 그대로", ItemType.RAW, Emotion.NEUTRAL, emptyList()), f)
    }
}
