/*
 * Anushtanam - Gayatri Mantra Recitation App
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.di

import com.mantrasiddhi.data.signal.AcousticAnalyzerImpl
import com.mantrasiddhi.domain.evaluation.acoustic.AcousticAnalyzer
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Binds the data-layer acoustic pipeline to the domain [AcousticAnalyzer] port so
 * the evaluation use case can depend only on the domain abstraction.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class AcousticModule {

    @Binds
    @Singleton
    abstract fun bindAcousticAnalyzer(
        impl: AcousticAnalyzerImpl
    ): AcousticAnalyzer
}
