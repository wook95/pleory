package ai.pleos.hello.data

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import androidx.room.RoomOpenDelegate
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteStatement
import java.lang.reflect.Proxy
import java.sql.Connection
import java.sql.DriverManager

/**
 * v1 → v2 마이그레이션(Item.done·remindedAt)과 리마인드 후보 조회(ReminderDao.CANDIDATES_SQL)를 실제 SQLite 로 돌린다.
 * v1 CREATE 문은 v1 PleoryDb_Impl 의 것을 옮겼고(exportSchema=false 라 스키마 파일이 없다),
 * v2 는 지금 생성된 PleoryDb_Impl 의 createAllTables 를 가짜 연결로 돌려 받는다 — 엔티티를 바꾸고 마이그레이션을 빠뜨리면 여기서 깨진다.
 */
class ReminderSqlTest {
    private val db: Connection = DriverManager.getConnection("jdbc:sqlite::memory:")

    @After fun close() = db.close()

    private fun exec(sql: String) = db.createStatement().use { it.execute(sql) }

    private fun columns(table: String): List<String> = db.createStatement().use { st ->
        st.executeQuery("PRAGMA table_info(`$table`)").use { rs ->
            buildList { while (rs.next()) add("${rs.getString("name")}|${rs.getString("type")}|${rs.getInt("notnull")}|${rs.getString("dflt_value")}|${rs.getInt("pk")}") }
        }
    }

    private fun createV1() {
        exec(SESSION)
        exec(UTTERANCE)
        exec(ITEM_V1)
    }

    @Test fun migration1to2_matchesV2Schema_andKeepsRows() {
        createV1()
        exec("INSERT INTO session (id, profileId, startedAt, segment, distanceKm, durationMin, congestionMin, cardSeen, state) VALUES (1, 'p', 0, 'COMMUTE_OUT', 0, 0, 0, 0, 'CLOSED')")
        exec("INSERT INTO utterance (id, sessionId, at, text, odometerKm, speedKmh, congested, llmStatus) VALUES (1, 1, 0, 't', 0, 0, 0, 'DONE')")
        exec("INSERT INTO item (id, utteranceId, `order`, text, type, emotion, keywords, deleted) VALUES (1, 1, 0, 'x', 'TODO', 'NEUTRAL', '[]', 0)")

        PleoryDb.MIGRATION_1_2_SQL.forEach(::exec)
        val migrated = columns("item")

        // Room 이 지금 엔티티로 만드는 item 표와 열(이름·타입·NOT NULL·기본값·PK)이 같아야 Room 의 열기 검증을 통과한다
        val itemV2 = roomCreateStatements().single { it.startsWith("CREATE TABLE IF NOT EXISTS `item` ") }
        exec(itemV2.replace("`item`", "`item_v2`"))
        assertEquals(columns("item_v2"), migrated)

        db.createStatement().use { st ->
            st.executeQuery("SELECT done, remindedAt FROM item WHERE id = 1").use { rs ->
                rs.next()
                assertEquals(0, rs.getInt("done"))
                assertEquals(null, rs.getObject("remindedAt"))
            }
        }
    }

    @Test fun migration2to3_addsHeadlines_matchesCurrentSessionSchema_andKeepsRows() {
        createV1()
        PleoryDb.MIGRATION_1_2_SQL.forEach(::exec)
        exec("INSERT INTO session (id, profileId, startedAt, segment, distanceKm, durationMin, congestionMin, cardSeen, state) VALUES (1, 'p', 0, 'COMMUTE_OUT', 0, 0, 0, 0, 'CLOSED')")

        PleoryDb.MIGRATION_2_3_SQL.forEach(::exec)
        val migrated = columns("session")

        val sessionV3 = roomCreateStatements().single { it.startsWith("CREATE TABLE IF NOT EXISTS `session` ") }
        exec(sessionV3.replace("`session`", "`session_v3`"))
        assertEquals(columns("session_v3"), migrated)

        db.createStatement().use { st ->
            st.executeQuery("SELECT headlineEmotion, headlineWork, headlineIdea FROM session WHERE id = 1").use { rs ->
                rs.next()
                assertEquals(null, rs.getObject("headlineEmotion"))
                assertEquals(null, rs.getObject("headlineWork"))
                assertEquals(null, rs.getObject("headlineIdea"))
            }
        }
    }

