package ai.pleos.hello.data

import ai.pleos.hello.core.StructuredItem

/** 파이프라인이 쓰는 저장소 경계. JVM 테스트는 가짜로 바꾼다. */
interface PleoryStore {
    suspend fun createSession(profileId: String, segment: Segment, startedAt: Long): Long
    suspend fun setSessionState(sessionId: Long, state: SessionState)
    suspend fun closeSession(sessionId: Long, endedAt: Long)
    suspend fun saveUtterance(utterance: Utterance): Long
    suspend fun utterance(id: Long): Utterance?
    suspend fun pendingUtterances(): List<Utterance>

    /** 기존 Item 삭제 + 새 Item 삽입 + llmStatus 갱신을 한 트랜잭션으로. */
    suspend fun replaceItems(utteranceId: Long, items: List<StructuredItem>, status: LlmStatus)
    suspend fun setLlmStatus(utteranceId: Long, status: LlmStatus)
}

class RoomPleoryStore(private val db: PleoryDb) : PleoryStore {
    override suspend fun createSession(profileId: String, segment: Segment, startedAt: Long) =
        db.sessions().insert(Session(profileId = profileId, startedAt = startedAt, segment = segment))

    override suspend fun setSessionState(sessionId: Long, state: SessionState) = db.sessions().setState(sessionId, state)

    override suspend fun closeSession(sessionId: Long, endedAt: Long) =
        db.sessions().close(sessionId, SessionState.CLOSED, endedAt)

    override suspend fun saveUtterance(utterance: Utterance) = db.utterances().insert(utterance)
    override suspend fun utterance(id: Long) = db.utterances().get(id)
    override suspend fun pendingUtterances() = db.utterances().pending()

    override suspend fun replaceItems(utteranceId: Long, items: List<StructuredItem>, status: LlmStatus) {
        db.items().replaceItems(
            utteranceId,
            items.mapIndexed { i, it ->
                Item(utteranceId = utteranceId, order = i, text = it.text, type = it.type, emotion = it.emotion, keywords = it.keywords)
            },
            status,
        )
    }

    override suspend fun setLlmStatus(utteranceId: Long, status: LlmStatus) = db.utterances().setStatus(utteranceId, status)
}
