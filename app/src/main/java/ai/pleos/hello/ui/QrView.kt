package ai.pleos.hello.ui

import ai.pleos.hello.core.QrPayload
import ai.pleos.hello.core.QrText
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.view.Gravity
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout

/**
 * "폰에서 열기" QR 모달(F10). 카드 평문을 그대로 담은 QR 한 장과 안내 한 줄, 닫기.
 * 어두운 화면이어도 QR 은 흰 바탕에 검정으로 그린다(폰 카메라 인식). 모듈은 정수 픽셀로 그려 경계가 번지지 않는다.
 * 넓은 창은 QR 왼쪽·글 오른쪽, 좁은 창은 위아래. 주행 중 닫기는 호출하는 쪽(ScreenGate)이 한다.
 */
class QrView(context: Context, private val onClose: () -> Unit) : ResponsiveScreen(context) {

    private var payload: QrText? = null
    private var bitmap: Bitmap? = null

    init { render() }

    fun setPayload(p: QrText) {
        payload = p
        bitmap = draw(p.text)
        render()
    }

    override fun render() {
        removeAllViews()
        val c = context
        val p = payload ?: return
        val bmp = bitmap ?: return
        gravity = Gravity.CENTER
        setPadding(c.dp(48), c.dp(40), c.dp(48), c.dp(40))
        val plate = FrameLayout(c).apply {
            background = shape(Color.WHITE, c.dpf(Tok.PLATE_R.toFloat()))
            val side = bmp.width
            addView(ImageView(c).apply {
                setImageBitmap(bmp)
                contentDescription = "카드 내용 QR 코드"
            }, FrameLayout.LayoutParams(side, side, Gravity.CENTER))
        }
        val plateSide = bmp.width + c.dp(24) * 2
        val text = c.col().apply {
            addView(c.txt(Tok.TITLE_L, Tok.FG, 700, s = "폰에서 열기"))
            addView(c.txt(Tok.BODY_M, Tok.FG, 500, s = "폰 카메라로 찍으면 오늘 카드가 글로 열려요"), lp().m(t = c.dp(16)))
            val meta = if (p.truncated) "항목 ${p.items}개를 담았어요. 길어서 ${p.dropped}개는 뺐어요." else "항목 ${p.items}개를 담았어요."
            addView(c.txt(Tok.BODY_S, Tok.SUB, s = "$meta 인터넷으로 보내지 않아요."), lp().m(t = c.dp(12)))
            addView(c.button("닫기", BtnKind.SECONDARY) { onClose() }, lp(WRAP_CONTENT).m(t = c.dp(40)))
        }
        if (wide) {
            addView(c.row().apply {
                addView(plate, LinearLayout.LayoutParams(plateSide, plateSide))
                addView(text, lp(c.dp(560)).m(l = c.dp(64)))
            }, lp(WRAP_CONTENT))
        } else {
            addView(plate, LinearLayout.LayoutParams(plateSide, plateSide))
            addView(text, lp(plateSide).m(t = c.dp(36)))
        }
    }

    /** 모듈당 정수 픽셀로 [SIDE_DP] 안에 들어가는 가장 큰 크기. 둘레 여백은 매트릭스에 이미 들어 있다. */
    private fun draw(text: String): Bitmap {
        val m = QrPayload.matrix(text)
        val n = m.width
        val unit = (context.dp(SIDE_DP) / n).coerceAtLeast(1)
        val px = IntArray(n * unit * n * unit)
        for (y in 0 until n * unit) for (x in 0 until n * unit) {
            px[y * n * unit + x] = if (m[x / unit, y / unit]) Color.BLACK else Color.WHITE
        }
        return Bitmap.createBitmap(px, n * unit, n * unit, Bitmap.Config.ARGB_8888)
    }

    private companion object {
        const val SIDE_DP = 640
    }
}
