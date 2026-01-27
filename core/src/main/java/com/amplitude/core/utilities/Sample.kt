package com.amplitude.core.utilities

internal object Sample {
    fun isInSample(
        seed: String,
        sampleRate: Double,
    ): Boolean {
        val hash = Hash.javaStyleHash64(seed)
        val scaledHash = (hash * 31uL) % 100_000uL
        val threshold = (sampleRate * 100_000).toLong().coerceAtLeast(0).toULong()
        return scaledHash < threshold
    }
}
