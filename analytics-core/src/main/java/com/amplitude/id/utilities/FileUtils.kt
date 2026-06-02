package com.amplitude.id.utilities

import java.io.File
import java.io.IOException

@Throws(IOException::class)
fun createDirectory(location: File) {
    if (!(location.exists() || location.mkdirs() || location.isDirectory)) {
        throw IOException("Could not create directory at $location")
    }
}
