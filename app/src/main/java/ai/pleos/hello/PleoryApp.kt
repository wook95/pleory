package ai.pleos.hello

import ai.pleos.hello.data.PleoryDb
import ai.pleos.hello.data.Segment
import ai.pleos.hello.data.Session
import ai.pleos.hello.platform.SdkHub
import android.app.Application
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** 수동 DI 보관소. SdkHub·DB 를 앱 범위로 조립한다. */
class PleoryApp : Application() {

    lateinit var sdkHub: SdkHub
        private set
    lateinit var db: PleoryDb
        private set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        db = PleoryDb.get(this)
        sdkHub = SdkHub(this).also { it.start() }
        appScope.launch { dbSelfCheck() }
    }

    /** Session 한 줄을 넣고 다시 읽은 뒤 지운다. */
    private suspend fun dbSelfCheck() {
        try {
            val dao = db.sessions()
            val id = dao.insert(Session(profileId = "selfcheck", startedAt = 0L, segment = Segment.OUTING))
            check(dao.get(id)?.profileId == "selfcheck") { "readback mismatch" }
            dao.delete(id)
            Log.i("PleoryDb", "PleoryDb self-check ok id=$id")
        } catch (e: Exception) {
            Log.e("PleoryDb", "PleoryDb self-check failed: $e")
        }
    }
}
