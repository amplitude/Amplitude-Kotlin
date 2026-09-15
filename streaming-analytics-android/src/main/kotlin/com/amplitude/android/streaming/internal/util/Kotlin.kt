package com.amplitude.android.streaming.internal.util

import kotlin.coroutines.cancellation.CancellationException

internal fun Map<String, Any?>.deepCopy(): MutableMap<String, Any?> {
    val copy = LinkedHashMap<String, Any?>(size)
    for ((key, value) in this) {
        copy[key] = value.deepCopyValue()
    }
    return copy
}

private fun Any?.deepCopyValue(): Any? =
    when (this) {
        is Map<*, *> -> {
            val copy = LinkedHashMap<Any?, Any?>(size)
            for ((k, v) in this) {
                copy[k] = v.deepCopyValue()
            }
            copy
        }
        is Collection<*> -> mapTo(ArrayList(size)) { it.deepCopyValue() }
        else -> this
    }

internal fun Long.millisToSeconds(): Double = this / 1_000.0

/**
 * Like [runCatching], but rethrows [CancellationException] so coroutine cancellation is not
 * swallowed into a failed [Result].
 *
 * [finally] always runs, including when [CancellationException] is rethrown.
 * Pass it by name so the trailing lambda remains [block]:
 * `runCatchingCancellable(finally = { cleanup() }) { work() }`.
 */
internal inline fun <T, R> T.runCatchingCancellable(
    finally: () -> Unit = {},
    block: T.() -> R,
): Result<R> {
    return try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        Result.failure(e)
    } finally {
        finally()
    }
}
