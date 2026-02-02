package com.amplitude.core.utilities

import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream

/**
 * Utility functions for gzip compression.
 */
internal object GzipUtils {
    /**
     * Compresses the given string data using gzip.
     *
     * @param data The string to compress
     * @return The gzip-compressed data as a byte array
     * @throws java.io.IOException if compression fails
     */
    fun compress(data: String): ByteArray {
        val byteArrayOutputStream = ByteArrayOutputStream()
        GZIPOutputStream(byteArrayOutputStream).use { gzipStream ->
            gzipStream.write(data.toByteArray(Charsets.UTF_8))
        }
        return byteArrayOutputStream.toByteArray()
    }
}
