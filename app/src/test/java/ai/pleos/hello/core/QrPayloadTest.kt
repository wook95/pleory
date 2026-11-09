package ai.pleos.hello.core

import ai.pleos.hello.data.Emotion
import ai.pleos.hello.data.Item
import ai.pleos.hello.data.ItemType
import ai.pleos.hello.data.Segment
import ai.pleos.hello.data.Session
import ai.pleos.hello.data.Utterance
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import com.google.zxing.qrcode.decoder.Version
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QrPayloadTest {
    private val tz = TimeZone.getTimeZone("Asia/Seoul")
    private val min = 60_000L

    /** 2026-11-08(일) 18:40 KST */
    private val t0 = 1_794_130_800_000L

    private val session = Session(
        id = 1, profileId = "p", startedAt = t0, endedAt = t0 + 51 * min, segment = Segment.COMMUTE_OUT,
        originLabel = "회사", destLabel = "집", distanceKm = 23.4, durationMin = 51, summaryText = "할 일 하나, 생각 하나 남겼어요.",
    )

    private fun utt(id: Long, atMin: Int, text: String = "한마디 $id") = Utterance(id = id, sessionId = 1, at = t0 + atMin * min, text = text)

    private fun item(id: Long, uid: Long, text: String, type: ItemType, order: Int = 0, deleted: Boolean = false, done: Boolean = false) =
        Item(id = id, utteranceId = uid, order = order, text = text, type = type, emotion = Emotion.NEUTRAL, deleted = deleted, done = done)

    private fun card(vararg pairs: Pair<Utterance, List<Item>>, s: Session = session) = CardModelBuilder.build(s, pairs.toList(), emptyList(), tz)

    @Test
    fun `평문 - 제목 대표 문장 묶음별 항목 이번 주행 순`() {
        val m = card(
            utt(1, 17) to listOf(item(1, 1, "내일 거래처 서류 다시 보내기", ItemType.TODO)),
            utt(2, 30) to listOf(item(2, 2, "팀장님 말이 계속 걸린다", ItemType.THOUGHT), item(3, 2, "회의 자료 만들기", ItemType.TODO, order = 1, done = true)),
            s = session.copy(headlineWork = "서류 보내고 회의 자료 만들기", headlineIdea = "팀장님 말"),
        )
        val p = QrPayload.build(m)
        val expected = listOf(
            "11월 8일 퇴근길 18:40–19:31",
            "일: 서류 보내고 회의 자료 만들기",
            "아이디어: 팀장님 말",
            "",
            "[일]",
            "- [ ] 내일 거래처 서류 다시 보내기",
            "- [x] 회의 자료 만들기",
            "",
            "[아이디어]",
            "- 팀장님 말이 계속 걸린다",
            "",
            "이번 주행: 18:40 회사 → 19:31 집 · 51분 · 23km",
        ).joinToString("\n")
        assertEquals(expected, p.text)
        assertEquals(expected.toByteArray(Charsets.UTF_8).size, p.bytes)
        assertEquals(3, p.items)
        assertFalse(p.truncated)
    }

    @Test
    fun `정리 전 한마디는 원문 표시로 아이디어에 들어가고 지운 항목은 빠진다`() {
        val p = QrPayload.build(card(utt(1, 5, "비 오는데 우산 챙길걸") to emptyList(), utt(2, 17) to listOf(item(1, 2, "지운 생각", ItemType.THOUGHT, deleted = true))))
        assertTrue(p.text.contains("[아이디어]\n- 비 오는데 우산 챙길걸 (원문)"))
        assertFalse(p.text.contains("지운 생각"))
        assertEquals(1, p.items)
    }

    @Test
    fun `상한을 넘으면 항목을 뒤에서부터 빼고 외 N개를 붙인다`() {
        val items = (1L..40L).map { utt(it, it.toInt()) to listOf(item(it, it, "긴 할 일 항목 번호 $it 다시 확인하기", ItemType.TODO)) }
        val full = QrPayload.build(card(*items.toTypedArray()), maxBytes = Int.MAX_VALUE)
        assertTrue(full.bytes > QrPayload.MAX_BYTES)

        val p = QrPayload.build(card(*items.toTypedArray()))
        assertTrue(p.bytes <= QrPayload.MAX_BYTES)
        assertTrue(p.truncated)
        assertEquals(40, p.items + p.dropped)
        val lines = p.text.lines()
        // 이번 주행은 줄이지 않고 맨 끝, 그 앞에 빈 줄과 "외 N개"
        assertTrue(lines.last().startsWith("이번 주행: "))
        assertEquals("외 ${p.dropped}개", lines[lines.size - 3])
        assertTrue(lines.contains("- [ ] 긴 할 일 항목 번호 1 다시 확인하기"))
        assertEquals("- [ ] 긴 할 일 항목 번호 ${p.items} 다시 확인하기", lines[lines.size - 4])
        // 한 개를 더 넣으면 상한을 넘는다 = 넣을 수 있는 만큼 넣었다("외 N개" 자릿수가 줄어 1바이트 줄 수 있다)
        val next = "\n- [ ] 긴 할 일 항목 번호 ${p.items + 1} 다시 확인하기".toByteArray(Charsets.UTF_8).size
        assertTrue(p.bytes + next - 1 > QrPayload.MAX_BYTES)
    }

    @Test
    fun `상한 크기의 한글 평문이 오류 정정 M 버전 25 이하에 들어간다`() {
        // 한글 3바이트 + 줄바꿈으로 상한을 꽉 채운다
        val sb = StringBuilder()
        while ((sb.toString() + "가").toByteArray(Charsets.UTF_8).size <= QrPayload.MAX_BYTES) sb.append(if (sb.length % 30 == 29) "\n" else "가")
        val text = sb.toString()
        assertTrue(text.toByteArray(Charsets.UTF_8).size > QrPayload.MAX_BYTES - 3)
        val n = QrPayload.matrix(text).width - 2 * QrPayload.QUIET
        val version = (n - 17) / 4
        assertTrue("version=$version", version <= 25)
        assertEquals(Version.getVersionForNumber(version).dimensionForVersion, n)
        assertEquals(text, decode(text))
    }

    @Test
    fun `인코드 후 디코드하면 한글 평문이 그대로 나온다`() {
        val m = card(
            utt(1, 17) to listOf(item(1, 1, "내일 거래처 서류 다시 보내기", ItemType.TODO), item(2, 1, "김 과장", ItemType.PERSON, order = 1)),
            utt(2, 30) to listOf(item(3, 2, "오늘은 좀 지쳤다 ↓", ItemType.EMOTION)),
        )
        val p = QrPayload.build(m)
        assertEquals(p.text, decode(p.text))
    }

    /** 매트릭스를 모듈당 4px 그림으로 펼쳐 zxing 리더로 읽는다(카메라 없이 화면 그대로). */
    private fun decode(text: String): String {
        val m = QrPayload.matrix(text)
        val unit = 4
        val w = m.width * unit
        val px = IntArray(w * w) { i -> if (m[(i % w) / unit, (i / w) / unit]) 0xFF000000.toInt() else 0xFFFFFFFF.toInt() }
        val bmp = BinaryBitmap(HybridBinarizer(RGBLuminanceSource(w, w, px)))
        return QRCodeReader().decode(bmp, mapOf(DecodeHintType.PURE_BARCODE to false, DecodeHintType.CHARACTER_SET to "UTF-8")).text
    }
}
