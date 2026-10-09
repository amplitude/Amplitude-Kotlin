package com.amplitude.core.utilities

import kotlinx.coroutines.CancellationException
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.io.ByteArrayOutputStream
import java.io.OutputStream

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

    @Test
    fun `should preserve unicode across segments and encoder boundaries`() {
        val parts = listOf("a".repeat(8191) + "\uD83D", "\uDE00世界", "\uD83D")
        val expected = parts.joinToString("").toByteArray(Charsets.UTF_8)
        val output = ByteArrayOutputStream()
        parts.writeUtf8(output)
        assertArrayEquals(expected, output.toByteArray())
    }

    @Test
    fun `should encode large segments using bounded writes`() {
        var largestWrite = 0
        val output =
            object : OutputStream() {
                override fun write(value: Int) = Unit

                override fun write(
                    bytes: ByteArray,
                    offset: Int,
                    length: Int,
                ) {
                    largestWrite = maxOf(largestWrite, length)
                }
            }
        listOf("世界😀".repeat(100_000)).writeUtf8(output)
        assertTrue(largestWrite in 1..8192)
    }
}
