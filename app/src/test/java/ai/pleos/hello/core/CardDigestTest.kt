package ai.pleos.hello.core

import ai.pleos.hello.data.Emotion
import ai.pleos.hello.data.Emotion.DOWN
import ai.pleos.hello.data.Emotion.NEUTRAL
import ai.pleos.hello.data.Emotion.UP
import ai.pleos.hello.data.ItemType
import ai.pleos.hello.data.ItemType.EMOTION
import ai.pleos.hello.data.ItemType.THOUGHT
import ai.pleos.hello.data.ItemType.TODO
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CardDigestTest {
    private fun d(text: String, type: ItemType, e: Emotion = NEUTRAL) = DigestItem(text, type, e)

    /** 태연 예시 혼잣말을 정리했을 때 나올 법한 항목(TRD §8-11). */
    private val ramble = listOf(
        d("오늘 팀장 때문에 진짜 힘들었다", EMOTION, DOWN),
        d("노들섬이 예뻤다", EMOTION, UP),
        d("이번 주말 노들섬 데이트", THOUGHT),
        d("내일 거래처 서류 다시 보내기", TODO),
        d("회의 자료 만들기", TODO),
        d("날씨가 흐려서 우울하다", EMOTION, DOWN),
        d("짬뽕 먹기", TODO),
        d("요즘 주식 확인하기", THOUGHT),
    )

    @Test fun groupsMapTypes() {
        assertEquals(ItemGroup.EMOTION, ItemGroup.of(EMOTION))
        assertEquals(ItemGroup.WORK, ItemGroup.of(TODO))
        assertEquals(ItemGroup.IDEA, ItemGroup.of(THOUGHT))
        assertEquals(ItemGroup.IDEA, ItemGroup.of(ItemType.PERSON))
        assertEquals(ItemGroup.IDEA, ItemGroup.of(ItemType.RAW))
    }

    @Test fun inputIsGroupEmotionText_noPlaceOrTime() {
        assertEquals(
            "emotion|가라앉음|오늘 팀장 때문에 진짜 힘들었다\nwork|보통|짬뽕 먹기",
            CardDigest.input(listOf(ramble[0], d("짬뽕\n먹기", TODO))),
        )
    }

    @Test fun parsesAllThree_trimsTrailingPeriod() {
        val r = CardDigest.parse("""{"emotion":"팀장 때문에 지쳤다가 노들섬에서 풀렸어.","work":"거래처 서류, 회의 자료, 짬뽕","idea":"주말 노들섬 데이트, 주식 확인"}""", ramble)
        assertEquals(
            Headlines("팀장 때문에 지쳤다가 노들섬에서 풀렸어", "거래처 서류, 회의 자료, 짬뽕", "주말 노들섬 데이트, 주식 확인"),
            (r as DigestResult.Ok).headlines,
        )
    }

    @Test fun absentGroupIsNull_evenIfModelWroteSomething() {
        val r = CardDigest.parse("""{"emotion":"지어낸 감정","work":"서류 보내기","idea":""}""", listOf(d("서류 보내기", TODO)))
        assertEquals(Headlines(null, "서류 보내기", null), (r as DigestResult.Ok).headlines)
    }

    @Test fun failsWhenPresentGroupEmpty() =
        assertTrue(CardDigest.parse("""{"emotion":"","work":"x","idea":"y"}""", ramble) is DigestResult.Failed)

    @Test fun failsWhenTooLong() =
        assertTrue(CardDigest.parse("""{"emotion":"${"가".repeat(CardDigest.MAX_CHARS + 1)}","work":"x","idea":"y"}""", ramble) is DigestResult.Failed)

    @Test fun failsOnNonJson() = assertTrue(CardDigest.parse("정리했어요", ramble) is DigestResult.Failed)

    @Test fun fallback_firstPlusCount_emotionArrowWhenDirectionChanges() {
        assertEquals(
            Headlines("오늘 팀장 때문에 진짜 힘들었다 외 2개", "내일 거래처 서류 다시 보내기 외 2개", "이번 주말 노들섬 데이트 외 1개"),
            CardDigest.fallback(ramble),
        )
        assertEquals(
            Headlines(emotion = "팀장 때문에 힘들었다 → 노을이 예뻤다"),
            CardDigest.fallback(listOf(d("팀장 때문에 힘들었다", EMOTION, DOWN), d("노을이 예뻤다", EMOTION, UP))),
        )
        assertEquals(Headlines(work = "서류 보내기"), CardDigest.fallback(listOf(d("서류 보내기", TODO))))
    }

    private class Fake(private val r: suspend () -> StructurerResult) : Digester {
        var calls = 0
        override suspend fun digest(input: String): StructurerResult { calls++; return r() }
    }

    private val okJson = """{"emotion":"지쳤다가 풀렸어","work":"서류와 회의 자료","idea":"데이트, 주식"}"""

    @Test fun chain_firstOk() = runBlocking {
        val log = mutableListOf<String>()
        val second = Fake { StructurerResult.Ok(okJson) }
        val h = DigestChain(listOf(DigestChain.Step("claude", Fake { StructurerResult.Ok(okJson) }), DigestChain.Step("gemini", second)), log::add).run(ramble)
        assertEquals("지쳤다가 풀렸어", h?.emotion)
        assertEquals(0, second.calls)
        assertEquals(listOf("digest ok: claude"), log)
    }

    @Test fun chain_contractViolationFallsBack_thenTimeout_thenNull() = runBlocking {
        val log = mutableListOf<String>()
        val h = DigestChain(
            listOf(
                DigestChain.Step("claude", Fake { StructurerResult.Ok("""{"emotion":"","work":"","idea":""}""") }),
                DigestChain.Step("gemini", Fake { delay(1_000); StructurerResult.Ok(okJson) }, timeoutMs = 50),
            ),
            log::add,
        ).run(ramble)
        assertNull(h)
        assertEquals(listOf("digest claude failed: contract violation", "digest gemini failed: gemini timeout"), log)
    }

    @Test fun chain_skipsUnavailable_andEmptyInputIsNull() = runBlocking {
        val first = Fake { StructurerResult.Ok(okJson) }
        val chain = DigestChain(listOf(DigestChain.Step("claude", first, available = { false }), DigestChain.Step("gemini", Fake { StructurerResult.Ok(okJson) })))
        assertEquals("데이트, 주식", chain.run(ramble)?.idea)
        assertEquals(0, first.calls)
        assertNull(chain.run(emptyList()))
    }
}
