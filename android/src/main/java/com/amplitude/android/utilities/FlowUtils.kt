package com.amplitude.android.utilities

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/** Emits the first value and then only consecutive changes, including changes to or from null. */
internal fun <T> Flow<T>.onChanged(): Flow<T> = onChanged { it }

/** Compares keys while preserving the original value for the collector. */
internal fun <T, K> Flow<T>.onChanged(keySelector: (T) -> K): Flow<T> =
    flow {
        val unset = Any()
        var previous: Any? = unset
        collect { value ->
            val key = keySelector(value)
            if (previous === unset || previous != key) {
                previous = key
                emit(value)
            }
        }
    }
