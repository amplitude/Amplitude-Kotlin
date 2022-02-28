package com.amplitude.android

import android.content.Context
import com.amplitude.core.Configuration
import com.amplitude.core.LoggerProvider
import com.amplitude.core.StorageProvider
import com.amplitude.android.utilities.AndroidLoggerProvider
import com.amplitude.android.utilities.AndroidStorageProvider
import com.amplitude.core.events.BaseEvent
import com.amplitude.core.utilities.FileStorageProvider

class Configuration(
    apiKey: String,
    context: Context,
    flushQueueSize: Int = FLUSH_QUEUE_SIZE,
    flushIntervalMillis: Int = FLUSH_INTERVAL_MILLIS,
    optOut: Boolean = false,
    storageProvider: StorageProvider = FileStorageProvider(),
    loggerProvider: LoggerProvider = AndroidLoggerProvider(),
    minIdLength: Int? = null,
    callback: ((BaseEvent) -> Unit)? = null,
    useAdvertisingIdForDeviceId: Boolean = false,
    useAppSetIdForDeviceId: Boolean = false,
    enableCoppaControl: Boolean = false
) : Configuration(apiKey, flushQueueSize, flushIntervalMillis, optOut, storageProvider, loggerProvider, minIdLength, callback)
