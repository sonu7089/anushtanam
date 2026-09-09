/*
 * Anushtanam - Vedic mantra recitation app
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.data.local

import android.content.Context
import com.mantrasiddhi.data.local.entity.JapaEntryEntity
import com.mantrasiddhi.data.local.dao.JapaDao
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.mantrasiddhi.data.local.dao.PurashcharanaDao
import com.mantrasiddhi.data.local.dao.RecitationDao
import com.mantrasiddhi.data.local.dao.SandhyaLogDao
import com.mantrasiddhi.data.local.dao.TargetDao
import com.mantrasiddhi.data.local.dao.UserDao
import com.mantrasiddhi.data.local.dao.CloudSyncDao
import com.mantrasiddhi.data.local.dao.SandhyaCountDao
import com.mantrasiddhi.data.local.entity.SandhyaCountEntity
import com.mantrasiddhi.data.local.entity.SandhyaCountChangeEntity
import com.mantrasiddhi.data.local.entity.SandhyaCountDraftEntity
import com.mantrasiddhi.data.local.entity.CloudSyncEntity
import com.mantrasiddhi.data.local.entity.CloudSyncScheduleEntity
import com.mantrasiddhi.data.local.entity.PurashcharanaEntity
import com.mantrasiddhi.data.local.entity.RecitationSessionEntity
import com.mantrasiddhi.data.local.entity.SandhyaLogEntity
import com.mantrasiddhi.data.local.entity.TargetEntity
import com.mantrasiddhi.data.local.entity.UserProfileEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Provider

@Database(
    entities = [
        UserProfileEntity::class,
        TargetEntity::class,
        RecitationSessionEntity::class,
        SandhyaLogEntity::class,
        PurashcharanaEntity::class,
        CloudSyncEntity::class,
        CloudSyncScheduleEntity::class,
        SandhyaCountEntity::class,
        SandhyaCountChangeEntity::class,
        SandhyaCountDraftEntity::class,
        JapaEntryEntity::class
    ],
    version = 12,
    exportSchema = true
)
abstract class MantraSiddhiDatabase : RoomDatabase() {

    abstract fun japaDao(): JapaDao
    abstract fun userDao(): UserDao
    abstract fun targetDao(): TargetDao
    abstract fun recitationDao(): RecitationDao
    abstract fun sandhyaLogDao(): SandhyaLogDao
    abstract fun purashcharanaDao(): PurashcharanaDao
    abstract fun cloudSyncDao(): CloudSyncDao
    abstract fun sandhyaCountDao(): SandhyaCountDao

    companion object {
        const val DATABASE_NAME = "mantra_siddhi_db"

        /** Mantra recorded for rows that predate the multi-mantra catalog (v7). */
        private const val LEGACY_MANTRA_ID = RecitationSessionEntity.LEGACY_MANTRA_ID

        /**
         * v1 → v2: adds the `savings` table (introduced in v2). DDL matches Room's
         * generated schema for SavingsEntity so the identity-hash check passes and a
         * v1 device upgrades without losing its existing recitation/target data.
         */
        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `savings` (" +
                        "`userId` INTEGER NOT NULL, " +
                        "`totalSaved` REAL NOT NULL, " +
                        "`totalLocked` REAL NOT NULL, " +
                        "`totalUnlocked` REAL NOT NULL, " +
                        "PRIMARY KEY(`userId`))"
                )
            }
        }

        /**
         * v2 → v3: adds per-axis (correctness/completeness/pronunciation) scores to
         * recitation sessions (Phase 4.4) and backfills existing rows with the overall
         * score so historical trends remain coherent.
         */
        val MIGRATION_2_3: Migration = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE recitation_sessions ADD COLUMN correctness REAL NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE recitation_sessions ADD COLUMN completeness REAL NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE recitation_sessions ADD COLUMN pronunciation REAL NOT NULL DEFAULT 0")
                db.execSQL(
                    "UPDATE recitation_sessions SET correctness = overallScore, " +
                        "completeness = overallScore, pronunciation = overallScore"
                )
            }
        }

        /**
         * v3 → v4: adds the `sandhya_logs` table backing the Sandhya Vandan feature.
         * Column types/nullability and the index names match Room's generated schema
         * for [SandhyaLogEntity] exactly, so the runtime identity-hash check passes and
         * existing user data is preserved on upgrade.
         */
        val MIGRATION_3_4: Migration = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `sandhya_logs` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`userId` INTEGER NOT NULL, " +
                        "`period` TEXT NOT NULL, " +
                        "`scheduledEpochMillis` INTEGER NOT NULL, " +
                        "`status` TEXT NOT NULL, " +
                        "`observedEpochMillis` INTEGER, " +
                        "`dateKey` TEXT NOT NULL)"
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS " +
                        "`index_sandhya_logs_userId_dateKey_period` " +
                        "ON `sandhya_logs` (`userId`, `dateKey`, `period`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_sandhya_logs_userId` " +
                        "ON `sandhya_logs` (`userId`)"
                )
            }
        }

        /**
         * v4 → v5: removes the virtual-money/savings concept. Drops the `savings`
         * table entirely and reshapes `targets`, replacing the `moneyAmount REAL`
         * column with `rewardType TEXT NOT NULL` + `rewardDescription TEXT NOT NULL`.
         *
         * SQLite can't drop/replace a column in place on older API levels, so we use
         * the create-new-table / copy / drop / rename pattern. The new `targets_new`
         * DDL matches Room's generated v5 schema for [TargetEntity] EXACTLY (column
         * order, types, NOT NULL, primary key) so the runtime identity-hash check
         * passes. Legacy rows with `moneyAmount > 0` are mapped to a MONEY reward with
         * a formatted "₹" description; all others become an unset OTHER reward.
         */
        val MIGRATION_4_5: Migration = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP TABLE IF EXISTS `savings`")

                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `targets_new` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`userId` INTEGER NOT NULL, " +
                        "`setByUserId` INTEGER NOT NULL, " +
                        "`targetRecitationCount` INTEGER NOT NULL, " +
                        "`currentRecitationCount` INTEGER NOT NULL, " +
                        "`rewardType` TEXT NOT NULL, " +
                        "`rewardDescription` TEXT NOT NULL, " +
                        "`isCompleted` INTEGER NOT NULL)"
                )

                db.execSQL(
                    "INSERT INTO `targets_new` (" +
                        "`id`, `userId`, `setByUserId`, `targetRecitationCount`, " +
                        "`currentRecitationCount`, `rewardType`, `rewardDescription`, `isCompleted`) " +
                        "SELECT `id`, `userId`, `setByUserId`, `targetRecitationCount`, " +
                        "`currentRecitationCount`, " +
                        "CASE WHEN `moneyAmount` > 0 THEN 'MONEY' ELSE 'OTHER' END, " +
                        "CASE WHEN `moneyAmount` > 0 THEN '₹' || printf('%.2f', `moneyAmount`) ELSE '' END, " +
                        "`isCompleted` FROM `targets`"
                )

                db.execSQL("DROP TABLE `targets`")
                db.execSQL("ALTER TABLE `targets_new` RENAME TO `targets`")
            }
        }

        /**
         * v5 → v6: adds the `purashcharana` table backing the Purashcharana sankalpa
         * (one lakh japa per akshara). Purely additive — no existing table is touched,
         * so targets, sessions and Sandhya logs carry over untouched. Column types and
         * the index name match Room's generated v6 schema for [PurashcharanaEntity]
         * exactly, so the runtime identity-hash check passes.
         */
        val MIGRATION_5_6: Migration = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `purashcharana` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`userId` INTEGER NOT NULL, " +
                        "`mantraAksharaCount` INTEGER NOT NULL, " +
                        "`targetJapaCount` INTEGER NOT NULL, " +
                        "`currentJapaCount` INTEGER NOT NULL, " +
                        "`startedAtMillis` INTEGER NOT NULL, " +
                        "`completedAtMillis` INTEGER, " +
                        "`isActive` INTEGER NOT NULL)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_purashcharana_userId` " +
                        "ON `purashcharana` (`userId`)"
                )
            }
        }

        /**
         * v6 -> v7: records WHICH mantra a recitation and a sankalpa belong to.
         *
         * Purely additive: one `mantraId TEXT NOT NULL DEFAULT 'gayatri'` column on each
         * of `recitation_sessions` and `purashcharana`. Existing rows are backfilled by
         * the column default, which is correct because every row written before this
         * version was the Gayatri — the only mantra the app carried. The entities declare
         * the same `@ColumnInfo(defaultValue = ...)`, so the DDL a fresh install creates
         * and the DDL this migration produces agree and Room's identity-hash check passes.
         *
         * The literal is spelled out rather than read from the catalog on purpose: it is a
         * statement about rows already on disk, and must not change if the catalog's
         * default mantra ever does.
         */
        val MIGRATION_6_7: Migration = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE `recitation_sessions` " +
                        "ADD COLUMN `mantraId` TEXT NOT NULL DEFAULT '$LEGACY_MANTRA_ID'"
                )
                db.execSQL(
                    "ALTER TABLE `purashcharana` " +
                        "ADD COLUMN `mantraId` TEXT NOT NULL DEFAULT '$LEGACY_MANTRA_ID'"
                )
            }
        }

        val MIGRATION_7_8: Migration = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_recitation_sessions_userId_createdAt_id` " +
                    "ON `recitation_sessions` (`userId`, `createdAt`, `id`)")
            }
        }

        val MIGRATION_8_9: Migration = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `cloud_sync_outbox` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`uid` TEXT NOT NULL, `streamId` TEXT NOT NULL, `kind` TEXT NOT NULL, `payload` TEXT NOT NULL, `createdAtMs` INTEGER NOT NULL)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_cloud_sync_outbox_uid_id` ON `cloud_sync_outbox` (`uid`, `id`)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `cloud_sync_schedule` (`uid` TEXT NOT NULL, `nextAttemptAtMs` INTEGER NOT NULL, PRIMARY KEY(`uid`))")
            }
        }

        val MIGRATION_9_10: Migration = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE user_profiles ADD COLUMN firebaseUid TEXT")
                db.execSQL("ALTER TABLE user_profiles ADD COLUMN photoUrl TEXT")
                db.execSQL("ALTER TABLE user_profiles ADD COLUMN photoSource TEXT")
                db.execSQL("ALTER TABLE user_profiles ADD COLUMN useGooglePhoto INTEGER NOT NULL DEFAULT 1")
                db.execSQL("CREATE UNIQUE INDEX index_user_profiles_firebaseUid ON user_profiles(firebaseUid)")
            }
        }

        val MIGRATION_10_11: Migration = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // No backfill: an old observance says nothing about its japa quantity.
                db.execSQL("CREATE TABLE IF NOT EXISTS `sandhya_counts` (`userId` INTEGER NOT NULL, `dateKey` TEXT NOT NULL, `period` TEXT NOT NULL, `quantity` INTEGER NOT NULL, `revision` INTEGER NOT NULL, `zoneId` TEXT NOT NULL, PRIMARY KEY(`userId`, `dateKey`, `period`))")
                db.execSQL("CREATE TABLE IF NOT EXISTS `sandhya_count_changes` (`id` TEXT NOT NULL, `userId` INTEGER NOT NULL, `dateKey` TEXT NOT NULL, `period` TEXT NOT NULL, `zoneId` TEXT NOT NULL, `mode` TEXT NOT NULL, `previousQuantity` INTEGER, `quantity` INTEGER NOT NULL, `revision` INTEGER NOT NULL, `createdAtMillis` INTEGER NOT NULL, PRIMARY KEY(`id`))")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_sandhya_count_changes_userId_dateKey_period` ON `sandhya_count_changes` (`userId`, `dateKey`, `period`)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `sandhya_count_drafts` (`id` TEXT NOT NULL, `userId` INTEGER NOT NULL, `dateKey` TEXT NOT NULL, `period` TEXT NOT NULL, `zoneId` TEXT NOT NULL, `mode` TEXT NOT NULL, `input` TEXT NOT NULL, `baseQuantity` INTEGER, `baseRevision` INTEGER NOT NULL, `updatedAtMillis` INTEGER NOT NULL, PRIMARY KEY(`id`))")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_sandhya_count_drafts_userId` ON `sandhya_count_drafts` (`userId`)")
            }
        }

        val MIGRATION_11_12: Migration = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                for (table in listOf("targets", "purashcharana")) {
                    db.execSQL("ALTER TABLE $table ADD COLUMN stableId TEXT NOT NULL DEFAULT ''")
                    db.execSQL("ALTER TABLE $table ADD COLUMN endedAtMillis INTEGER")
                    db.execSQL("ALTER TABLE $table ADD COLUMN baselineCount INTEGER NOT NULL DEFAULT 0")
                    db.execSQL("UPDATE $table SET stableId = lower(hex(randomblob(16)))")
                }
                db.execSQL("UPDATE targets SET stableId = 'legacy-target' WHERE isCompleted = 0")
                db.execSQL("UPDATE purashcharana SET stableId = 'legacy-vow-' || startedAtMillis")
                db.execSQL("ALTER TABLE targets ADD COLUMN mantraId TEXT NOT NULL DEFAULT 'gayatri'")
                db.execSQL("ALTER TABLE targets ADD COLUMN startedAtMillis INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE targets ADD COLUMN isActive INTEGER NOT NULL DEFAULT 1")
                // The former target schema did not record enrollment. Do not invent old eligibility.
                db.execSQL("UPDATE targets SET baselineCount = currentRecitationCount, startedAtMillis = ${System.currentTimeMillis()}, isActive = CASE WHEN isCompleted = 1 THEN 0 ELSE 1 END, endedAtMillis = CASE WHEN isCompleted = 1 THEN ${System.currentTimeMillis()} ELSE NULL END")
                db.execSQL("UPDATE purashcharana SET baselineCount = currentJapaCount, endedAtMillis = CASE WHEN isActive = 0 THEN ${System.currentTimeMillis()} ELSE NULL END")
                for (column in listOf("targetCompleted", "vowCompleted", "completionReversed", "milestonesCrossed")) {
                    db.execSQL("ALTER TABLE sandhya_count_changes ADD COLUMN $column INTEGER NOT NULL DEFAULT 0")
                }
                db.execSQL("CREATE TABLE IF NOT EXISTS japa_entries (id TEXT NOT NULL, userId INTEGER NOT NULL, mantraId TEXT NOT NULL, dateKey TEXT NOT NULL, zoneId TEXT NOT NULL, period TEXT, quantity INTEGER NOT NULL, revision INTEGER NOT NULL, createdAtMillis INTEGER NOT NULL, targetId TEXT, vowId TEXT, source TEXT NOT NULL, sessionId INTEGER, updatedAtMillis INTEGER NOT NULL, operationId TEXT NOT NULL, PRIMARY KEY(id))")
                db.execSQL("CREATE INDEX index_japa_entries_userId_dateKey_period ON japa_entries(userId, dateKey, period)")
                db.execSQL("CREATE INDEX index_japa_entries_targetId ON japa_entries(targetId)")
                db.execSQL("CREATE INDEX index_japa_entries_vowId ON japa_entries(vowId)")
                // Existing MF-01 history stays editable, without importing it into a later vow.
                db.execSQL("INSERT INTO japa_entries SELECT 'legacy-sandhya:' || userId || ':' || dateKey || ':' || period, userId, 'gayatri', dateKey, zoneId, period, quantity, 1, 0, NULL, NULL, 'SANDHYA', NULL, 0, 'legacy-sandhya:' || userId || ':' || dateKey || ':' || period FROM sandhya_counts")
            }
        }

        fun buildDatabase(
            context: Context,
            userDaoProvider: Provider<UserDao>
        ): MantraSiddhiDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                MantraSiddhiDatabase::class.java,
                DATABASE_NAME
            )
            // Contiguous v1 to v12 migration chain. No destructive fallback: a missing or
            // broken migration must fail loudly in testing rather than silently wipe
            // a published user's recitation history, targets, Sandhya logs, and the
            // years of japa recorded against a Purashcharana.
            .addMigrations(
                MIGRATION_1_2,
                MIGRATION_2_3,
                MIGRATION_3_4,
                MIGRATION_4_5,
                MIGRATION_5_6,
                MIGRATION_6_7,
                MIGRATION_7_8,
                MIGRATION_8_9,
                MIGRATION_9_10,
                MIGRATION_10_11,
                MIGRATION_11_12
            )
            .addCallback(object : Callback() {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    super.onCreate(db)
                    // Seed initial demo data in a background coroutine
                    CoroutineScope(Dispatchers.IO).launch {
                        val userDao = userDaoProvider.get()
                        userDao.insertAll(
                            listOf(
                                UserProfileEntity(id = 1, name = "Arjun", profileType = "INDIVIDUAL", parentId = null, avatarIndex = 0),
                                UserProfileEntity(id = 2, name = "Meera", profileType = "PARENT", parentId = null, avatarIndex = 1),
                                UserProfileEntity(id = 3, name = "Aarav", profileType = "CHILD", parentId = 2, avatarIndex = 2),
                                UserProfileEntity(id = 4, name = "Priya", profileType = "INDIVIDUAL", parentId = null, avatarIndex = 3)
                            )
                        )
                    }
                }
            })
            .build()
        }
    }
}
