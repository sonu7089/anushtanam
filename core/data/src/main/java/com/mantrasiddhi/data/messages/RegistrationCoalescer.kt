package com.mantrasiddhi.data.messages

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Only acknowledged inputs are reusable; failed writes remain retryable. */
internal class RegistrationCoalescer<T> {
    private val lock = Mutex()
    private var acknowledged: T? = null
    suspend fun update(inputs: T, write: suspend () -> Unit) = lock.withLock {
        if (acknowledged != inputs) {
            write()
            acknowledged = inputs
        }
    }
}
