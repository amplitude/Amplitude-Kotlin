package com.amplitude.core.utilities.http

import java.io.OutputStream
import java.io.OutputStreamWriter

/** Encodes continuously, including surrogate pairs crossing segment boundaries. */
internal fun Iterable<String>.writeUtf8(output: OutputStream) {
    OutputStreamWriter(output, Charsets.UTF_8).buffered().use { writer ->
        forEach { writer.write(it) }
    }
}

/** Counts UTF-8 bytes with the same bounded encoder used to send the body. */
internal fun Iterable<String>.utf8Length(): Long {
    val counter =
        object : OutputStream() {
            var count = 0L

            override fun write(value: Int) {
                count++
            }

            override fun write(
                bytes: ByteArray,
                offset: Int,
                length: Int,
            ) {
                count += length
            }
        }
    writeUtf8(counter)
    return counter.count
}
