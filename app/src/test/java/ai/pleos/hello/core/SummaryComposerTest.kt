package ai.pleos.hello.core

import ai.pleos.hello.data.Emotion
import ai.pleos.hello.data.ItemType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SummaryComposerTest {
    private fun emo(e: Emotion, vararg kw: String) = SummaryItem(ItemType.EMOTION, e, kw.toList())
    private val todo = SummaryItem(ItemType.TODO)
    private val thought = SummaryItem(ItemType.THOUGHT)

    @Test fun prdDemo() {
        val items = listOf(
            emo(Emotion.DOWN, "팀장"),
            SummaryItem(ItemType.TODO, Emotion.NEUTRAL, listOf("거래처", "서류")),
            emo(Emotion.UP, "노을"),
        )
        assertEquals("힘들게 시작해서 노을로 끝났어. 할 일 하나 있어.", SummaryComposer.compose(items))
    }

    @Test fun empty() = assertEquals("오늘은 말이 없었네. 그래도 여기까지 왔어.", SummaryComposer.compose(emptyList()))

    @Test fun emptyHasNoTodoSuffix() = assertTrue(!SummaryComposer.compose(emptyList()).contains("할 일"))

    @Test fun oneEmotionDown() = assertEquals("오늘은 좀 힘들었네.", SummaryComposer.compose(listOf(emo(Emotion.DOWN))))
    @Test fun oneEmotionUp() = assertEquals("오늘은 괜찮았네.", SummaryComposer.compose(listOf(emo(Emotion.UP))))
    @Test fun oneEmotionNeutral() = assertEquals("오늘은 괜찮았네.", SummaryComposer.compose(listOf(emo(Emotion.NEUTRAL))))

    @Test fun noEmotion() = assertEquals("생각이 좀 있었네.", SummaryComposer.compose(listOf(thought)))

    @Test fun twoEmotionsOther() =
        assertEquals("감정이 올라왔다, 가라앉았다.", SummaryComposer.compose(listOf(emo(Emotion.UP), emo(Emotion.DOWN))))

    @Test fun threeEmotionsNotDownThenUp() =
        assertEquals(
            "감정이 가라앉았다, 그냥, 가라앉았다.",
            SummaryComposer.compose(listOf(emo(Emotion.DOWN), emo(Emotion.NEUTRAL), emo(Emotion.DOWN))),
        )

    @Test fun downNeutralUpMiddleIgnored() =
        assertEquals(
            "힘들게 시작해서 바다로 끝났어.",
            SummaryComposer.compose(listOf(emo(Emotion.DOWN), emo(Emotion.NEUTRAL), emo(Emotion.UP, "바다"))),
        )

    @Test fun particleWithBatchim() =
        assertEquals(
            "힘들게 시작해서 음악으로 끝났어.",
            SummaryComposer.compose(listOf(emo(Emotion.DOWN), emo(Emotion.UP, "음악"))),
        )

    @Test fun downUpWithoutKeyword() =
        assertEquals("힘들게 시작해서 좋게 끝났어.", SummaryComposer.compose(listOf(emo(Emotion.DOWN), emo(Emotion.UP))))

    @Test fun blankKeywordSkipped() =
        assertEquals(
            "힘들게 시작해서 노을로 끝났어.",
            SummaryComposer.compose(listOf(emo(Emotion.DOWN), emo(Emotion.UP, "", "노을"))),
        )

    @Test fun allBlankKeywordsFallBack() =
        assertEquals(
            "힘들게 시작해서 좋게 끝났어.",
            SummaryComposer.compose(listOf(emo(Emotion.DOWN), emo(Emotion.UP, "", " "))),
        )

    @Test fun todoCountWords() {
        assertEquals("생각이 좀 있었네. 할 일 둘 있어.", SummaryComposer.compose(listOf(thought, todo, todo)))
        assertEquals("생각이 좀 있었네. 할 일 셋 있어.", SummaryComposer.compose(listOf(thought, todo, todo, todo)))
    }

    @Test fun todoOnlyIsNoEmotion() =
        assertEquals("생각이 좀 있었네. 할 일 하나 있어.", SummaryComposer.compose(listOf(todo)))

    @Test fun nonEmotionTypeIgnoredEvenWithEmotionValue() =
        assertEquals(
            "생각이 좀 있었네.",
            SummaryComposer.compose(listOf(SummaryItem(ItemType.THOUGHT, Emotion.DOWN))),
        )

    @Test fun capsAt40Chars() {
        val items = List(12) { emo(Emotion.NEUTRAL) } + todo
        assertEquals(40, SummaryComposer.compose(items).length)
    }
}
