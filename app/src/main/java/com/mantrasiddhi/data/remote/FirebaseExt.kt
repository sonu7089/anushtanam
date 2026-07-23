/*
 * Anushtanam - Gayatri Mantra Recitation App
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.data.remote

import com.google.android.gms.tasks.Task
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Awaits a Google Play Services [Task] as a suspend function.
 *
 * Kept local so the project does not need `kotlinx-coroutines-play-services`; it wraps
 * the completion callback in a cancellable continuation.
 */
suspend fun <T> Task<T>.awaitResult(): T = suspendCancellableCoroutine { cont ->
    addOnCompleteListener { task ->
        val e = task.exception
        when {
            e != null -> cont.resumeWithException(e)
            task.isCanceled -> cont.cancel()
            else ->
                @Suppress("UNCHECKED_CAST")
                cont.resume(task.result as T)
        }
    }
}
