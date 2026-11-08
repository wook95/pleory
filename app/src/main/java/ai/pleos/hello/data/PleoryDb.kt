package ai.pleos.hello.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [Session::class, Utterance::class, Item::class, TrackPoint::class, Place::class],
    version = 1,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class PleoryDb : RoomDatabase() {
    abstract fun sessions(): SessionDao
    abstract fun utterances(): UtteranceDao
    abstract fun items(): ItemDao
    abstract fun trackPoints(): TrackPointDao
    abstract fun places(): PlaceDao

    companion object {
        @Volatile private var instance: PleoryDb? = null

        fun get(context: Context): PleoryDb = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, PleoryDb::class.java, "pleory.db")
                .build().also { instance = it }
        }
    }
}
