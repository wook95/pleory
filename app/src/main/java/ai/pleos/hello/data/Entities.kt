package ai.pleos.hello.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

enum class Segment { COMMUTE_IN, COMMUTE_OUT, OUTING }
enum class SessionState { OPEN, CARD, CLOSED }
enum class LlmStatus { PENDING, DONE, FAILED }
enum class ItemType { THOUGHT, TODO, EMOTION, PERSON, RAW }
enum class Emotion { DOWN, NEUTRAL, UP }
enum class PlaceKind { HOME, WORK }

/** 한 번의 주행 기록. profileId 는 Pleos 운전자 프로필 ID(조회 파티션 키). */
@Entity(tableName = "session", indices = [Index("profileId")])
data class Session(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val profileId: String,
    val startedAt: Long,
    val endedAt: Long? = null,
    val segment: Segment,
    val originLabel: String? = null,
    val destLabel: String? = null,
    val startLat: Double? = null,
    val startLng: Double? = null,
    val endLat: Double? = null,
    val endLng: Double? = null,
    val distanceKm: Double = 0.0,
    val durationMin: Int = 0,
    val weather: String? = null,
    /** 속도 20km/h 미만 누적 분 */
    val congestionMin: Int = 0,
    val summaryText: String? = null,
    val cardSeen: Boolean = false,
    val state: SessionState = SessionState.OPEN,
)

/** 한마디(STT 원문). 음성 파일은 저장하지 않는다. */
@Entity(
    tableName = "utterance",
    foreignKeys = [ForeignKey(Session::class, ["id"], ["sessionId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("sessionId")],
)
data class Utterance(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val at: Long,
    val text: String,
    val lat: Double? = null,
    val lng: Double? = null,
    val placeLabel: String? = null,
    val odometerKm: Double = 0.0,
    val speedKmh: Int = 0,
    val congested: Boolean = false,
    val llmStatus: LlmStatus = LlmStatus.PENDING,
)

/** 한마디에서 정리된 항목. text 는 정리문, editedText 는 사용자 수정본. */
@Entity(
    tableName = "item",
    foreignKeys = [ForeignKey(Utterance::class, ["id"], ["utteranceId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("utteranceId")],
)
data class Item(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val utteranceId: Long,
    @androidx.room.ColumnInfo(name = "order") val order: Int,
    val text: String,
    val editedText: String? = null,
    val type: ItemType,
    val emotion: Emotion = Emotion.NEUTRAL,
    val keywords: List<String> = emptyList(),
    val deleted: Boolean = false,
    /** 도착 리마인드의 "했어"(F15). 끝낸 할 일은 다시 알리지 않는다. */
    @androidx.room.ColumnInfo(defaultValue = "0") val done: Boolean = false,
    /** 도착 리마인드로 말한 시각(F15). 한 번 말한 항목은 다시 말하지 않는다. */
    val remindedAt: Long? = null,
)

/** 경로·거리·정체 계산용 궤적점(5초 간격). */
@Entity(
    tableName = "track_point",
    primaryKeys = ["sessionId", "at"],
    foreignKeys = [ForeignKey(Session::class, ["id"], ["sessionId"], onDelete = ForeignKey.CASCADE)],
)
data class TrackPoint(
    val sessionId: Long,
    val at: Long,
    val lat: Double,
    val lng: Double,
    val speedKmh: Int,
)

/** 집/직장 위치(F12). */
@Entity(tableName = "place", primaryKeys = ["profileId", "kind"])
data class Place(
    val profileId: String,
    val kind: PlaceKind,
    val lat: Double,
    val lng: Double,
)
