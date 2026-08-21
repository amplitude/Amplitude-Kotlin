package com.amplitude.android.utilities

import kotlinx.coroutines.CancellationException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class KotlinUtilsTest {
    @Test
    fun `runCatchingCancellable returns success when block completes`() {
        val result = runCatchingCancellable { "ok" }

        assertTrue(result.isSuccess)
        assertEquals("ok", result.getOrNull())
    }

    @Test
    fun `runCatchingCancellable returns failure for non-cancellation exceptions`() {
        val result = runCatchingCancellable { error("boom") }

        assertTrue(result.isFailure)
        assertEquals("boom", result.exceptionOrNull()?.message)
    }

    @Test
    fun `runCatchingCancellable rethrows CancellationException`() {
        assertThrows(CancellationException::class.java) {
            runCatchingCancellable { throw CancellationException("cancelled") }
        }
    }

    @Test
    fun `runCatchingCancellable runs finally on success`() {
        var ran = false

        runCatchingCancellable(finally = { ran = true }) { "ok" }

        assertTrue(ran)
    }

    @Test
    fun `runCatchingCancellable runs finally on failure`() {
        var ran = false

        runCatchingCancellable(finally = { ran = true }) { error("boom") }

        assertTrue(ran)
    }

    @Test
    fun `runCatchingCancellable runs finally when CancellationException is rethrown`() {
        var ran = false

        assertThrows(CancellationException::class.java) {
            runCatchingCancellable(finally = { ran = true }) {
                throw CancellationException("cancelled")
            }
        }
        assertTrue(ran)
    }
}
