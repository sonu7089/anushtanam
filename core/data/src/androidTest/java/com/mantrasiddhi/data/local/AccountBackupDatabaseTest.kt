package com.mantrasiddhi.data.local

import android.content.Context
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.mantrasiddhi.data.SessionManager
import com.mantrasiddhi.data.local.entity.UserProfileEntity
import com.mantrasiddhi.data.preferences.SandhyaPreferences
import com.mantrasiddhi.data.preferences.UserPreferencesDataStore
import com.mantrasiddhi.data.sync.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

/** Exercises disk transactions and restoration without contacting Firebase. */
class AccountBackupDatabaseTest {
    @Test fun rollbackRestoreReplayAndDraftDeletionPreserveOwnership() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val app = FirebaseApp.initializeApp(context, FirebaseOptions.Builder().setProjectId("demo-account-backup")
            .setApplicationId("1:123:android:account-backup").setApiKey("fake-emulator-key").build(), UUID.randomUUID().toString())
        val db = Room.inMemoryDatabaseBuilder(context, MantraSiddhiDatabase::class.java).build()
        val outbox = CloudSyncOutbox(context, db.cloudSyncDao(), CloudSyncScheduler(context))
        val store = AccountRecordStore(db, outbox, FirebaseAuth.getInstance(app))
        val bridge = AccountBackup(db, store, UserPreferencesDataStore(context, store),
            SandhyaPreferences(context, store), SessionManager())
        try {
            db.userDao().insert(UserProfileEntity(7, "Original", "INDIVIDUAL", null, 0, "owner"))
            db.userDao().insert(UserProfileEntity(8, "Other", "INDIVIDUAL", null, 0, "other"))
            try {
                db.withTransaction {
                    store.put("owner", "preference_minimum_PRATAH", mapOf("value" to 108L))
                    error("Simulated crash before commit")
                }
            } catch (_: IllegalStateException) { }
            assertNull(store.get("owner", "preference_minimum_PRATAH"))
            assertFalse(db.cloudSyncDao().hasPending("owner"))
            store.put("owner", "preference_minimum_PRATAH", mapOf("value" to Long.MAX_VALUE))
            store.put("owner", "preference_minimum_PRATAH", mapOf("value" to Long.MAX_VALUE))
            assertEquals(1, db.cloudSyncDao().pending("owner").size)
            assertNull(store.get("other", "preference_minimum_PRATAH"))

            fun wire(id: String, time: Long, data: Map<String, Any?>, deleted: Boolean = false) = mapOf(
                "id" to id, "updatedAtMillis" to time, "operationId" to "remote", "deleted" to deleted,
                "dataJson" to BackupFingerprint.encode(data))
            val profile = wire("profile", 100, mapOf("name" to "Restored", "avatarIndex" to 3, "useGooglePhoto" to false))
            db.withTransaction { bridge.merge("owner", 7, profile); bridge.merge("owner", 7, profile) }
            assertEquals("Restored", db.userDao().getById(7)!!.name)
            assertEquals("Other", db.userDao().getById(8)!!.name)
            val draft = wire("draft_one", 200, mapOf("id" to "one", "dateKey" to "20260910", "period" to "SAYAM",
                "zoneId" to "UTC", "mode" to "RECORD", "input" to "108", "baseQuantity" to null,
                "baseRevision" to 0, "updatedAtMillis" to 200L))
            assertNull(AccountRecordPolicy.validate("draft_one", draft))
            db.withTransaction { bridge.merge("owner", 7, draft) }
            assertEquals("108", db.sandhyaCountDao().getDraft(7)!!.input)
            assertNull(db.sandhyaCountDao().getDraft(8))
            val tombstone = wire("draft_one", 100, mapOf("deleted" to true), true)
            db.withTransaction {
                bridge.merge("owner", 7, tombstone)
                bridge.merge("owner", 7, draft + ("updatedAtMillis" to 999L))
            }
            assertNull(db.sandhyaCountDao().getDraft(7))
            // Remote replay must not generate uploads.
            assertEquals(1, db.cloudSyncDao().pending("owner").size)
        } finally { db.close(); app.delete() }
    }
}
