package com.amplitude.core.utilities

public object Sample {
    public fun isInSample(
        seed: String,
        sampleRate: Double,
    ): Boolean {
        val hash = Hash.xxHash32(seed).toULong()
        val hashMultiply = hash * 31uL
        val hashMod = hashMultiply % 1_000_000uL
        val effectiveSampleRate = hashMod.toDouble() / 1_000_000.0
        return effectiveSampleRate < sampleRate
    }
}
