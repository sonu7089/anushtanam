package com.mantrasiddhi.data.sync

import android.content.Context
import android.util.AtomicFile
import com.mantrasiddhi.data.local.dao.CloudSyncDao
import com.mantrasiddhi.data.local.entity.CloudSyncEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

object SyncKind {
    const val ACCOUNT_RECORD = "account_record"
    const val PRACTICE_RECORD = "practice_record"
    const val CONFLICT_RESOLUTION = "conflict_resolution"
    const val JAPA_ENTRY = "japa_entry"
    const val JAPA_GOAL = "japa_goal"
    const val PROGRESS = "progress"
    const val TARGET_CREATE = "target_create"
    const val TARGET_INCREMENT = "target_increment"
    const val TARGET_CLEAR = "target_clear"
    const val PURASHCHARANA = "purashcharana"
    const val CHILD_GOAL = "child_goal"
}

/** Enqueue is local disk I/O only. Failed or unacknowledged work is never discarded. */
@Singleton
class CloudSyncOutbox @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: CloudSyncDao,
    private val scheduler: CloudSyncScheduler
) {
    @Volatile private var identity: String? = null

    suspend fun enqueue(uid: String, kind: String, payload: Map<String, Any?>) = withContext(Dispatchers.IO) {
        enqueueInTransaction(uid, kind, payload)
        // The outbox survives a scheduler failure and is recovered on application start.
        runCatching { scheduler.schedule() }
        Unit
    }

    suspend fun enqueueInTransaction(uid: String, kind: String, payload: Map<String, Any?>) {
        require(uid.isNotBlank())
        dao.enqueue(CloudSyncEntity(uid = uid, streamId = streamId(), kind = kind,
            payload = JSONObject(payload).toString(), createdAtMs = System.currentTimeMillis()))
    }

    suspend fun schedule() { runCatching { scheduler.schedule() } }

    @Synchronized
    private fun streamId(): String {
        identity?.let { return it }
        // A restored database must not reuse the sequence identity of the original
        // installation: its restored auto-increment counter may be behind the server.
        val file = AtomicFile(context.noBackupFilesDir.resolve("cloud-sync-stream"))
        val existing = runCatching { String(file.readFully(), Charsets.UTF_8) }.getOrNull()
        val value = existing?.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString().also { generated ->
            val stream = file.startWrite()
            try { stream.write(generated.toByteArray(Charsets.UTF_8)); file.finishWrite(stream) }
            catch (failure: Exception) { file.failWrite(stream); throw failure }
        }
        identity = value
        return value
    }
}
