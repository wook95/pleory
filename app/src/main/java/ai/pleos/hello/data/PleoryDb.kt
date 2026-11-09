package ai.pleos.hello.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

@Database(
    entities = [Session::class, Utterance::class, Item::class, TrackPoint::class, Place::class],
    version = 3,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class PleoryDb : RoomDatabase() {
    abstract fun sessions(): SessionDao
    abstract fun utterances(): UtteranceDao
    abstract fun items(): ItemDao
    abstract fun trackPoints(): TrackPointDao
    abstract fun places(): PlaceDao
    abstract fun reminders(): ReminderDao

    companion object {
        @Volatile private var instance: PleoryDb? = null

        private const val NAME = "pleory.db"

        /** v2: 도착 리마인드(F15)의 Item.done·Item.remindedAt. 기존 행은 끝내지 않고 알리지 않은 상태가 된다. */
        val MIGRATION_1_2_SQL = listOf(
            "ALTER TABLE `item` ADD COLUMN `done` INTEGER NOT NULL DEFAULT 0",
            "ALTER TABLE `item` ADD COLUMN `remindedAt` INTEGER",
        )
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) = MIGRATION_1_2_SQL.forEach(db::execSQL)
        }

        /** v3: 카드 묶음별 대표 문장(TRD §8-11). 기존 세션은 null 이라 화면이 규칙 폴백으로 그린다. */
        val MIGRATION_2_3_SQL = listOf(
            "ALTER TABLE `session` ADD COLUMN `headlineEmotion` TEXT",
            "ALTER TABLE `session` ADD COLUMN `headlineWork` TEXT",
            "ALTER TABLE `session` ADD COLUMN `headlineIdea` TEXT",
        )
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) = MIGRATION_2_3_SQL.forEach(db::execSQL)
        }

        /** SQLCipher 로 암호화해 연다. 비밀번호는 Keystore 로 감싸 둔다([DbKey]). */
        fun get(context: Context): PleoryDb = instance ?: synchronized(this) {
            instance ?: run {
                System.loadLibrary("sqlcipher")
                val app = context.applicationContext
                Room.databaseBuilder(app, PleoryDb::class.java, NAME)
                    .openHelperFactory(SupportOpenHelperFactory(DbKey.passphrase(app, NAME)))
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .build().also { instance = it }
            }
        }
    }
}
