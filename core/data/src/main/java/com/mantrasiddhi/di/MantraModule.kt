/*
 * Anushtanam - Vedic mantra recitation app
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.di

import com.mantrasiddhi.domain.mantra.ActiveMantra
import com.mantrasiddhi.domain.mantra.BundledMantraCatalog
import com.mantrasiddhi.domain.mantra.Mantra
import com.mantrasiddhi.domain.mantra.MantraCatalog
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Supplies the mantra catalog and the mantra the app is currently teaching.
 *
 * This is the single place the app decides *which* mantra everything else works on. Today
 * it is whichever one `mantras/catalog.json` names as the default, which makes switching
 * mantras a one-line change to a data file. If the app ever lets the sadhak choose, only
 * [provideActiveMantra] changes — every scorer and use case already takes the mantra as a
 * dependency rather than reaching for a global.
 */
@Module
@InstallIn(SingletonComponent::class)
object MantraModule {

    @Provides
    @Singleton
    fun provideMantraCatalog(): MantraCatalog = BundledMantraCatalog

    @Provides
    @Singleton
    @ActiveMantra
    fun provideActiveMantra(catalog: MantraCatalog): Mantra = catalog.default
}
