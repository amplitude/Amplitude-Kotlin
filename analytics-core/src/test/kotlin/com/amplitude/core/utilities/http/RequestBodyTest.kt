package com.amplitude.core.utilities.http

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.io.OutputStream

class RequestBodyTest {
    @Test
    fun `should preserve unicode across segments and encoder boundaries`() {
        val parts = listOf("a".repeat(8191) + "\uD83D", "\uDE00世界", "\uD83D")
        val expected = parts.joinToString("").toByteArray(Charsets.UTF_8)
        val output = ByteArrayOutputStream()
        parts.writeUtf8(output)
        assertArrayEquals(expected, output.toByteArray())
        assertEquals(expected.size.toLong(), parts.utf8Length())
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
        assertEquals(0L, emptyList<String>().utf8Length())
    }
}
