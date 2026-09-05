/*
 * Anushtanam - Gayatri Mantra Recitation App
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.data.local

import android.content.Context
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
        PurashcharanaEntity::class
    ],
    version = 6,
    exportSchema = true
)
abstract class MantraSiddhiDatabase : RoomDatabase() {

    abstract fun userDao(): UserDao
    abstract fun targetDao(): TargetDao
    abstract fun recitationDao(): RecitationDao
    abstract fun sandhyaLogDao(): SandhyaLogDao
    abstract fun purashcharanaDao(): PurashcharanaDao

    companion object {
        const val DATABASE_NAME = "mantra_siddhi_db"

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

        fun buildDatabase(
            context: Context,
            userDaoProvider: Provider<UserDao>
        ): MantraSiddhiDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                MantraSiddhiDatabase::class.java,
                DATABASE_NAME
            )
            // Contiguous v1→v6 migration chain. No destructive fallback: a missing or
            // broken migration must fail loudly in testing rather than silently wipe
            // a published user's recitation history, targets, Sandhya logs, and the
            // years of japa recorded against a Purashcharana.
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
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
