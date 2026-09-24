package com.mantrasiddhi.data.sync

import androidx.room.withTransaction
import com.google.firebase.auth.FirebaseAuth
import com.mantrasiddhi.data.local.MantraSiddhiDatabase
import com.mantrasiddhi.data.local.entity.BackupQueueState
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

data class AccountRecordSnapshot(val uid: String?, val records: Map<String, Map<String, Any?>> = emptyMap())

/** Account-scoped settings and editable drafts share the durable practice outbox. */
@Singleton
class AccountRecordStore @Inject constructor(
    private val database: MantraSiddhiDatabase,
    private val outbox: CloudSyncOutbox,
    private val auth: FirebaseAuth
) {
    val currentUid: String? get() = auth.currentUser?.uid
    private val accounts: Flow<String?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { trySend(it.currentUser?.uid) }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }.distinctUntilChanged()

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun observe(): Flow<AccountRecordSnapshot> = accounts.flatMapLatest { uid ->
        if (uid == null) flowOf(AccountRecordSnapshot(null)) else database.backupDao().observeAccountRecords(uid).map { rows ->
            AccountRecordSnapshot(uid, rows.associate { row ->
                val record = CloudSyncPolicy.decode(row.payload)
                record.text("id") to data(record)
            })
        }
    }

    suspend fun owner(userId: Long): String = database.userDao().getById(userId)?.firebaseUid ?: "local:$userId"
    suspend fun get(uid: String, id: String): Map<String, Any?>? =
        database.backupDao().queued(uid, "account:record:$id")?.let(CloudSyncPolicy::decode)

    suspend fun put(uid: String, id: String, value: Map<String, Any?>, seed: Boolean = false) {
        val changed = database.withTransaction {
            val old = get(uid, id)
            if (seed && old != null || old != null && BackupFingerprint.encode(data(old)) == BackupFingerprint.encode(value)) return@withTransaction false
            // A terminal draft marker always wins over a delayed editor write.
            if (id.startsWith("draft_") && old?.get("deleted") == true) return@withTransaction false
            val record = mapOf("id" to id, "updatedAtMillis" to if (seed) 0L else
                maxOf(System.currentTimeMillis(), Math.addExact(old?.number("updatedAtMillis") ?: 0, 1)),
                "operationId" to UUID.randomUUID().toString(), "deleted" to (value["deleted"] == true),
                "dataJson" to BackupFingerprint.encode(value))
            require(AccountRecordPolicy.validate(id, record) == null)
            database.backupDao().putQueued(BackupQueueState(uid, "account:record:$id", BackupFingerprint.encode(record)))
            if (!uid.startsWith("local:")) outbox.enqueueInTransaction(uid, SyncKind.ACCOUNT_RECORD, record)
            true
        }
        if (changed && !uid.startsWith("local:")) outbox.schedule()
    }

    suspend fun preference(key: String, value: Any?): Boolean {
        val uid = currentUid ?: return false
        put(uid, "preference_$key", mapOf("value" to value))
        return true
    }

    /** Import only records owned by this local profile; never another account's settings. */
    suspend fun importLocal(userId: Long, uid: String) {
        for (row in database.backupDao().accountRecords("local:$userId")) {
            val record = CloudSyncPolicy.decode(row.payload)
            put(uid, record.text("id"), data(record), seed = true)
        }
    }

    suspend fun merge(uid: String, record: Map<String, Any?>): Boolean {
        val id = record.text("id")
        require(AccountRecordPolicy.validate(id, record) == null)
        val old = get(uid, id)
        val authoritativeSeed = record.number("updatedAtMillis") == 0L && old?.number("updatedAtMillis") == 0L &&
            old["deleted"] != true
        if (!authoritativeSeed && !AccountRecordPolicy.newer(record, old)) return false
        database.backupDao().putQueued(BackupQueueState(uid, "account:record:$id", BackupFingerprint.encode(record)))
        return true
    }

    companion object {
        fun data(record: Map<String, Any?>): Map<String, Any?> = CloudSyncPolicy.decode(record.text("dataJson"))
    }
}

internal object AccountRecordPolicy {
    private val preferences = setOf("theme_mode", "language_display", "recitation_sensitivity", "advanced_vedic_mode",
        "sandhya_enabled", "sandhya_preparation", "sandhya_event", "sandhya_region", "minimum_PRATAH", "minimum_MADHYAHNA", "minimum_SAYAM")

