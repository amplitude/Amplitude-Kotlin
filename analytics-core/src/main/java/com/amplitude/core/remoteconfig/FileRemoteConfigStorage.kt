package com.amplitude.core.remoteconfig

import com.amplitude.common.Logger
import com.amplitude.core.utilities.Hash
import com.amplitude.core.utilities.toHexString
import org.json.JSONObject
import java.io.File
import java.io.IOException

/**
 * Stores remote config in `com.amplitude.remote-config/<hashed namespace>.json` under
 * [storageDirectory]. The file records the API key and is ignored when it differs.
 */
internal class FileRemoteConfigStorage(
    storageDirectory: File,
    private val apiKey: String,
    namespace: String,
    private val logger: Logger,
) : RemoteConfigStorage {
    private val lock = Any()
    private val cacheFile =
        File(storageDirectory, "$STORAGE_PREFIX/${Hash.fnv1a64(namespace).toHexString()}.json")

    override fun read(): CachedRemoteConfig? =
        synchronized(lock) {
            try {
                if (!cacheFile.exists()) return null
                val envelope = JSONObject(cacheFile.readText())
                if (envelope.getString(API_KEY) != apiKey) return null
                val timestamp = envelope.getLong(TIMESTAMP)
                if (timestamp <= 0L) return null
                CachedRemoteConfig(envelope.getJSONObject(CONFIG).toString(), timestamp)
            } catch (e: Exception) {
                logger.warn("Unable to read remote-config cache: ${e.message}")
                null
            }
        }

    override suspend fun write(value: CachedRemoteConfig) {
        val envelope =
            JSONObject()
                .put(API_KEY, apiKey)
                .put(CONFIG, JSONObject(value.configJson))
                .put(TIMESTAMP, value.timestamp)
        synchronized(lock) { persist(envelope.toString()) }
    }

    // Temp file + fsync + rename so a crash never leaves a partial cache.
    private fun persist(contents: String) {
        val directory = cacheFile.parentFile ?: throw IOException("Missing remote-config cache directory")
        if (!directory.isDirectory && !directory.mkdirs() && !directory.isDirectory) {
            throw IOException("Unable to create remote-config cache directory")
        }
        val temporary = File.createTempFile("config-", ".tmp", directory)
        try {
            temporary.outputStream().use { output ->
                output.write(contents.toByteArray(Charsets.UTF_8))
                output.fd.sync()
            }
            if (!temporary.renameTo(cacheFile)) throw IOException("Unable to replace remote-config cache")
        } finally {
            temporary.delete()
        }
    }

    private companion object {
        const val STORAGE_PREFIX = "com.amplitude.remote-config"
        const val API_KEY = "apiKey"
        const val CONFIG = "config"
        const val TIMESTAMP = "timestamp"
    }
}
