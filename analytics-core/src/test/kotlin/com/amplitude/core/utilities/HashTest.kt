package com.amplitude.core.utilities

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class HashTest {
    // Helper: format UInt as 8-char lowercase hex (like xxhsum / hexdigest)
    private fun hex8(x: UInt): String = x.toString(16).padStart(8, '0')

    @Test
    fun `xxHash32 vector spammish repetition seed 0`() {
        val s = "Nobody inspects the spammish repetition"
        val h = Hash.xxHash32(s, 0u)
        assertEquals("e2293b2f", hex8(h))
    }

    @Test
    fun `xxHash32 vector unsigned 32-bit seed warning string seed 0`() {
        val s = "I want an unsigned 32-bit seed!"
        val h = Hash.xxHash32(s, 0u)
        assertEquals("f7a35af8", hex8(h))
    }

    @Test
    fun `xxHash32 vector unsigned 32-bit seed warning string seed 1`() {
        val s = "I want an unsigned 32-bit seed!"
        val h = Hash.xxHash32(s, 1u)
        assertEquals("d8d4b4ba", hex8(h))
    }

    @Test
    fun `xxHash32 determinism same input same output`() {
        val s = "session-123"
        val a = Hash.xxHash32(s, 0u)
        val b = Hash.xxHash32(s, 0u)
        assertEquals(a, b)
    }

    @Test
    fun `xxHash32 apache getValue semantics unsigned long range`() {
        // Match server logic
        // Apache Commons Codec XXHash32.getValue() returns a long representing an unsigned 32-bit hash.
        // So when we widen, it should be in [0, 2^32-1].
        val s = "anything"
        val h32 = Hash.xxHash32(s, 0u)
        val value = h32.toULong().toLong() // "getValue()" style widening
        assertTrue(value >= 0)
        assertTrue(value <= UInt.MAX_VALUE.toLong())
    }

    @Test
    fun `xxHash32 different inputs produce different hashes`() {
        val hash1 = Hash.xxHash32("input1")
        val hash2 = Hash.xxHash32("input2")
        assertNotEquals(hash1, hash2)
    }

    @Test
    fun `xxHash32 different seeds produce different hashes`() {
        val input = "test-string"
        val hashSeed0 = Hash.xxHash32(input, 0u)
        val hashSeed1 = Hash.xxHash32(input, 1u)
        assertNotEquals(hashSeed0, hashSeed1)
    }

    @Test
    fun `xxHash32 produces good distribution`() {
        val hashes = (0 until 1000).map { Hash.xxHash32("seed-$it") }.toSet()
        assertEquals(1000, hashes.size, "All unique inputs should produce unique hashes")
    }
}