    fun newer(next: Map<String, Any?>, old: Map<String, Any?>?): Boolean {
        if (old == null) return true
        if (next.text("id").startsWith("draft_") && next["deleted"] != old["deleted"]) return next["deleted"] == true
        // A bootstrap only fills a missing value, regardless of installation clock.
        if (next.number("updatedAtMillis") == 0L) return false
        return next.number("updatedAtMillis") > old.number("updatedAtMillis") ||
            next.number("updatedAtMillis") == old.number("updatedAtMillis") && next.text("operationId") > old.text("operationId")
    }

    fun validate(id: String, record: Map<String, Any?>): String? = runCatching {
        require(record.keys == setOf("id", "updatedAtMillis", "operationId", "deleted", "dataJson"))
        require(record["id"] == id && id.matches(Regex("[A-Za-z0-9_-]{1,200}")))
        require(record["updatedAtMillis"] is Long || record["updatedAtMillis"] is Int)
        require(record.number("updatedAtMillis") in 0..253402300799999L)
        require(record.text("operationId").matches(Regex("[A-Za-z0-9_-]{1,200}")))
        require(record["deleted"] is Boolean && record.text("dataJson").length in 2..199999)
        val d = AccountRecordStore.data(record)
        when {
            id.startsWith("preference_") -> {
                require(record["deleted"] == false && d.keys == setOf("value"))
                val key = id.removePrefix("preference_")
                require(key in preferences)
                val v = d["value"]
                when {
                    key == "theme_mode" -> require(v in setOf("SYSTEM", "LIGHT", "DARK"))
                    key == "language_display" -> require(v in setOf("BOTH", "DEVANAGARI", "TRANSLITERATION"))
                    key == "recitation_sensitivity" -> require(v is Number && v.toDouble().isFinite() && v.toDouble() in 0.0..1.0)
                    key.startsWith("minimum_") -> require((v is Long || v is Int) && (v as Number).toLong() >= 0)
                    key == "sandhya_region" -> {
                        @Suppress("UNCHECKED_CAST") val region = v as Map<String, Any?>
                        require(region.keys == setOf("id", "name", "latitude", "longitude", "zoneId"))
                        require(region.text("id").length in 1..200 && region.text("name").length in 1..200)
                        require((region["latitude"] as Number).toDouble() in -90.0..90.0)
                        require((region["longitude"] as Number).toDouble() in -180.0..180.0)
                        if (region.text("zoneId").isNotEmpty()) java.time.ZoneId.of(region.text("zoneId"))
                    }
                    else -> require(v is Boolean)
                }
            }
            id == "profile" -> {
                require(record["deleted"] == false && d.keys == setOf("name", "avatarIndex", "useGooglePhoto"))
                require(d["name"] is String && d.text("name").length <= 200)
                require(d["avatarIndex"] is Int || d["avatarIndex"] is Long)
                require(d.number("avatarIndex") in 0..Int.MAX_VALUE.toLong() && d["useGooglePhoto"] is Boolean)
            }
            id.startsWith("draft_") -> {
                if (record["deleted"] == true) require(d == mapOf("deleted" to true)) else {
                    require(d.keys == setOf("id", "dateKey", "period", "zoneId", "mode", "input", "baseQuantity", "baseRevision", "updatedAtMillis"))
                    require(id == "draft_${d.text("id")}")
                    com.mantrasiddhi.domain.util.SandhyaDateKey.parse(d.text("dateKey"))
                    java.time.ZoneId.of(d.text("zoneId"))
                    require(d["period"] in setOf("PRATAH", "MADHYAHNA", "SAYAM"))
                    require(d["mode"] in setOf("RECORD", "ADD", "EDIT", "COUNTER"))
                    require(d["input"] is String && d.text("input").length <= 1000)
                    for (key in listOf("baseRevision", "updatedAtMillis", "baseQuantity")) {
                        if (key == "baseQuantity" && d[key] == null) continue
                        require((d[key] is Long || d[key] is Int) && d.number(key) >= 0)
                    }
                }
            }
            else -> error("Unknown account record")
        }
    }.exceptionOrNull()?.let { "Invalid account record: ${it.message.orEmpty()}" }
}
