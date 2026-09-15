package com.amplitude.android.streaming.internal.util

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Returns a [LazySuspend] that runs [initializer] the first time it is invoked.
 *
 * ```kotlin
 * private val token = lazySuspend { fetchToken() }
 *
 * suspend fun useToken() {
 *     val value = token()
 * }
 * ```
 */
internal fun <T> lazySuspend(initializer: suspend () -> T): LazySuspend<T> =
    LazySuspendImpl(initializer)

/**
 * A value computed once by a suspending initializer, then reused.
 *
 * The first [invoke] runs the initializer and later calls return that result, including `null`.
 * Concurrent callers wait on the same in-flight run. A thrown exception is not stored, so the
 * next [invoke] runs the initializer again. The initializer uses the first caller's coroutine
 * context.
 */
internal interface LazySuspend<T> {
    /**
     * Returns the cached value, running the initializer if this instance has none yet.
     */
    suspend operator fun invoke(): T
}

/**
 * Suspend analog of [lazy]: the first [invoke] runs [initialize] and later calls reuse that
 * result.
 */
private class LazySuspendImpl<T>(
    private val initialize: suspend () -> T,
) : LazySuspend<T> {
    private val mutex = Mutex()

    @Volatile
    private var value: Any? = UNINITIALIZED
    override suspend operator fun invoke(): T {
        val current = value
        if (current !== UNINITIALIZED) {
            @Suppress("UNCHECKED_CAST")
            return current as T
        }
        return mutex.withLock {
            val locked = value
            if (locked !== UNINITIALIZED) {
                @Suppress("UNCHECKED_CAST")
                locked as T
            } else {
                initialize().also { value = it }
            }
        }
    }

    private companion object {
        val UNINITIALIZED = Any()
    }
}
