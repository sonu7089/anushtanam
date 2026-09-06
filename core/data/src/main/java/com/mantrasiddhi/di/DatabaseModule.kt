/*
 * Anushtanam - Vedic mantra recitation app
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.di

import android.content.Context
import com.mantrasiddhi.data.local.MantraSiddhiDatabase
import com.mantrasiddhi.data.local.dao.PurashcharanaDao
import com.mantrasiddhi.data.local.dao.RecitationDao
import com.mantrasiddhi.data.local.dao.SandhyaLogDao
import com.mantrasiddhi.data.local.dao.TargetDao
import com.mantrasiddhi.data.local.dao.UserDao
import com.mantrasiddhi.data.local.dao.CloudSyncDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import javax.inject.Provider
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context,
        userDaoProvider: Provider<UserDao>
    ): MantraSiddhiDatabase {
        return MantraSiddhiDatabase.buildDatabase(context, userDaoProvider)
    }

    @Provides
    fun provideUserDao(db: MantraSiddhiDatabase): UserDao {
        return db.userDao()
    }

    @Provides
    fun provideTargetDao(db: MantraSiddhiDatabase): TargetDao {
        return db.targetDao()
    }

    @Provides
    fun provideRecitationDao(db: MantraSiddhiDatabase): RecitationDao {
        return db.recitationDao()
    }

    @Provides
    fun provideSandhyaLogDao(db: MantraSiddhiDatabase): SandhyaLogDao {
        return db.sandhyaLogDao()
    }

    @Provides
    fun providePurashcharanaDao(db: MantraSiddhiDatabase): PurashcharanaDao {
        return db.purashcharanaDao()
    }

    @Provides
    fun provideIoDispatcher(): CoroutineDispatcher {
        return Dispatchers.IO
    }

    @Provides
    fun provideCloudSyncDao(db: MantraSiddhiDatabase): CloudSyncDao = db.cloudSyncDao()
}
