/*
 * Anushtanam - Vedic mantra recitation app
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.di

import com.mantrasiddhi.data.diagnostics.OnDeviceEvaluationDiagnostics
import com.mantrasiddhi.domain.evaluation.diagnostics.EvaluationDiagnostics
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Binds the on-device evaluation telemetry implementation to its domain port so the
 * evaluation use case depends only on the abstraction (§5.4).
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class DiagnosticsModule {

    @Binds
    @Singleton
    abstract fun bindEvaluationDiagnostics(
        impl: OnDeviceEvaluationDiagnostics
    ): EvaluationDiagnostics
}
