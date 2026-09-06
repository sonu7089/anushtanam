/*
 * Anushtanam - Vedic mantra recitation app
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.domain.mantra

/**
 * A bundled mantra definition is missing, unreadable or inconsistent.
 *
 * This is deliberately fatal rather than a silent fallback. The catalog ships inside the
 * APK, so a failure here means the build is wrong, and every message names the file, the
 * field and what was expected so it can be fixed in one pass.
 */
class MantraCatalogException(message: String, cause: Throwable? = null) :
    IllegalStateException(message, cause)
