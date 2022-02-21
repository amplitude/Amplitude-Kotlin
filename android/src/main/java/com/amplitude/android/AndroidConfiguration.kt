package com.amplitude.android

import com.amplitude.Configuration
import com.amplitude.Constants
import com.amplitude.LoggerProvider
import com.amplitude.StorageProvider
import com.amplitude.android.utilities.AndroidLoggerProvider
import com.amplitude.android.utilities.AndroidStorageProvider
import com.amplitude.events.BaseEvent

class AndroidConfiguration(
    apiKey: String,
    flushQueueSize: Int = Constants.FLUSH_QUEUE_SIZE,
    flushIntervalMillis: Int = Constants.FLUSH_INTERVAL_MILLIS,
    optOut: Boolean = false,
    storageProvider: StorageProvider = AndroidStorageProvider(),
    loggerProvider: LoggerProvider = AndroidLoggerProvider(),
    minIdLength: Int? = null,
    callback: ((BaseEvent) -> Unit)? = null,
    useAdvertisingIdForDeviceId: Boolean = false,
    useAppSetIdForDeviceId: Boolean = false,
    enableCoppaControl: Boolean = false
) : Configuration(apiKey, flushQueueSize, flushIntervalMillis, optOut, storageProvider, loggerProvider, minIdLength, callback)