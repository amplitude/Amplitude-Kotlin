package com.amplitude.android

import android.content.Context
import com.amplitude.android.utilities.AndroidLoggerProvider
import com.amplitude.core.Configuration
import com.amplitude.core.LoggerProvider
import com.amplitude.core.StorageProvider
import com.amplitude.core.events.BaseEvent
import com.amplitude.core.utilities.FileStorageProvider

class Configuration(
    apiKey: String,
    val context: Context,
    flushQueueSize: Int = FLUSH_QUEUE_SIZE,
    flushIntervalMillis: Int = FLUSH_INTERVAL_MILLIS,
    instanceName: String = DEFAULT_INSTANCE,
    optOut: Boolean = false,
    storageProvider: StorageProvider = FileStorageProvider(),
    loggerProvider: LoggerProvider = AndroidLoggerProvider(),
    minIdLength: Int? = null,
    callback: ((BaseEvent) -> Unit)? = null,
    val useAdvertisingIdForDeviceId: Boolean = false,
    val useAppSetIdForDeviceId: Boolean = false,
    val newDeviceIdPerInstall: Boolean = false,
    val trackingOptions: TrackingOptions = TrackingOptions(),
    val enableCoppaControl: Boolean = false,
    val locationListening: Boolean = true
) : Configuration(apiKey, flushQueueSize, flushIntervalMillis, instanceName, optOut, storageProvider, loggerProvider, minIdLength, callback)
