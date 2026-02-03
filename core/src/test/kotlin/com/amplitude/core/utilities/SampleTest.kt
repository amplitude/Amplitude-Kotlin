package com.amplitude.core.utilities

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SampleTest {
    @Test
    fun `isInSample returns true when sample rate is 1_0`() {
        // Sample rate of 1.0 should always include everything
        assertTrue(Sample.isInSample("any-seed-1", 1.0))
        assertTrue(Sample.isInSample("any-seed-2", 1.0))
        assertTrue(Sample.isInSample("any-seed-3", 1.0))
    }

    @Test
    fun `isInSample returns false when sample rate is 0_0`() {
        // Sample rate of 0.0 should never include anything
        assertFalse(Sample.isInSample("any-seed-1", 0.0))
        assertFalse(Sample.isInSample("any-seed-2", 0.0))
        assertFalse(Sample.isInSample("any-seed-3", 0.0))
    }

    @Test
    fun `isInSample is deterministic for same seed`() {
        val seed = "test-device-id"
        val sampleRate = 0.5

        val result1 = Sample.isInSample(seed, sampleRate)
        val result2 = Sample.isInSample(seed, sampleRate)
        val result3 = Sample.isInSample(seed, sampleRate)

        assertEquals(result1, result2)
        assertEquals(result2, result3)
    }

    @Test
    fun `isInSample respects sample rate distribution`() {
        // Test with a large number of seeds to verify approximate distribution
        val sampleRate = 0.5
        var inSampleCount = 0
        val totalSeeds = 10000

        for (i in 0 until totalSeeds) {
            if (Sample.isInSample("seed-$i", sampleRate)) {
                inSampleCount++
            }
        }

        // With 50% sample rate, we expect roughly 50% to be in sample
        // Allow for some variance (40% - 60%)
        val ratio = inSampleCount.toDouble() / totalSeeds
        assertTrue(ratio > 0.4 && ratio < 0.6) {
            "Expected ~50% in sample, got ${ratio * 100}%"
        }
    }

    @Test
    fun `isInSample handles edge sample rates`() {
        val seed = "test-seed"

        // Very small sample rate
        val verySmallRate = 0.001
        // Result should be deterministic regardless of value
        val result1 = Sample.isInSample(seed, verySmallRate)
        assertEquals(result1, Sample.isInSample(seed, verySmallRate))

        // Sample rate just under 1.0
        val almostOneRate = 0.999999
        val result2 = Sample.isInSample(seed, almostOneRate)
        assertEquals(result2, Sample.isInSample(seed, almostOneRate))
    }

    @Test
    fun `isInSample produces different results for different seeds at 50 percent rate`() {
        val sampleRate = 0.5

        // With enough different seeds, we should see both true and false results
        val results = mutableSetOf<Boolean>()
        for (i in 0 until 100) {
            results.add(Sample.isInSample("unique-seed-$i", sampleRate))
        }

        // At 50% rate, we should definitely see both outcomes in 100 tries
        assertTrue(results.contains(true)) { "Should have at least one true result" }
        assertTrue(results.contains(false)) { "Should have at least one false result" }
    }

    @Test
    fun `isInSample handles empty seed`() {
        // Empty seed should still work deterministically
        val result1 = Sample.isInSample("", 0.5)
        val result2 = Sample.isInSample("", 0.5)
        assertEquals(result1, result2)
    }

    @Test
    fun `isInSample handles unicode seeds`() {
        val unicodeSeed = "用户设备ID-こんにちは-🎉"
        val result1 = Sample.isInSample(unicodeSeed, 0.5)
        val result2 = Sample.isInSample(unicodeSeed, 0.5)
        assertEquals(result1, result2)
    }

    @Test
    fun `isInSample with increasing sample rate includes more seeds`() {
        val seeds = (0 until 1000).map { "seed-$it" }

        val countAt10Percent = seeds.count { Sample.isInSample(it, 0.1) }
        val countAt50Percent = seeds.count { Sample.isInSample(it, 0.5) }
        val countAt90Percent = seeds.count { Sample.isInSample(it, 0.9) }

        // Higher sample rates should include more seeds
        assertTrue(countAt10Percent < countAt50Percent) {
            "10% rate ($countAt10Percent) should include fewer than 50% rate ($countAt50Percent)"
        }
        assertTrue(countAt50Percent < countAt90Percent) {
            "50% rate ($countAt50Percent) should include fewer than 90% rate ($countAt90Percent)"
        }
    }
}
