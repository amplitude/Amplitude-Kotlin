package com.amplitude.kotlin

data class Configuration(
    val apiKey: String,
    val flushQueueSize: Int = Constants.FLUSH_QUEUE_SIZE,
    val flushIntervalMillis: Int = Constants.FLUSH_INTERVAL_MILLIS,
    val optOut: Boolean = false,
    val storageProvider: StorageProvider,
    val loggerProvider: LoggerProvider,
    val minIdLength: Int?
) {
    fun isValid(): Boolean {
        return apiKey.isNotBlank()
    }
}
