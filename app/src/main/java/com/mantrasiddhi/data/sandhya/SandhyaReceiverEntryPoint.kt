/*
 * Anushtanam - Gayatri Mantra Recitation App
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.data.sandhya

import android.content.Context
import com.mantrasiddhi.data.preferences.SandhyaPreferences
import com.mantrasiddhi.domain.repository.SandhyaLogRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/**
 * Hilt access for the Sandhya broadcast receivers. Receivers resolve their
 * dependencies through this entry point rather than `@AndroidEntryPoint` field
 * injection, which keeps them simple and avoids the abstract `onReceive` super-call.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface SandhyaReceiverEntryPoint {
    fun scheduler(): SandhyaScheduler
    fun notifier(): SandhyaNotifier
    fun logRepository(): SandhyaLogRepository
    fun preferences(): SandhyaPreferences

    companion object {
        fun from(context: Context): SandhyaReceiverEntryPoint =
            EntryPointAccessors.fromApplication(
                context.applicationContext,
                SandhyaReceiverEntryPoint::class.java
            )
    }
}
