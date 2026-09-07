/*
 * Anushtanam - Vedic mantra recitation app
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.domain.mantra

import javax.inject.Qualifier

/**
 * Marks the [Mantra] the app is currently teaching — [MantraCatalog.default].
 *
 * Injecting the active mantra rather than reaching for a global keeps every scorer and
 * use case testable against an arbitrary mantra, and leaves one obvious place to change
 * if the app ever lets the sadhak pick between bundled mantras: the binding that
 * provides this, in `core/data`'s `MantraModule`.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ActiveMantra
