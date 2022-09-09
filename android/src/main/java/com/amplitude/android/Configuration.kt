package com.amplitude.android

import android.content.Context
import com.amplitude.android.utilities.AndroidLoggerProvider
import com.amplitude.android.utilities.AndroidStorageProvider
import com.amplitude.core.Configuration
import com.amplitude.core.EventCallBack
import com.amplitude.core.LoggerProvider
import com.amplitude.core.ServerZone
import com.amplitude.core.StorageProvider
import com.amplitude.core.events.IngestionMetadata
import com.amplitude.core.events.Plan

open class Configuration @JvmOverloads constructor(
    apiKey: String,
    val context: Context,
    override var flushQueueSize: Int = FLUSH_QUEUE_SIZE,
    override var flushIntervalMillis: Int = FLUSH_INTERVAL_MILLIS,
    override var instanceName: String = DEFAULT_INSTANCE,
    override var optOut: Boolean = false,
    override val storageProvider: StorageProvider = AndroidStorageProvider(),
    override val loggerProvider: LoggerProvider = AndroidLoggerProvider(),
    override var minIdLength: Int? = null,
    override var partnerId: String? = null,
    override var callback: EventCallBack? = null,
    override var flushMaxRetries: Int = FLUSH_MAX_RETRIES,
    override var useBatch: Boolean = false,
    override var serverZone: ServerZone = ServerZone.US,
    override var serverUrl: String? = null,
    override var plan: Plan? = null,
    override var ingestionMetadata: IngestionMetadata? = null,
    var useAdvertisingIdForDeviceId: Boolean = false,
    var useAppSetIdForDeviceId: Boolean = false,
    var newDeviceIdPerInstall: Boolean = false,
    val trackingOptions: TrackingOptions = TrackingOptions(),
    var enableCoppaControl: Boolean = false,
    var locationListening: Boolean = true,
    var flushEventsOnClose: Boolean = true,
    var minTimeBetweenSessionsMillis: Long = MIN_TIME_BETWEEN_SESSIONS_MILLIS,
    var trackingSessionEvents: Boolean = true
) : Configuration(apiKey, flushQueueSize, flushIntervalMillis, instanceName, optOut, storageProvider, loggerProvider, minIdLength, partnerId, callback, flushMaxRetries, useBatch, serverZone, serverUrl, plan, ingestionMetadata) {
    companion object {
        const val MIN_TIME_BETWEEN_SESSIONS_MILLIS: Long = 300000
    }
}
