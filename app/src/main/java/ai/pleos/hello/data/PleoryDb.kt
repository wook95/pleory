package ai.pleos.hello.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

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

        private const val NAME = "pleory.db"

        /** SQLCipher 로 암호화해 연다. 비밀번호는 Keystore 로 감싸 둔다([DbKey]). */
        fun get(context: Context): PleoryDb = instance ?: synchronized(this) {
            instance ?: run {
                System.loadLibrary("sqlcipher")
                val app = context.applicationContext
                Room.databaseBuilder(app, PleoryDb::class.java, NAME)
                    .openHelperFactory(SupportOpenHelperFactory(DbKey.passphrase(app, NAME)))
                    .build().also { instance = it }
            }
        }
    }
}
