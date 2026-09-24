package com.amplitude.id.utilities

import java.io.File
import java.io.IOException

@Deprecated("Not intended for public use. Will be internal in a future release.")
@Throws(IOException::class)
public fun createDirectory(location: File) {
    if (!(location.exists() || location.mkdirs() || location.isDirectory)) {
        throw IOException("Could not create directory at $location")
    }
}
