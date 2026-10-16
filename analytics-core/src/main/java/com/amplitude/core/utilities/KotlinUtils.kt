package com.amplitude.core.utilities

import java.io.OutputStream
import java.io.OutputStreamWriter
import kotlin.coroutines.cancellation.CancellationException

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

/** Encodes continuously, including surrogate pairs crossing segment boundaries. */
internal fun Iterable<String>.writeUtf8(output: OutputStream) {
    OutputStreamWriter(output, Charsets.UTF_8).buffered().use { writer ->
        forEach { writer.write(it) }
    }
}
