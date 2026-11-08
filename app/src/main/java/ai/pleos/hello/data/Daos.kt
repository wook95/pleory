package ai.pleos.hello.data

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
}

@Dao
interface UtteranceDao {
    @Insert suspend fun insert(utterance: Utterance): Long
    @Query("SELECT * FROM utterance WHERE sessionId = :sessionId ORDER BY at")
    suspend fun bySession(sessionId: Long): List<Utterance>
    @Query("SELECT * FROM utterance WHERE id = :id") suspend fun get(id: Long): Utterance?
    @Query("SELECT * FROM utterance WHERE llmStatus = 'PENDING' ORDER BY at") suspend fun pending(): List<Utterance>
    @Query("UPDATE utterance SET llmStatus = :status WHERE id = :id") suspend fun setStatus(id: Long, status: LlmStatus)
}

@Dao
abstract class ItemDao {
    @Insert abstract suspend fun insertAll(items: List<Item>)
    @Query("SELECT * FROM item WHERE utteranceId = :utteranceId ORDER BY `order`")
    abstract suspend fun byUtterance(utteranceId: Long): List<Item>
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
