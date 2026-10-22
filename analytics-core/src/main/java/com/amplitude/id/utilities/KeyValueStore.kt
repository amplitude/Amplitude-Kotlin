package com.amplitude.id.utilities

/**
 * Key-value store interface
 */
public interface KeyValueStore {
    public fun getLong(
        key: String,
        defaultVal: Long,
    ): Long

    public fun putLong(
        key: String,
        value: Long,
    ): Boolean

    public fun deleteKey(key: String)
}
