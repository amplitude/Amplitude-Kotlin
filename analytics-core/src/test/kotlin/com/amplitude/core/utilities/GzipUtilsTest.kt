package com.amplitude.core.utilities

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.io.ByteArrayInputStream
import java.util.zip.GZIPInputStream

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GzipUtilsTest {
    @Test
    fun `compress produces valid gzip data with correct magic bytes`() {
        val input = "Hello, World!"
        val compressed = GzipUtils.compress(input)

        // Verify gzip magic bytes (0x1f, 0x8b)
        assertEquals(0x1f.toByte(), compressed[0])
        assertEquals(0x8b.toByte(), compressed[1])
    }

    @Test
    fun `compress produces data that can be decompressed to original`() {
        val input = "This is a test string for gzip compression verification."
        val compressed = GzipUtils.compress(input)

        // Decompress and verify
        val decompressed =
            GZIPInputStream(ByteArrayInputStream(compressed)).bufferedReader().use {
                it.readText()
            }

        assertEquals(input, decompressed)
    }

    @Test
    fun `compress handles unicode characters correctly`() {
        val input = "Hello, \u4e16\u754c! \u00e9\u00e0\u00fc" // Chinese and accented characters
        val compressed = GzipUtils.compress(input)

        // Decompress and verify
        val decompressed =
            GZIPInputStream(ByteArrayInputStream(compressed)).bufferedReader().use {
                it.readText()
            }

        assertEquals(input, decompressed)
    }

    @Test
    fun `compress handles empty string`() {
        val input = ""
        val compressed = GzipUtils.compress(input)

        // Verify gzip magic bytes are present even for empty input
        assertEquals(0x1f.toByte(), compressed[0])
        assertEquals(0x8b.toByte(), compressed[1])

        // Decompress and verify
        val decompressed =
            GZIPInputStream(ByteArrayInputStream(compressed)).bufferedReader().use {
                it.readText()
            }

        assertEquals(input, decompressed)
    }

    @Test
    fun `compress produces smaller output for large repetitive data`() {
        val input = "a".repeat(10000)
        val compressed = GzipUtils.compress(input)

        // Repetitive data should compress significantly
        assertTrue(compressed.size < input.toByteArray().size / 10)
    }

    @Test
    fun `compress handles large JSON-like payload`() {
        val jsonPayload =
            buildString {
                append("{\"events\":[")
                for (i in 0 until 100) {
                    if (i > 0) append(",")
                    append("{\"event_type\":\"test_event_$i\",\"user_id\":\"user_$i\",\"timestamp\":${System.currentTimeMillis()}}")
                }
                append("]}")
            }

        val compressed = GzipUtils.compress(jsonPayload)

        // Decompress and verify
        val decompressed =
            GZIPInputStream(ByteArrayInputStream(compressed)).bufferedReader().use {
                it.readText()
            }

        assertEquals(jsonPayload, decompressed)
        // JSON with repeated structure should compress well
        assertTrue(compressed.size < jsonPayload.toByteArray().size)
    }
}