    @Test fun candidatesSql_filtersProfileWindowTypeAndFlags_newestFirst() {
        createV1()
        PleoryDb.MIGRATION_1_2_SQL.forEach(::exec)
        exec("INSERT INTO session (id, profileId, startedAt, segment, distanceKm, durationMin, congestionMin, cardSeen, state) VALUES (1, 'p', 0, 'COMMUTE_OUT', 0, 0, 0, 0, 'CLOSED')")
        exec("INSERT INTO session (id, profileId, startedAt, segment, distanceKm, durationMin, congestionMin, cardSeen, state) VALUES (2, 'other', 0, 'COMMUTE_IN', 0, 0, 0, 0, 'CLOSED')")
        exec("INSERT INTO utterance (id, sessionId, at, text, odometerKm, speedKmh, congested, llmStatus) VALUES (1, 1, 1000, 'a', 0, 0, 0, 'DONE')")
        exec("INSERT INTO utterance (id, sessionId, at, text, odometerKm, speedKmh, congested, llmStatus) VALUES (2, 1, 2000, 'b', 0, 0, 0, 'DONE')")
        exec("INSERT INTO utterance (id, sessionId, at, text, odometerKm, speedKmh, congested, llmStatus) VALUES (3, 1, 10, 'old', 0, 0, 0, 'DONE')")
        exec("INSERT INTO utterance (id, sessionId, at, text, odometerKm, speedKmh, congested, llmStatus) VALUES (4, 2, 2000, 'other', 0, 0, 0, 'DONE')")
        fun item(id: Int, u: Int, type: String = "TODO", extra: String = "", vals: String = "") =
            exec("INSERT INTO item (id, utteranceId, `order`, text, editedText, type, emotion, keywords, deleted$extra) VALUES ($id, $u, 0, '정리$id', ${if (id == 2) "'고친$id'" else "NULL"}, '$type', 'NEUTRAL', '[]', 0$vals)")
        item(1, 1)
        item(2, 2) // editedText 가 이긴다
        item(3, 2, type = "THOUGHT")
        item(4, 2, extra = ", done", vals = ", 1")
        item(5, 2, extra = ", remindedAt", vals = ", 1500")
        exec("INSERT INTO item (id, utteranceId, `order`, text, type, emotion, keywords, deleted) VALUES (6, 2, 1, '지움', 'TODO', 'NEUTRAL', '[]', 1)")
        item(7, 3) // 기간 밖
        item(8, 4) // 다른 프로필

        val sql = ReminderDao.CANDIDATES_SQL.replace(":profileId", "?").replace(":since", "?")
        val rows = db.prepareStatement(sql).use { ps ->
            ps.setString(1, "p")
            ps.setLong(2, 100)
            ps.executeQuery().use { rs ->
                buildList { while (rs.next()) add("${rs.getLong("itemId")}:${rs.getString("text")}:${rs.getLong("sessionId")}:${rs.getString("segment")}:${rs.getLong("spokenAt")}") }
            }
        }
        assertEquals(listOf("2:고친2:1:COMMUTE_OUT:2000", "1:정리1:1:COMMUTE_OUT:1000"), rows)
    }

    /** 생성된 PleoryDb_Impl.createAllTables 가 실행하는 SQL 을 모은다(SQLite 없이 문장만). */
    private fun roomCreateStatements(): List<String> {
        val sqls = mutableListOf<String>()
        val statement = Proxy.newProxyInstance(javaClass.classLoader, arrayOf(SQLiteStatement::class.java)) { _, m, _ ->
            when (m.returnType) {
                java.lang.Boolean.TYPE -> false
                Integer.TYPE -> 0
                java.lang.Long.TYPE -> 0L
                else -> null
            }
        }
        val connection = Proxy.newProxyInstance(javaClass.classLoader, arrayOf(SQLiteConnection::class.java)) { _, m, args ->
            when (m.name) {
                "prepare" -> statement.also { sqls += args[0] as String }
                "inTransaction" -> false
                else -> null
            }
        } as SQLiteConnection
        val impl = Class.forName("ai.pleos.hello.data.PleoryDb_Impl").getDeclaredConstructor().newInstance()
        val delegate = impl.javaClass.getDeclaredMethod("createOpenDelegate").apply { isAccessible = true }.invoke(impl) as RoomOpenDelegate
        delegate.createAllTables(connection)
        return sqls
    }

    private companion object {
        const val SESSION = "CREATE TABLE IF NOT EXISTS `session` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `profileId` TEXT NOT NULL, `startedAt` INTEGER NOT NULL, `endedAt` INTEGER, `segment` TEXT NOT NULL, `originLabel` TEXT, `destLabel` TEXT, `startLat` REAL, `startLng` REAL, `endLat` REAL, `endLng` REAL, `distanceKm` REAL NOT NULL, `durationMin` INTEGER NOT NULL, `weather` TEXT, `congestionMin` INTEGER NOT NULL, `summaryText` TEXT, `cardSeen` INTEGER NOT NULL, `state` TEXT NOT NULL)"
        const val UTTERANCE = "CREATE TABLE IF NOT EXISTS `utterance` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `sessionId` INTEGER NOT NULL, `at` INTEGER NOT NULL, `text` TEXT NOT NULL, `lat` REAL, `lng` REAL, `placeLabel` TEXT, `odometerKm` REAL NOT NULL, `speedKmh` INTEGER NOT NULL, `congested` INTEGER NOT NULL, `llmStatus` TEXT NOT NULL, FOREIGN KEY(`sessionId`) REFERENCES `session`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"
        const val ITEM_V1 = "CREATE TABLE IF NOT EXISTS `item` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `utteranceId` INTEGER NOT NULL, `order` INTEGER NOT NULL, `text` TEXT NOT NULL, `editedText` TEXT, `type` TEXT NOT NULL, `emotion` TEXT NOT NULL, `keywords` TEXT NOT NULL, `deleted` INTEGER NOT NULL, FOREIGN KEY(`utteranceId`) REFERENCES `utterance`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"
    }
}
