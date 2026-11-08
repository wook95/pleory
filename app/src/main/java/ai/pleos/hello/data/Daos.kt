package ai.pleos.hello.data

import ai.pleos.hello.core.ReminderCandidate
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update

@Dao
interface SessionDao {
    @Insert suspend fun insert(session: Session): Long
    @Query("SELECT * FROM session WHERE id = :id") suspend fun get(id: Long): Session?
    @Update suspend fun update(session: Session)
    @Query("DELETE FROM session WHERE id = :id") suspend fun delete(id: Long)
    @Query("UPDATE session SET state = :state WHERE id = :id") suspend fun setState(id: Long, state: SessionState)
    @Query("SELECT id FROM session WHERE endedAt IS NOT NULL AND id != :exclude ORDER BY endedAt DESC LIMIT 1")
    suspend fun latestEnded(exclude: Long): Long?
    @Query("UPDATE session SET state = :state, endedAt = :endedAt WHERE id = :id")
    suspend fun close(id: Long, state: SessionState, endedAt: Long)

    /** S2 지난 기록: [since] 이후에 시작해 끝난 세션, 최신순(F11). */
    @Query("SELECT * FROM session WHERE profileId = :profileId AND startedAt >= :since AND endedAt IS NOT NULL ORDER BY startedAt DESC")
    suspend fun endedSince(profileId: String, since: Long): List<Session>
}

@Dao
interface UtteranceDao {
    @Insert suspend fun insert(utterance: Utterance): Long
    @Query("SELECT * FROM utterance WHERE sessionId = :sessionId ORDER BY at")
    suspend fun bySession(sessionId: Long): List<Utterance>
    @Query("SELECT * FROM utterance WHERE id = :id") suspend fun get(id: Long): Utterance?
    @Query("SELECT * FROM utterance WHERE sessionId IN (:sessionIds) ORDER BY at")
    suspend fun bySessions(sessionIds: List<Long>): List<Utterance>
    @Query("SELECT * FROM utterance WHERE llmStatus = 'PENDING' ORDER BY at") suspend fun pending(): List<Utterance>
    @Query("UPDATE utterance SET llmStatus = :status WHERE id = :id") suspend fun setStatus(id: Long, status: LlmStatus)
}

@Dao
abstract class ItemDao {
    @Insert abstract suspend fun insertAll(items: List<Item>)
    @Query("SELECT * FROM item WHERE utteranceId = :utteranceId ORDER BY `order`")
    abstract suspend fun byUtterance(utteranceId: Long): List<Item>

    /** 지운 항목도 함께 읽는다 — "모두 지움"과 "정리 전"을 카드와 같은 규칙으로 가르려면 필요하다(거르기는 [ai.pleos.hello.core.History]). */
    @Query("SELECT * FROM item WHERE utteranceId IN (:utteranceIds) ORDER BY `order`")
    abstract suspend fun byUtterances(utteranceIds: List<Long>): List<Item>
    @Query("DELETE FROM item WHERE utteranceId = :utteranceId") abstract suspend fun deleteByUtterance(utteranceId: Long)

    /** 카드에서 항목 하나 지우기(F9). 행은 남기고 표시만 끈다 — 원문(Utterance.text)은 그대로다. */
    @Query("UPDATE item SET deleted = 1 WHERE id = :id") abstract suspend fun markDeleted(id: Long)
    @Query("UPDATE utterance SET llmStatus = :status WHERE id = :utteranceId")
    abstract suspend fun setUtteranceStatus(utteranceId: Long, status: LlmStatus)

    /**
     * 기존 Item 삭제 + 새 Item 삽입 + llmStatus 갱신을 한 트랜잭션으로. 재처리를 몇 번 해도 Item 수가 늘지 않는다(TRD §2-1).
     * 주의: 사용자가 지운 항목([markDeleted])도 함께 지우고 새로 넣으므로 표시가 되살아난다. 지금은 항목 행이 없는 PENDING 발화만
     * 재처리해서 문제없지만, DONE·FAILED 발화를 다시 정리하는 경로를 만들면 지운 항목을 거르거나 재처리를 막아야 한다.
     */
    @Transaction
    open suspend fun replaceItems(utteranceId: Long, items: List<Item>, status: LlmStatus) {
        deleteByUtterance(utteranceId)
        if (items.isNotEmpty()) insertAll(items)
        setUtteranceStatus(utteranceId, status)
    }
}

@Dao
interface TrackPointDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insert(point: TrackPoint)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertAll(points: List<TrackPoint>)
    @Query("SELECT * FROM track_point WHERE sessionId = :sessionId ORDER BY at")
    suspend fun bySession(sessionId: Long): List<TrackPoint>
}

@Dao
interface PlaceDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(place: Place)
    @Query("SELECT * FROM place WHERE profileId = :profileId") suspend fun byProfile(profileId: String): List<Place>
}

/** 도착 리마인드(F15). 대상 선정 규칙은 [ai.pleos.hello.core.ArrivalReminder] 가 갖고, 여기 조회는 후보를 좁히기만 한다. */
@Dao
interface ReminderDao {
    @Query(CANDIDATES_SQL) suspend fun candidates(profileId: String, since: Long): List<ReminderCandidate>
    @Query("UPDATE item SET remindedAt = :at WHERE id IN (:ids)") suspend fun markReminded(ids: List<Long>, at: Long)
    @Query("UPDATE item SET done = 1 WHERE id = :id") suspend fun markDone(id: Long)

    companion object {
        /** 같은 프로필, [since] 이후에 말한, 끝내지도 지우지도 알리지도 않은 할 일. JVM 테스트(ReminderSqlTest)가 이 문장을 그대로 돌린다. */
        const val CANDIDATES_SQL = """
            SELECT item.id AS itemId, COALESCE(item.editedText, item.text) AS text, utterance.sessionId AS sessionId,
                   session.segment AS segment, utterance.at AS spokenAt, item.type AS type, item.done AS done,
                   item.deleted AS deleted, item.remindedAt AS remindedAt
            FROM item
            JOIN utterance ON item.utteranceId = utterance.id
            JOIN session ON utterance.sessionId = session.id
            WHERE session.profileId = :profileId AND utterance.at >= :since AND item.type = 'TODO'
              AND item.done = 0 AND item.deleted = 0 AND item.remindedAt IS NULL
            ORDER BY utterance.at DESC, item.id"""
    }
}
