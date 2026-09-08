package com.mantrasiddhi.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GoogleProfileMigrationTest {
    @get:Rule val migrations = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(),
        MantraSiddhiDatabase::class.java, emptyList(), FrameworkSQLiteOpenHelperFactory())

    @Test fun upgradeKeepsProfilesFamilyHistoryAndPendingUploads() {
        val name = "google-profile-migration"
        migrations.createDatabase(name, 9).apply {
            execSQL("INSERT INTO user_profiles VALUES (7, 'Meera', 'PARENT', NULL, 2)")
            execSQL("INSERT INTO user_profiles VALUES (8, 'Aarav', 'CHILD', 7, 1)")
            execSQL("INSERT INTO targets VALUES (1, 7, 7, 108, 32, 'OTHER', '', 0)")
            execSQL("INSERT INTO recitation_sessions (id, userId, mantraId, mode, overallScore, wordScoresJson, transcription, isCorrect, durationMs, createdAt, correctness, completeness, pronunciation) " +
                "VALUES (1, 7, 'gayatri', 'NORMAL', 0.9, '{}', 'om', 1, 5000, 1700000000000, 0.9, 0.9, 0.9)")
            execSQL("INSERT INTO purashcharana (id, userId, mantraId, mantraAksharaCount, targetJapaCount, currentJapaCount, startedAtMillis, completedAtMillis, isActive) " +
                "VALUES (1, 7, 'gayatri', 24, 2400000, 500000, 1700000000000, NULL, 1)")
            execSQL("INSERT INTO sandhya_logs VALUES (1, 7, 'PRATAH', 1700000000000, 'OBSERVED', 1700000001000, '2023-11-14')")
            execSQL("INSERT INTO cloud_sync_outbox VALUES (1, 'original-owner', 'device', 'target_increment', '{}', 0)")
            execSQL("INSERT INTO cloud_sync_schedule VALUES ('original-owner', 60000)")
            close()
        }
        migrations.runMigrationsAndValidate(name, 10, true, MantraSiddhiDatabase.MIGRATION_9_10).use { db ->
            db.query("SELECT name, avatarIndex, firebaseUid, photoUrl, photoSource, useGooglePhoto FROM user_profiles WHERE id = 7").use {
                assertTrue(it.moveToFirst())
                assertEquals("Meera", it.getString(0)); assertEquals(2, it.getInt(1))
                assertTrue(it.isNull(2)); assertTrue(it.isNull(3)); assertTrue(it.isNull(4)); assertEquals(1, it.getInt(5))
            }
            val unchanged = mapOf(
                "SELECT parentId FROM user_profiles WHERE id = 8" to 7L,
                "SELECT currentRecitationCount FROM targets WHERE id = 1" to 32L,
                "SELECT userId FROM recitation_sessions WHERE id = 1" to 7L,
                "SELECT currentJapaCount FROM purashcharana WHERE id = 1" to 500000L,
                "SELECT observedEpochMillis FROM sandhya_logs WHERE id = 1" to 1700000001000L,
                "SELECT nextAttemptAtMs FROM cloud_sync_schedule WHERE uid = 'original-owner'" to 60000L
            )
            unchanged.forEach { (query, expected) -> db.query(query).use {
                assertTrue(it.moveToFirst()); assertEquals(expected, it.getLong(0))
            } }
            db.query("SELECT uid FROM cloud_sync_outbox WHERE id = 1").use {
                assertTrue(it.moveToFirst()); assertEquals("original-owner", it.getString(0))
            }
            db.execSQL("UPDATE user_profiles SET firebaseUid = 'original-owner', photoSource = 'GOOGLE', useGooglePhoto = 0 WHERE id = 7")
            assertTrue(runCatching {
                db.execSQL("UPDATE user_profiles SET firebaseUid = 'original-owner' WHERE id = 8")
            }.isFailure)
        }
    }
}
