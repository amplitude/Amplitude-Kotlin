package com.amplitude.core.utilities

import com.amplitude.core.utilities.http.writeUtf8
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
    fun compress(data: String): ByteArray = compress(listOf(data))

    /**
     * Compresses [parts] using gzip as if they were concatenated, without joining them first.
     *
     * @param parts The strings to compress, written in order
     * @return The gzip-compressed data as a byte array
     * @throws java.io.IOException if compression fails
     */
    fun compress(parts: Iterable<String>): ByteArray {
        val byteArrayOutputStream = ByteArrayOutputStream()
        GZIPOutputStream(byteArrayOutputStream).use { gzipStream ->
            parts.writeUtf8(gzipStream)
        }
        return byteArrayOutputStream.toByteArray()
    }
}
