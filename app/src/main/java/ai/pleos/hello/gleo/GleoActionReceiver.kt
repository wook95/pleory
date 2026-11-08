package ai.pleos.hello.gleo

import ai.pleos.hello.PleoryApp
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import org.json.JSONObject

/**
 * Gleo AI 의 ADD_DRIVE_NOTE 액션(component=service → sendBroadcast)을 받아 메모로 저장한다.
 * 문서상 파라미터는 extra "ACTION_PARAM"(JSON 문자열)이지만 key 별 extra 로 오는 경우도 받는다.
 * 응답은 7초 안에 APP_ACTION_RESPONSE 브로드캐스트로 보낸다. 음성은 우리가 "적어뒀어"로 직접 하므로 mode=silent.
 */
class GleoActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_ADD_DRIVE_NOTE) return
        val note = parseNote(intent)
        val app = context.applicationContext as PleoryApp
        if (note.isBlank()) {
            Log.i(TAG, "gleo $ACTION_ADD_DRIVE_NOTE rejected: blank note")
            respond(context, "fail", "The note is empty.")
            return
        }
        // 발화 본문은 로그에 남기지 않는다(STT 와 같은 취급)
        Log.i(TAG, "gleo $ACTION_ADD_DRIVE_NOTE accepted len=${note.length}")
        app.controller.addNoteFromVoice(note)
        respond(context, "success", "The note was saved to the drive journal.")
    }

    private fun parseNote(intent: Intent): String {
        val raw = intent.getStringExtra(EXTRA_ACTION_PARAM)
        if (raw != null) {
            val fromJson = runCatching { JSONObject(raw.trim().trim('"')).optString(KEY_NOTE) }.getOrNull()
            if (!fromJson.isNullOrBlank()) return fromJson.trim()
        }
        return intent.getStringExtra(KEY_NOTE)?.trim().orEmpty()
    }

    private fun respond(context: Context, status: String, message: String) {
        val response = JSONObject()
            .put("functionName", ACTION_ADD_DRIVE_NOTE)
            .put("status", status)
            .put("message", message)
            .put("mode", "silent")
        context.sendBroadcast(
            Intent(ACTION_RESPONSE)
                .putExtra("packageName", context.packageName)
                .putExtra("response", response.toString()),
        )
        Log.i(TAG, "gleo response sent status=$status")
    }

    private companion object {
        const val TAG = "PleoryFlow"
        const val ACTION_ADD_DRIVE_NOTE = "ADD_DRIVE_NOTE"
        const val ACTION_RESPONSE = "ai.pleos.playground.intent.action.APP_ACTION_RESPONSE"
        const val EXTRA_ACTION_PARAM = "ACTION_PARAM"
        const val KEY_NOTE = "note"
    }
}
