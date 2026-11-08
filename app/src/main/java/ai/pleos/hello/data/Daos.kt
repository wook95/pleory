package ai.pleos.hello.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface SessionDao {
    @Insert suspend fun insert(session: Session): Long
    @Query("SELECT * FROM session WHERE id = :id") suspend fun get(id: Long): Session?
    @Query("DELETE FROM session WHERE id = :id") suspend fun delete(id: Long)
}

@Dao
interface UtteranceDao {
    @Insert suspend fun insert(utterance: Utterance): Long
    @Query("SELECT * FROM utterance WHERE sessionId = :sessionId ORDER BY at")
    suspend fun bySession(sessionId: Long): List<Utterance>
}

@Dao
interface ItemDao {
    @Insert suspend fun insertAll(items: List<Item>)
    @Query("SELECT * FROM item WHERE utteranceId = :utteranceId ORDER BY `order`")
    suspend fun byUtterance(utteranceId: Long): List<Item>
}

@Dao
interface TrackPointDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insert(point: TrackPoint)
    @Query("SELECT * FROM track_point WHERE sessionId = :sessionId ORDER BY at")
    suspend fun bySession(sessionId: Long): List<TrackPoint>
}

@Dao
interface PlaceDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(place: Place)
    @Query("SELECT * FROM place WHERE profileId = :profileId") suspend fun byProfile(profileId: String): List<Place>
}
