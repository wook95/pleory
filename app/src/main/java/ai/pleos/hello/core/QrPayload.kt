package ai.pleos.hello.core

import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/** QR 에 넣을 카드 평문(F10). [items] 는 실제로 넣은 항목 수, [dropped] 는 상한 때문에 뺀 수. */
data class QrText(val text: String, val bytes: Int, val items: Int, val dropped: Int) {
    val truncated get() = dropped > 0
}

/**
 * S1 카드를 폰으로 가져갈 QR 평문(TRD §8-9 F10, v3 §8-11). 서버·URL 없이 글 그대로 넣는다. 카드와 같은 모양이다:
 * 제목("11월 8일 퇴근길 18:40–19:31") → 묶음별 대표 문장("감정: …") → 묶음별 항목("[일]" 아래 "- [ ] …", 감정은 "- ↓ …")
 * → 이번 주행 한 줄. 지운 항목은 카드 모델에서 이미 빠져 있다. 끝낸 할 일은 "- [x]".
 * UTF-8 [MAX_BYTES] 를 넘으면 항목을 뒤에서부터 빼고(제목·대표 문장·이번 주행은 줄이지 않는다) "외 N개" 를 붙인다.
 * 줄 단위로 빼므로 글자 중간에서 자르지 않고, 항목이 다 빠진 묶음은 머리도 뺀다.
 */
object QrPayload {
    /**
     * 오류 정정 M 에서 버전 25(117모듈) 이하로 들어가는 크기. 25-M 의 바이트 모드 용량은 997바이트이고
     * UTF-8 ECI 머리(모드·ECI·길이 지시자)를 빼고 여유를 두어 980 으로 잡았다(QrPayloadTest 에서 실측).
     */
    const val MAX_BYTES = 980

    /** 둘레 여백(quiet zone). 규격 최소가 4모듈이다. */
    const val QUIET = 4

    /**
     * 1모듈 = 1칸인 비트 매트릭스(둘레 여백 [QUIET] 포함). UTF-8 ECI 를 붙여야 한글이 깨지지 않는다(기본은 ISO-8859-1).
     */
    fun matrix(text: String): BitMatrix = QRCodeWriter().encode(
        text, BarcodeFormat.QR_CODE, 0, 0,
        mapOf(
            EncodeHintType.CHARACTER_SET to "UTF-8",
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
            EncodeHintType.MARGIN to QUIET,
        ),
    )

    fun build(m: CardModel, maxBytes: Int = MAX_BYTES): QrText {
        val head = listOf(m.heading) + m.groups.map { "${it.group.label}: ${it.headline}" }
        val trip = "이번 주행: ${m.startLabel} → ${m.endLabel} · ${m.durationMin}분 · ${CardModelBuilder.km(m.distanceKm)}km"
        val lines = m.groups.flatMap { g -> g.items.map { g.group to line(g.group, it.item) } }
        for (k in lines.size downTo 0) {
            val dropped = lines.size - k
            val sections = lines.take(k).groupBy({ it.first }, { it.second }).flatMap { (g, ls) -> listOf("", "[${g.label}]") + ls }
            val text = (head + sections + listOfNotNull(if (dropped > 0) "외 ${dropped}개" else null) + "" + trip).joinToString("\n")
            val bytes = text.toByteArray(Charsets.UTF_8).size
            if (bytes <= maxBytes || k == 0) return QrText(text, bytes, k, dropped)
        }
        error("unreachable")
    }

    private fun line(g: ItemGroup, it: CardItem) = when (g) {
        ItemGroup.WORK -> "- [${if (it.done) "x" else " "}] ${it.text}"
        ItemGroup.EMOTION -> "- ${CardFormat.emotionGlyph(it.emotion)} ${it.text}"
        ItemGroup.IDEA -> "- ${it.text}" + if (it.type == ai.pleos.hello.data.ItemType.RAW) " (원문)" else ""
    }
}
