package ai.pleos.hello.debugpanel

import ai.pleos.hello.PleoryApp
import ai.pleos.hello.data.Emotion
import ai.pleos.hello.data.Item
import ai.pleos.hello.data.ItemType
import ai.pleos.hello.data.LlmStatus
import ai.pleos.hello.data.Segment
import ai.pleos.hello.data.Session
import ai.pleos.hello.data.SessionState
import ai.pleos.hello.data.TrackPoint
import ai.pleos.hello.data.Utterance
import android.util.Log
import java.util.Calendar

/**
 * S2 지난 기록 확인용 지난 세션 6건(디버그 전용). 오늘·어제·2·4·6일 전은 7일 창 안, 7일 전 1건은 창 밖(보이면 안 된다).
 * 지운 항목 1개, 정리 전(원문) 한마디 1개, 한마디 없는 세션 1개를 섞는다. 같은 날 다시 넣으면 같은 시각의 세션을 지우고 새로 넣는다.
 * adb shell am start -n com.pplab.pleory/ai.pleos.hello.debugpanel.DebugPanelActivity --es action seed-history
 */
object HistorySeed {
    private class U(val minute: Int, val text: String, val items: List<Item>, val place: String? = null)
    private class S(val daysAgo: Int, val hour: Int, val minute: Int, val durationMin: Int, val km: Double, val segment: Segment, val utterances: List<U>)

    private fun i(text: String, type: ItemType, emotion: Emotion = Emotion.NEUTRAL, deleted: Boolean = false) =
        Item(utteranceId = 0, order = 0, text = text, type = type, emotion = emotion, deleted = deleted)

    private val SEEDS = listOf(
        S(0, 8, 10, 42, 18.6, Segment.COMMUTE_IN, listOf(
            U(9, "아침에 커피 쏟았다 짜증나", listOf(i("아침에 커피를 쏟아서 짜증났다", ItemType.EMOTION, Emotion.DOWN)), "강변북로"),
            U(27, "점심에 엄마한테 전화해야지", listOf(i("점심에 엄마한테 전화하기", ItemType.TODO)), "잠실대교"),
        )),
        S(1, 18, 35, 53, 22.8, Segment.COMMUTE_OUT, listOf(
            U(12, "회의 잘 끝나서 후련하다", listOf(i("회의가 잘 끝나서 후련했다", ItemType.EMOTION, Emotion.UP)), "올림픽대로"),
            U(31, "금요일까지 보고서 초안 보내야 해 주말엔 뭐 하지", listOf(
                i("금요일까지 보고서 초안 보내기", ItemType.TODO),
                i("주말 계획 고민", ItemType.THOUGHT),
                i("잘못 들린 항목", ItemType.TODO, deleted = true),
            ), "한강대교"),
        )),
        S(2, 14, 0, 23, 6.2, Segment.OUTING, listOf(
            U(5, "마트 가면 우유 사기", listOf(i("마트에서 우유 사기", ItemType.TODO)), "송파대로"),
            U(15, "음 그거 뭐였지", emptyList()), // 정리 전(원문 한 줄)
        )),
        S(4, 18, 50, 51, 23.1, Segment.COMMUTE_OUT, listOf(
            U(8, "오늘 팀장 때문에 진짜 힘들었다", listOf(i("팀장 때문에 힘들었다", ItemType.EMOTION, Emotion.DOWN)), "테헤란로"),
            U(22, "거래처 견적서 다시 확인해야 해", listOf(i("거래처 견적서 다시 확인", ItemType.TODO)), "올림픽대로"),
            U(40, "노을 진짜 예쁘다", listOf(i("노을이 예뻤다", ItemType.EMOTION, Emotion.UP)), "한강대교"),
        )),
        S(6, 7, 55, 45, 19.4, Segment.COMMUTE_IN, emptyList()),
        S(7, 18, 40, 50, 22.0, Segment.COMMUTE_OUT, listOf( // 7일 창 밖
            U(10, "창 밖 세션", listOf(i("보이면 안 되는 할 일", ItemType.TODO))),
        )),
    )

    /** tools/demo-route.gpx 의 회사 → 집 사이를 가볍게 굽힌 도식용 궤적. */
    private val FROM = 37.5133 to 127.1
    private val TO = 37.54 to 126.84

    suspend fun seed(app: PleoryApp) {
        val db = app.db
        val profileId = app.profileId()
        val starts = SEEDS.map { startOf(it) }
        db.query("SELECT id FROM session WHERE startedAt IN (${starts.joinToString(",")})", null).use { c ->
            val ids = buildList { while (c.moveToNext()) add(c.getLong(0)) }
            ids.forEach { db.sessions().delete(it) }
        }
        SEEDS.zip(starts).forEach { (s, start) ->
            val end = start + s.durationMin * 60_000L
            val (oLabel, dLabel) = when (s.segment) {
                Segment.COMMUTE_IN -> "집" to "회사"
                Segment.COMMUTE_OUT -> "회사" to "집"
                Segment.OUTING -> "집" to "마트"
            }
            val sid = db.sessions().insert(
                Session(
                    profileId = profileId, startedAt = start, endedAt = end, segment = s.segment, originLabel = oLabel, destLabel = dLabel,
                    distanceKm = s.km, durationMin = s.durationMin, state = SessionState.CLOSED, cardSeen = true,
                ),
            )
            val n = 24
            fun at(k: Int): Pair<Double, Double> {
                val t = k / (n - 1.0)
                val (a, b) = if (s.segment == Segment.COMMUTE_IN) TO to FROM else FROM to TO
                return a.first + (b.first - a.first) * t + Math.sin(t * Math.PI) * 0.012 to a.second + (b.second - a.second) * t
            }
            db.trackPoints().insertAll((0 until n).map { k -> at(k).let { (lat, lng) -> TrackPoint(sid, start + k * (end - start) / (n - 1), lat, lng, 0) } })
            s.utterances.forEach { u ->
                val (lat, lng) = at((u.minute.toDouble() / s.durationMin * (n - 1)).toInt().coerceIn(0, n - 1))
                val uid = db.utterances().insert(
                    Utterance(
                        sessionId = sid, at = start + u.minute * 60_000L, text = u.text, lat = lat, lng = lng, placeLabel = u.place,
                        llmStatus = if (u.items.isEmpty()) LlmStatus.FAILED else LlmStatus.DONE,
                    ),
                )
                if (u.items.isNotEmpty()) db.items().insertAll(u.items.mapIndexed { k, it -> it.copy(utteranceId = uid, order = k) })
            }
        }
        Log.i(DemoScenario.TAG, "history seeded ${SEEDS.size} sessions profile=$profileId")
    }

    private fun startOf(s: S) = Calendar.getInstance().apply {
        add(Calendar.DAY_OF_YEAR, -s.daysAgo)
        set(Calendar.HOUR_OF_DAY, s.hour); set(Calendar.MINUTE, s.minute); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis
}
