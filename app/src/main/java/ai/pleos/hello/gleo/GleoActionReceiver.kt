package ai.pleos.hello.gleo

import ai.pleos.hello.PleoryApp
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import org.json.JSONObject

/**
 * Gleo AI 의 ADD_DRIVE_NOTE / ADD_DRIVE_NOTES 액션(component=service → sendBroadcast)을 받아 메모로 저장한다.
 * 실측(Gleo 4.2.0.15): 문서 예시와 달리 intent action 은 "<packageName>.<function>" 이고 setPackage 로 보낸다.
 * 문서상 파라미터는 extra "ACTION_PARAM"(JSON 문자열)이지만 key 별 extra 로 오는 경우도 받는다.
 * ADD_DRIVE_NOTES 는 Gleo 가 구조화한 items 배열(PRD §4-1)을 함께 보낸다 — 계약에 맞으면 Gemini 를 건너뛴다.
 * 이 receiver 는 exported 이고 Gleo 권한이 문서화돼 있지 않아 누구나 호출할 수 있다 — 입력 길이를 제한하고 검증한다(TRD §8-3).
 * 머신이 받지 않는 상태(시동 OFF 등)면 실패로 회신한다.
 * 응답은 7초 안에 APP_ACTION_RESPONSE 브로드캐스트로 보낸다. Gleo 는 Thinking 상태에서만 응답을 받으므로
 * 우리 TTS 로 포커스를 뺏지 않고 mode=voice 로 Gleo 가 확인 음성을 내게 한다.
 * SHOW_TODAY_CARD(component=activity)는 이 receiver 가 아니라 MainActivity 가 받는다.
 */
class GleoActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val function = functionOf(intent.action) ?: return
        val param = parseParam(intent)
        val itemsJson = if (function == FN_ADD_DRIVE_NOTES) param?.optJSONArray(KEY_ITEMS)?.toString() else null
        val note = (param?.optString(if (function == FN_ADD_DRIVE_NOTES) KEY_ORIGINAL else KEY_NOTE)?.trim())
            .takeUnless { it.isNullOrBlank() }
            ?: intent.getStringExtra(KEY_NOTE)?.trim().orEmpty()
        val app = context.applicationContext as PleoryApp
        val problem = inputProblem(note, intent.getStringExtra(EXTRA_ACTION_PARAM)?.length ?: 0)
        if (problem != null) {
            Log.i(TAG, "gleo $function rejected: ${problem.first}")
            respond(context, function, "fail", problem.second)
            return
        }
        // 발화 본문은 로그에 남기지 않는다(STT 와 같은 취급)
        Log.i(TAG, "gleo $function accepted len=${note.length} items=${param?.optJSONArray(KEY_ITEMS)?.length() ?: 0}")
        val accepted = if (itemsJson != null) app.controller.addStructuredNoteFromVoice(note, itemsJson)
        else app.controller.addNoteFromVoice(note)
        if (!accepted) {
            Log.i(TAG, "gleo $function rejected: session closed")
            respond(context, function, "fail", "The drive journal is not available right now.")
            return
        }
        respond(context, function, "success", "The note was saved to the drive journal.")
    }

    private fun parseParam(intent: Intent): JSONObject? {
        val raw = intent.getStringExtra(EXTRA_ACTION_PARAM) ?: return null
        return runCatching { JSONObject(raw.trim().trim('"')) }.getOrNull()
    }

    companion object {
        /**
         * Gleo 에 회신한다. 보안 가이드(수신자 제한 없는 암시적 브로드캐스트 금지)에 따라 Gleo 패키지로 한정한다.
         * 실측: APP_ACTION_RESPONSE 는 [GLEO_PACKAGE] 가 동적으로 등록한 receiver 가 받는다.
         */
        fun respond(context: Context, function: String, status: String, message: String) {
            val response = JSONObject()
                .put("functionName", function)
                .put("status", status)
                .put("message", message)
                .put("mode", "voice")
            context.sendBroadcast(
                Intent(ACTION_RESPONSE)
                    .setPackage(GLEO_PACKAGE)
                    .putExtra("packageName", context.packageName)
                    .putExtra("response", response.toString()),
            )
            Log.i(TAG, "gleo response sent fn=$function status=$status")
        }

        private const val TAG = "PleoryFlow"
        private const val FN_ADD_DRIVE_NOTE = "ADD_DRIVE_NOTE"
        private const val FN_ADD_DRIVE_NOTES = "ADD_DRIVE_NOTES"
        const val FN_SHOW_TODAY_CARD = "SHOW_TODAY_CARD"
        private const val GLEO_PACKAGE = "ai.umos.ambientai"
        private const val ACTION_RESPONSE = "ai.pleos.playground.intent.action.APP_ACTION_RESPONSE"
        private const val EXTRA_ACTION_PARAM = "ACTION_PARAM"
        private const val KEY_NOTE = "note"
        private const val KEY_ORIGINAL = "original"
        private const val KEY_ITEMS = "items"
        const val MAX_NOTE_CHARS = 500
        const val MAX_PARAM_CHARS = 5_000

        /** 입력 검증. 문제가 없으면 null, 있으면 (로그용 사유, Gleo 에 돌려줄 영문 메시지). */
        fun inputProblem(note: String, paramChars: Int): Pair<String, String>? = when {
            note.isBlank() -> "blank note" to "The note is empty."
            note.length > MAX_NOTE_CHARS || paramChars > MAX_PARAM_CHARS -> "too long" to "The note is too long."
            else -> null
        }

        /**
         * intent action 을 schema 의 function 이름으로 바꾼다. Gleo 가 실제로 보내는 "ai.pleos.hello.ADD_DRIVE_NOTE"
         * 와 문서 표기 "ADD_DRIVE_NOTE" 를 모두 받는다. 모르는 action 이면 null.
         */
        fun functionOf(action: String?): String? = listOf(FN_ADD_DRIVE_NOTE, FN_ADD_DRIVE_NOTES)
            .firstOrNull { matches(action, it) }

        /** MainActivity 가 받는 SHOW_TODAY_CARD 인가(패키지 접두·맨 이름 모두). */
        fun isShowTodayCard(action: String?) = matches(action, FN_SHOW_TODAY_CARD)

        /** SHOW_TODAY_CARD 회신(status, message). 카드는 주차 중(화면 게이트 열림)에만 연다(PRD v4 §11). */
        fun showTodayCardReply(parked: Boolean): Pair<String, String> =
            if (parked) "success" to "The drive card is now on the screen."
            else "fail" to "The card is available only while parked."

        private fun matches(action: String?, function: String) = action == function || action?.endsWith(".$function") == true
    }
}
