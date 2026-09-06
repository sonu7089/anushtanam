/*
 * Anushtanam - Vedic mantra recitation app
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.data.local

import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mantrasiddhi.data.local.dao.RecitationDao
import com.mantrasiddhi.data.local.dao.TargetDao
import com.mantrasiddhi.data.local.dao.UserDao
import com.mantrasiddhi.data.local.entity.RecitationSessionEntity
import com.mantrasiddhi.data.local.entity.TargetEntity
import com.mantrasiddhi.data.local.entity.UserProfileEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class MantraSiddhiDatabaseTest {

    private lateinit var db: MantraSiddhiDatabase
    private lateinit var userDao: UserDao
    private lateinit var targetDao: TargetDao
    private lateinit var recitationDao: RecitationDao

    @get:Rule
    val migrationHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        MantraSiddhiDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory()
    )

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, MantraSiddhiDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        userDao = db.userDao()
        targetDao = db.targetDao()
        recitationDao = db.recitationDao()
    }

    @After
    @Throws(IOException::class)
    fun closeDb() {
        db.close()
    }

    @Test
    fun should_insertAndGetUserProfile_when_validEntity() = runTest {
        val user = UserProfileEntity(id = 1, name = "Arjun", profileType = "INDIVIDUAL", parentId = null, avatarIndex = 0)
        userDao.insert(user)
        val loaded = userDao.getById(1)
        assertNotNull(loaded)
        assertEquals("Arjun", loaded?.name)
    }

    @Test
    fun should_insertAndGetActiveTarget_when_validEntity() = runTest {
        val target = TargetEntity(
            id = 1,
            userId = 2,
            setByUserId = 2,
            targetRecitationCount = 108,
            currentRecitationCount = 0,
            rewardType = "OTHER",
            rewardDescription = "",
            isCompleted = false
        )
        targetDao.insert(target)
        val loaded = targetDao.getActiveForUser(2)
        assertNotNull(loaded)
        assertEquals(108, loaded?.targetRecitationCount)
    }

    @Test
    fun should_insertAndGetRecitationSession_when_validEntity() = runTest {
        val session = RecitationSessionEntity(
            id = 1,
            userId = 2,
            mantraId = "gayatri",
            mode = "NORMAL",
            overallScore = 0.95,
            wordScoresJson = "om:0.95",
            transcription = "om",
            isCorrect = true,
            durationMs = 3000,
            createdAt = System.currentTimeMillis()
        )
        recitationDao.insert(session)
        val sessions = recitationDao.getSessionsForUser(2).first()
        assertEquals(1, sessions.size)
        assertEquals("om", sessions[0].transcription)
    }

    @Test
    fun should_updateTargetCurrentCount_when_recitationsDone() = runTest {
        val target = TargetEntity(
            id = 1,
            userId = 2,
            setByUserId = 2,
            targetRecitationCount = 10,
            currentRecitationCount = 0,
            rewardType = "OTHER",
            rewardDescription = "",
            isCompleted = false
        )
        targetDao.insert(target)
        val updated = target.copy(currentRecitationCount = 5)
        targetDao.update(updated)
        val loaded = targetDao.getActiveForUser(2)
        assertEquals(5, loaded?.currentRecitationCount)
    }

    @Test
    fun should_returnChildrenOfParent_when_childrenExist() = runTest {
        val parent = UserProfileEntity(id = 10, name = "Meera", profileType = "PARENT", parentId = null, avatarIndex = 1)
        val child = UserProfileEntity(id = 11, name = "Aarav", profileType = "CHILD", parentId = 10, avatarIndex = 2)
        userDao.insert(parent)
        userDao.insert(child)
        val children = userDao.getChildrenOf(10).first()
        assertEquals(1, children.size)
        assertEquals("Aarav", children[0].name)
    }

    /**
     * Verifies the v4 → v5 migration: the `savings` table is dropped, the `targets`
     * table gains the reward columns (and loses `moneyAmount`), and legacy rows with a
     * positive money amount are mapped to a MONEY reward with a formatted description.
     */
    @Test
    @Throws(IOException::class)
    fun migrate4To5_dropsSavings_andMapsTargetRewards() {
        val dbName = "migration-test-4-5"

        // Create the v4 schema and seed a money target + a savings row.
        migrationHelper.createDatabase(dbName, 4).apply {
            execSQL(
                "INSERT INTO targets (id, userId, setByUserId, targetRecitationCount, " +
                    "currentRecitationCount, moneyAmount, isCompleted) " +
                    "VALUES (1, 2, 2, 108, 40, 150.0, 0)"
            )
            execSQL(
                "INSERT INTO targets (id, userId, setByUserId, targetRecitationCount, " +
                    "currentRecitationCount, moneyAmount, isCompleted) " +
                    "VALUES (2, 3, 3, 108, 10, 0.0, 0)"
            )
            execSQL(
                "INSERT INTO savings (userId, totalSaved, totalLocked, totalUnlocked) " +
                    "VALUES (2, 10.0, 5.0, 5.0)"
            )
            close()
        }

        // Run the migration and validate against the generated v5 schema.
        val migratedDb = migrationHelper.runMigrationsAndValidate(
            dbName,
            5,
            true,
            MantraSiddhiDatabase.MIGRATION_4_5
        )

        // savings table is gone.
        migratedDb.query(
            "SELECT name FROM sqlite_master WHERE type='table' AND name='savings'"
        ).use { cursor ->
            assertFalse(cursor.moveToFirst())
        }

        // targets no longer has moneyAmount but does have the reward columns.
        migratedDb.query("PRAGMA table_info(targets)").use { cursor ->
            val columns = mutableSetOf<String>()
            val nameIdx = cursor.getColumnIndex("name")
            while (cursor.moveToNext()) {
                columns.add(cursor.getString(nameIdx))
            }
            assertFalse(columns.contains("moneyAmount"))
            assertTrue(columns.contains("rewardType"))
            assertTrue(columns.contains("rewardDescription"))
        }

        // Legacy money row mapped to a MONEY reward with a "₹" description.
        migratedDb.query(
            "SELECT rewardType, rewardDescription FROM targets WHERE id = 1"
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("MONEY", cursor.getString(0))
            assertEquals("₹150.00", cursor.getString(1))
        }

        // Zero-money row mapped to an unset OTHER reward.
        migratedDb.query(
            "SELECT rewardType, rewardDescription FROM targets WHERE id = 2"
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("OTHER", cursor.getString(0))
            assertEquals("", cursor.getString(1))
        }
        migratedDb.close()
    }

    /**
     * Verifies the v5 → v6 migration: the `purashcharana` table and its userId index are
     * created, and the additive migration leaves existing target data untouched. A
     * Purashcharana spans years, so an upgrade that dropped user data here would be
     * unrecoverable.
     */
    @Test
    @Throws(IOException::class)
    fun migrate5To6_addsPurashcharanaTable() {
        val dbName = "migration-test-5-6"

        // Create the v5 schema and seed a target that must survive the upgrade.
        migrationHelper.createDatabase(dbName, 5).apply {
            execSQL(
                "INSERT INTO targets (id, userId, setByUserId, targetRecitationCount, " +
                    "currentRecitationCount, rewardType, rewardDescription, isCompleted) " +
                    "VALUES (1, 2, 2, 108, 40, 'TOY', 'New toy car', 0)"
            )
            close()
        }

        // Run the migration and validate against the generated v6 schema.
        val migratedDb = migrationHelper.runMigrationsAndValidate(
            dbName,
            6,
            true,
            MantraSiddhiDatabase.MIGRATION_5_6
        )

        // purashcharana exists with exactly the sankalpa columns.
        migratedDb.query("PRAGMA table_info(purashcharana)").use { cursor ->
            val columns = mutableSetOf<String>()
            val nameIdx = cursor.getColumnIndex("name")
            while (cursor.moveToNext()) {
                columns.add(cursor.getString(nameIdx))
            }
            assertEquals(
                setOf(
                    "id", "userId", "mantraAksharaCount", "targetJapaCount",
                    "currentJapaCount", "startedAtMillis", "completedAtMillis", "isActive"
                ),
                columns
            )
        }

        // The userId index is there: it is hit on every verified recitation.
        migratedDb.query(
            "SELECT name FROM sqlite_master WHERE type='index' AND name='index_purashcharana_userId'"
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
        }

        // Pre-existing target data survived untouched.
        migratedDb.query(
            "SELECT currentRecitationCount, rewardDescription FROM targets WHERE id = 1"
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(40, cursor.getInt(0))
            assertEquals("New toy car", cursor.getString(1))
        }
        migratedDb.close()
    }

    /**
     * Verifies the v6 -> v7 migration: both `recitation_sessions` and `purashcharana` gain a
     * `mantraId`, existing rows are backfilled with the mantra the app shipped with, and
     * nothing else moves.
     *
     * The backfill is the load-bearing part. A sadhak's word history and, worse, a
     * Purashcharana measured in years are keyed to a mantra from this version on; if these
     * rows came out with no mantra (or the wrong one) the history would read as belonging
     * to a mantra that was never recited and the sankalpa could not be reconciled.
     */
    @Test
    @Throws(IOException::class)
    fun migrate6To7_addsMantraId_andBackfillsExistingRows() {
        val dbName = "migration-test-6-7"

        // Create the v6 schema and seed a session and a sankalpa that must survive.
        migrationHelper.createDatabase(dbName, 6).apply {
            execSQL(
                "INSERT INTO recitation_sessions (id, userId, mode, overallScore, " +
                    "wordScoresJson, transcription, isCorrect, durationMs, createdAt, " +
                    "correctness, completeness, pronunciation) " +
                    "VALUES (1, 2, 'NORMAL', 0.9, 'om:0.9', 'om', 1, 5000, 1700000000000, " +
                    "0.9, 0.9, 0.9)"
            )
            execSQL(
                "INSERT INTO purashcharana (id, userId, mantraAksharaCount, targetJapaCount, " +
                    "currentJapaCount, startedAtMillis, completedAtMillis, isActive) " +
                    "VALUES (1, 2, 24, 2400000, 500000, 1700000000000, NULL, 1)"
            )
            close()
        }

        // Run the migration and validate against the generated v7 schema.
        val migratedDb = migrationHelper.runMigrationsAndValidate(
            dbName,
            7,
            true,
            MantraSiddhiDatabase.MIGRATION_6_7
        )

        // The existing session is now attributed to the mantra it was actually recited from.
        migratedDb.query(
            "SELECT mantraId, overallScore FROM recitation_sessions WHERE id = 1"
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("gayatri", cursor.getString(0))
            assertEquals(0.9, cursor.getDouble(1), 0.0001)
        }

        // The sankalpa keeps every japa already recorded, and now names its mantra.
        migratedDb.query(
            "SELECT mantraId, currentJapaCount, startedAtMillis FROM purashcharana WHERE id = 1"
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("gayatri", cursor.getString(0))
            assertEquals(500000L, cursor.getLong(1))
            assertEquals(1700000000000L, cursor.getLong(2))
        }
        migratedDb.close()
    }
}
