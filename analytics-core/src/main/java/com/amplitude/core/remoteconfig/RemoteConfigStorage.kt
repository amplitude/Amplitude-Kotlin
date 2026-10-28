package com.amplitude.core.remoteconfig

import com.amplitude.core.Storage
import com.amplitude.core.Storage.Constants.REMOTE_CONFIG
import com.amplitude.core.Storage.Constants.REMOTE_CONFIG_TIMESTAMP

/** A persisted remote config blob and the time it was fetched. */
internal data class CachedRemoteConfig(val configJson: String, val timestamp: Long)

/** Persistence owned by [RemoteConfigClientImpl]. */
internal interface RemoteConfigStorage {
    /** Blocking; call on the storage dispatcher. */
    fun read(): CachedRemoteConfig?

    suspend fun write(value: CachedRemoteConfig)
}

/** Stores remote config in the analytics event [Storage] under the existing keys. */
internal class StorageBackedRemoteConfigStorage(
    private val storage: Storage,
) : RemoteConfigStorage {
    override fun read(): CachedRemoteConfig? {
        val configJson = storage.read(REMOTE_CONFIG)
        val timestamp = storage.read(REMOTE_CONFIG_TIMESTAMP)?.toLongOrNull()
        if (configJson == null && timestamp == null) return null
        return CachedRemoteConfig(configJson.orEmpty(), timestamp ?: 0L)
    }

    override suspend fun write(value: CachedRemoteConfig) {
        storage.write(REMOTE_CONFIG, value.configJson)
        storage.write(REMOTE_CONFIG_TIMESTAMP, value.timestamp.toString())
    }
}
