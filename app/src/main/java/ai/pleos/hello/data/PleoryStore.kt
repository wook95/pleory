package ai.pleos.hello.data

import ai.pleos.hello.core.CardModel
import ai.pleos.hello.core.CardModelBuilder
import ai.pleos.hello.core.SessionEnd
import ai.pleos.hello.core.StructuredItem
import ai.pleos.hello.core.TrackSample
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 파이프라인이 쓰는 저장소 경계. JVM 테스트는 가짜로 바꾼다. */
interface PleoryStore {
    suspend fun createSession(profileId: String, segment: Segment, startedAt: Long): Long
    suspend fun setSessionState(sessionId: Long, state: SessionState)
    suspend fun closeSession(sessionId: Long, endedAt: Long)
    suspend fun saveUtterance(utterance: Utterance): Long
    suspend fun utterance(id: Long): Utterance?
    suspend fun pendingUtterances(): List<Utterance>

    /** 기존 Item 삭제 + 새 Item 삽입 + llmStatus 갱신을 한 트랜잭션으로. */
    suspend fun replaceItems(utteranceId: Long, items: List<StructuredItem>, status: LlmStatus)
    suspend fun setLlmStatus(utteranceId: Long, status: LlmStatus)

    /** 카드 표시 시점의 종료값과 궤적을 Session 행에 쓰고 state 를 CARD 로 바꾼다. */
    suspend fun finishSession(sessionId: Long, endedAt: Long, end: SessionEnd, track: List<TrackSample>) {}
    suspend fun setSummaryText(sessionId: Long, text: String) {}

    /** 카드 표시용 모델. 구조화가 안 끝난 발화는 원문 한 줄로 들어간다. */
    suspend fun loadCard(sessionId: Long): CardModel? = null

    /** 모든 Room 행(세션·발화·항목·궤적·장소)을 지운다. 동의 설정은 건드리지 않는다. */
    suspend fun deleteAll() {}
}

class RoomPleoryStore(private val db: PleoryDb) : PleoryStore {
    override suspend fun createSession(profileId: String, segment: Segment, startedAt: Long) =
        db.sessions().insert(Session(profileId = profileId, startedAt = startedAt, segment = segment))

    override suspend fun setSessionState(sessionId: Long, state: SessionState) = db.sessions().setState(sessionId, state)

    override suspend fun closeSession(sessionId: Long, endedAt: Long) =
        db.sessions().close(sessionId, SessionState.CLOSED, endedAt)

    override suspend fun saveUtterance(utterance: Utterance) = db.utterances().insert(utterance)
    override suspend fun utterance(id: Long) = db.utterances().get(id)
    override suspend fun pendingUtterances() = db.utterances().pending()

    override suspend fun replaceItems(utteranceId: Long, items: List<StructuredItem>, status: LlmStatus) {
        db.items().replaceItems(
            utteranceId,
            items.mapIndexed { i, it ->
                Item(utteranceId = utteranceId, order = i, text = it.text, type = it.type, emotion = it.emotion, keywords = it.keywords)
            },
            status,
        )
    }

    override suspend fun setLlmStatus(utteranceId: Long, status: LlmStatus) = db.utterances().setStatus(utteranceId, status)

    override suspend fun finishSession(sessionId: Long, endedAt: Long, end: SessionEnd, track: List<TrackSample>) {
        val dao = db.sessions()
        val s = dao.get(sessionId) ?: return
        dao.update(
            s.copy(
                endedAt = endedAt, segment = end.segment, originLabel = end.originLabel, destLabel = end.destLabel,
                startLat = end.startLat, startLng = end.startLng, endLat = end.endLat, endLng = end.endLng,
                distanceKm = end.distanceKm, durationMin = end.durationMin, weather = end.weather,
                congestionMin = end.congestionMin, state = SessionState.CARD,
            ),
        )
        db.trackPoints().insertAll(track.map { TrackPoint(sessionId, it.atMillis, it.lat, it.lng, 0) })
    }

    override suspend fun setSummaryText(sessionId: Long, text: String) {
        val dao = db.sessions()
        dao.get(sessionId)?.let { dao.update(it.copy(summaryText = text)) }
    }

    override suspend fun loadCard(sessionId: Long): CardModel? {
        val session = db.sessions().get(sessionId) ?: return null
        val utterances = db.utterances().bySession(sessionId).map { it to db.items().byUtterance(it.id) }
        return CardModelBuilder.build(session, utterances, db.trackPoints().bySession(sessionId))
    }

    override suspend fun deleteAll(): Unit = withContext(Dispatchers.IO) {
        db.clearAllTables()
        val counts = listOf("session", "utterance", "item", "track_point", "place").joinToString(" ") { t ->
            db.query("SELECT COUNT(*) FROM $t", null).use { c -> c.moveToFirst(); "$t=${c.getInt(0)}" }
        }
        Log.i("PleoryFlow", "deleteAll rows: $counts")
    }
}
