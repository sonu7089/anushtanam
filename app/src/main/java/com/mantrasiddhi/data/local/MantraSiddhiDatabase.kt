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
import com.mantrasiddhi.data.local.dao.RecitationDao
import com.mantrasiddhi.data.local.dao.SavingsDao
import com.mantrasiddhi.data.local.dao.TargetDao
import com.mantrasiddhi.data.local.dao.UserDao
import com.mantrasiddhi.data.local.entity.RecitationSessionEntity
import com.mantrasiddhi.data.local.entity.SavingsEntity
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
        SavingsEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class MantraSiddhiDatabase : RoomDatabase() {

    abstract fun userDao(): UserDao
    abstract fun targetDao(): TargetDao
    abstract fun recitationDao(): RecitationDao
    abstract fun savingsDao(): SavingsDao

    companion object {
        const val DATABASE_NAME = "mantra_siddhi_db"

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

        fun buildDatabase(
            context: Context,
            userDaoProvider: Provider<UserDao>
        ): MantraSiddhiDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                MantraSiddhiDatabase::class.java,
                DATABASE_NAME
            )
            .addMigrations(MIGRATION_2_3)
            .fallbackToDestructiveMigration()
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
