package com.amplitude.core

/**
 * Shared utility methods for configuration validation and URL resolution.
 * Used by Configuration and ImmutableConfiguration classes to avoid duplication.
 */
internal object ConfigurationUtils {
    fun isValid(
        apiKey: String,
        flushQueueSize: Int,
        flushIntervalMillis: Int,
        minIdLength: Int?,
    ): Boolean {
        return apiKey.isNotBlank() &&
            flushQueueSize > 0 &&
            flushIntervalMillis > 0 &&
            (minIdLength == null || minIdLength > 0)
    }

    fun shouldCompressUploadBody(
        serverUrl: String?,
        enableRequestBodyCompression: Boolean,
    ): Boolean {
        return if (!serverUrl.isNullOrBlank()) enableRequestBodyCompression else true
    }

    fun getApiHost(
        serverUrl: String?,
        serverZone: ServerZone,
        useBatch: Boolean,
    ): String {
        return serverUrl?.takeIf { it.isNotBlank() } ?: when {
            serverZone == ServerZone.EU && useBatch -> Constants.EU_BATCH_API_HOST
            serverZone == ServerZone.EU -> Constants.EU_DEFAULT_API_HOST
            useBatch -> Constants.BATCH_API_HOST
            else -> Constants.DEFAULT_API_HOST
        }
    }
}
