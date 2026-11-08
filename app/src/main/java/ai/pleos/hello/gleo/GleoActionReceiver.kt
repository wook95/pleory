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
 * 응답은 7초 안에 APP_ACTION_RESPONSE 브로드캐스트로 보낸다. Gleo 는 Thinking 상태에서만 응답을 받으므로
 * 우리 TTS 로 포커스를 뺏지 않고 mode=voice 로 Gleo 가 확인 음성을 내게 한다.
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
        if (note.isBlank()) {
            Log.i(TAG, "gleo $function rejected: blank note")
            respond(context, function, "fail", "The note is empty.")
            return
        }
        // 발화 본문은 로그에 남기지 않는다(STT 와 같은 취급)
        Log.i(TAG, "gleo $function accepted len=${note.length} items=${param?.optJSONArray(KEY_ITEMS)?.length() ?: 0}")
        if (itemsJson != null) app.controller.addStructuredNoteFromVoice(note, itemsJson)
        else app.controller.addNoteFromVoice(note)
        respond(context, function, "success", "The note was saved to the drive journal.")
    }

    private fun parseParam(intent: Intent): JSONObject? {
        val raw = intent.getStringExtra(EXTRA_ACTION_PARAM) ?: return null
        return runCatching { JSONObject(raw.trim().trim('"')) }.getOrNull()
    }

    private fun respond(context: Context, function: String, status: String, message: String) {
        val response = JSONObject()
            .put("functionName", function)
            .put("status", status)
            .put("message", message)
            .put("mode", "voice")
        context.sendBroadcast(
            Intent(ACTION_RESPONSE)
                .putExtra("packageName", context.packageName)
                .putExtra("response", response.toString()),
        )
        Log.i(TAG, "gleo response sent status=$status")
    }

    companion object {
        private const val TAG = "PleoryFlow"
        private const val FN_ADD_DRIVE_NOTE = "ADD_DRIVE_NOTE"
        private const val FN_ADD_DRIVE_NOTES = "ADD_DRIVE_NOTES"
        private const val ACTION_RESPONSE = "ai.pleos.playground.intent.action.APP_ACTION_RESPONSE"
        private const val EXTRA_ACTION_PARAM = "ACTION_PARAM"
        private const val KEY_NOTE = "note"
        private const val KEY_ORIGINAL = "original"
        private const val KEY_ITEMS = "items"

        /**
         * intent action 을 schema 의 function 이름으로 바꾼다. Gleo 가 실제로 보내는 "ai.pleos.hello.ADD_DRIVE_NOTE"
         * 와 문서 표기 "ADD_DRIVE_NOTE" 를 모두 받는다. 모르는 action 이면 null.
         */
        fun functionOf(action: String?): String? = listOf(FN_ADD_DRIVE_NOTE, FN_ADD_DRIVE_NOTES)
            .firstOrNull { action == it || action?.endsWith(".$it") == true }
    }
}
