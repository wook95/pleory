package ai.pleos.hello.data

import androidx.room.TypeConverter

/** enum 은 name, keywords 는 탭 구분 문자열로 저장한다. */
class Converters {
    @TypeConverter fun fromSegment(v: Segment): String = v.name
    @TypeConverter fun toSegment(v: String): Segment = Segment.valueOf(v)
    @TypeConverter fun fromState(v: SessionState): String = v.name
    @TypeConverter fun toState(v: String): SessionState = SessionState.valueOf(v)
    @TypeConverter fun fromLlm(v: LlmStatus): String = v.name
    @TypeConverter fun toLlm(v: String): LlmStatus = LlmStatus.valueOf(v)
    @TypeConverter fun fromType(v: ItemType): String = v.name
    @TypeConverter fun toType(v: String): ItemType = ItemType.valueOf(v)
    @TypeConverter fun fromEmotion(v: Emotion): String = v.name
    @TypeConverter fun toEmotion(v: String): Emotion = Emotion.valueOf(v)
    @TypeConverter fun fromKind(v: PlaceKind): String = v.name
    @TypeConverter fun toKind(v: String): PlaceKind = PlaceKind.valueOf(v)

    @TypeConverter
    fun fromKeywords(v: List<String>): String = v.joinToString("\t")

    @TypeConverter
    fun toKeywords(v: String): List<String> = if (v.isEmpty()) emptyList() else v.split("\t")
}
