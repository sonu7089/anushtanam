/*
 * Anushtanam - Vedic mantra recitation app
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.di

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.ktx.messaging
import com.mantrasiddhi.data.remote.FirebaseAuthRepositoryImpl
import com.mantrasiddhi.data.remote.FirestoreGoalRepositoryImpl
import com.mantrasiddhi.data.remote.FirestoreLinkRepositoryImpl
import com.mantrasiddhi.data.remote.FirestoreProgressRepositoryImpl
import com.mantrasiddhi.data.remote.FirestorePurashcharanaSyncRepositoryImpl
import com.mantrasiddhi.data.remote.FirestoreTargetSyncRepositoryImpl
import com.mantrasiddhi.domain.repository.AuthRepository
import com.mantrasiddhi.domain.repository.GoalRepository
import com.mantrasiddhi.domain.repository.LinkRepository
import com.mantrasiddhi.domain.repository.ProgressSyncRepository
import com.mantrasiddhi.domain.repository.PurashcharanaSyncRepository
import com.mantrasiddhi.domain.repository.TargetSyncRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Provides the Firebase SDK singletons and binds the Phase B cloud repositories.
 */
@Module
@InstallIn(SingletonComponent::class)
object FirebaseProvidesModule {

    @Provides
    @Singleton
    fun provideFirebaseAuth(): FirebaseAuth = Firebase.auth

    @Provides
    @Singleton
    fun provideFirebaseFirestore(): FirebaseFirestore = Firebase.firestore

    @Provides
    @Singleton
    fun provideFirebaseMessaging(): FirebaseMessaging = Firebase.messaging
}

@Module
@InstallIn(SingletonComponent::class)
abstract class FirebaseBindsModule {

    @Binds
    @Singleton
    abstract fun bindAuthRepository(impl: FirebaseAuthRepositoryImpl): AuthRepository

    @Binds
    @Singleton
    abstract fun bindLinkRepository(impl: FirestoreLinkRepositoryImpl): LinkRepository

    @Binds
    @Singleton
    abstract fun bindGoalRepository(impl: FirestoreGoalRepositoryImpl): GoalRepository

    @Binds
    @Singleton
    abstract fun bindProgressSyncRepository(impl: FirestoreProgressRepositoryImpl): ProgressSyncRepository

    @Binds
    @Singleton
    abstract fun bindTargetSyncRepository(impl: FirestoreTargetSyncRepositoryImpl): TargetSyncRepository

    @Binds
    @Singleton
    abstract fun bindPurashcharanaSyncRepository(
        impl: FirestorePurashcharanaSyncRepositoryImpl
    ): PurashcharanaSyncRepository
}
