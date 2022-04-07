package com.amplitude.android

import android.content.Context
import com.amplitude.android.utilities.AndroidLoggerProvider
import com.amplitude.android.utilities.AndroidStorageProvider
import com.amplitude.core.Configuration
import com.amplitude.core.EventCallBack
import com.amplitude.core.LoggerProvider
import com.amplitude.core.ServerZone
import com.amplitude.core.StorageProvider

class Configuration @JvmOverloads constructor(
    apiKey: String,
    val context: Context,
    flushQueueSize: Int = FLUSH_QUEUE_SIZE,
    flushIntervalMillis: Int = FLUSH_INTERVAL_MILLIS,
    instanceName: String = DEFAULT_INSTANCE,
    optOut: Boolean = false,
    storageProvider: StorageProvider = AndroidStorageProvider(),
    loggerProvider: LoggerProvider = AndroidLoggerProvider(),
    minIdLength: Int? = null,
    partnerId: String? = null,
    callback: EventCallBack? = null,
    flushMaxRetries: Int = FLUSH_MAX_RETRIES,
    useBatch: Boolean = false,
    serverZone: ServerZone = ServerZone.US,
    serverUrl: String? = null,
    val useAdvertisingIdForDeviceId: Boolean = false,
    val useAppSetIdForDeviceId: Boolean = false,
    val newDeviceIdPerInstall: Boolean = false,
    val trackingOptions: TrackingOptions = TrackingOptions(),
    val enableCoppaControl: Boolean = false,
    val locationListening: Boolean = true,
    val flushEventsOnClose: Boolean = true,
    val minTimeBetweenSessionsMillis: Long = MIN_TIME_BETWEEN_SESSIONS_MILLIS,
    val trackingSessionEvents: Boolean = true
) : Configuration(apiKey, flushQueueSize, flushIntervalMillis, instanceName, optOut, storageProvider, loggerProvider, minIdLength, partnerId, callback, flushMaxRetries, useBatch, serverZone, serverUrl) {
    companion object {
        const val MIN_TIME_BETWEEN_SESSIONS_MILLIS: Long = 5 * 60 * 1000
    }
}
