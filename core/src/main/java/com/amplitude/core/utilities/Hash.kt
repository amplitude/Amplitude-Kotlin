package com.amplitude.core.utilities

import kotlin.text.iterator

internal object Hash {
    fun javaStyleHash64(value: String): ULong {
        var hash = 0L
        for (ch in value) {
            hash = (hash shl 5) - hash + ch.code
        }
        return hash.toULong()
    }

    fun fnv1a64(value: String): ULong {
        var hash = 0xcbf29ce484222325uL
        val prime = 0x100000001b3uL
        for (byte in value.toByteArray(Charsets.UTF_8)) {
            hash = (hash xor (byte.toULong() and 0xFFu)) * prime
        }
        return hash
    }
}

internal fun ULong.toHexString(): String = this.toString(16).padStart(16, '0')
