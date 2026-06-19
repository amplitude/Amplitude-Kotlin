package com.amplitude.core.utilities

internal object Hash {
    fun fnv1a64(value: String): ULong {
        var hash = 0xcbf29ce484222325uL
        val prime = 0x100000001b3uL
        for (byte in value.toByteArray(Charsets.UTF_8)) {
            hash = (hash xor (byte.toULong() and 0xFFu)) * prime
        }
        return hash
    }

    fun xxHash32(
        value: String,
        seed: UInt = 0u,
    ): UInt {
        return XxHash32.hash(value, seed)
    }
}

private object XxHash32 {
    private const val PRIME32_1 = 0x9E3779B1u
    private const val PRIME32_2 = 0x85EBCA77u
    private const val PRIME32_3 = 0xC2B2AE3Du
    private const val PRIME32_4 = 0x27D4EB2Fu
    private const val PRIME32_5 = 0x165667B1u

    fun hash(
        value: String,
        seed: UInt,
    ): UInt {
        val bytes = value.toByteArray(Charsets.UTF_8)
        val length = bytes.size
        var index = 0
        var hash: UInt

        if (length >= 16) {
            var acc1 = seed + PRIME32_1 + PRIME32_2
            var acc2 = seed + PRIME32_2
            var acc3 = seed
            var acc4 = seed - PRIME32_1

            // Process 16-byte blocks
            while (index <= length - 16) {
                acc1 = round(acc1, readLittleEndianUInt(bytes, index))
                index += 4
                acc2 = round(acc2, readLittleEndianUInt(bytes, index))
                index += 4
                acc3 = round(acc3, readLittleEndianUInt(bytes, index))
                index += 4
                acc4 = round(acc4, readLittleEndianUInt(bytes, index))
                index += 4
            }

            hash = acc1.rotateLeft(1) + acc2.rotateLeft(7) + acc3.rotateLeft(12) + acc4.rotateLeft(18)
        } else {
            hash = seed + PRIME32_5
        }

        hash += length.toUInt()

        // Process remaining 4-byte blocks
        while (index <= length - 4) {
            hash += readLittleEndianUInt(bytes, index) * PRIME32_3
            hash = hash.rotateLeft(17) * PRIME32_4
            index += 4
        }

        // Process remaining bytes
        while (index < length) {
            hash += (bytes[index].toInt() and 0xFF).toUInt() * PRIME32_5
            hash = hash.rotateLeft(11) * PRIME32_1
            index++
        }

        // Final avalanche
        hash = hash xor (hash shr 15)
        hash *= PRIME32_2
        hash = hash xor (hash shr 13)
        hash *= PRIME32_3
        hash = hash xor (hash shr 16)

        return hash
    }

    private fun round(
        acc: UInt,
        input: UInt,
    ): UInt {
        return (acc + input * PRIME32_2).rotateLeft(13) * PRIME32_1
    }

    private fun readLittleEndianUInt(
        bytes: ByteArray,
        index: Int,
    ): UInt {
        return (
            (bytes[index].toInt() and 0xFF) or
                ((bytes[index + 1].toInt() and 0xFF) shl 8) or
                ((bytes[index + 2].toInt() and 0xFF) shl 16) or
                ((bytes[index + 3].toInt() and 0xFF) shl 24)
        ).toUInt()
    }
}

internal fun ULong.toHexString(): String = this.toString(16).padStart(16, '0')
