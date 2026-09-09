/*
 * Anushtanam - Vedic mantra recitation app
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.di

import com.mantrasiddhi.data.SessionManager
import com.mantrasiddhi.data.repository.AppPreferencesRepositoryImpl
import com.mantrasiddhi.data.repository.PurashcharanaRepositoryImpl
import com.mantrasiddhi.data.repository.RecitationRepositoryImpl
import com.mantrasiddhi.data.repository.SandhyaLogRepositoryImpl
import com.mantrasiddhi.data.repository.TargetRepositoryImpl
import com.mantrasiddhi.data.repository.UserRepositoryImpl
import com.mantrasiddhi.domain.repository.AppPreferencesRepository
import com.mantrasiddhi.domain.repository.PurashcharanaRepository
import com.mantrasiddhi.domain.repository.RecitationRepository
import com.mantrasiddhi.domain.repository.SandhyaLogRepository
import com.mantrasiddhi.domain.repository.SessionRepository
import com.mantrasiddhi.domain.repository.TargetRepository
import com.mantrasiddhi.domain.repository.UserRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindSandhyaCountRepository(
        impl: com.mantrasiddhi.data.repository.SandhyaCountRepositoryImpl
    ): com.mantrasiddhi.domain.repository.SandhyaCountRepository

    @Binds
    @Singleton
    abstract fun bindUserRepository(
        impl: UserRepositoryImpl
    ): UserRepository

    @Binds
    @Singleton
    abstract fun bindTargetRepository(
        impl: TargetRepositoryImpl
    ): TargetRepository

    @Binds
    @Singleton
    abstract fun bindRecitationRepository(
        impl: RecitationRepositoryImpl
    ): RecitationRepository

    @Binds
    @Singleton
    abstract fun bindSessionRepository(
        impl: SessionManager
    ): SessionRepository

    @Binds
    @Singleton
    abstract fun bindAppPreferencesRepository(
        impl: AppPreferencesRepositoryImpl
    ): AppPreferencesRepository

    @Binds
    @Singleton
    abstract fun bindSandhyaLogRepository(
        impl: SandhyaLogRepositoryImpl
    ): SandhyaLogRepository

    @Binds
    @Singleton
    abstract fun bindPurashcharanaRepository(
        impl: PurashcharanaRepositoryImpl
    ): PurashcharanaRepository
}
