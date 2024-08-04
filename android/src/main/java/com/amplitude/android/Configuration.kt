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
import com.amplitude.id.FileIdentityStorageProvider
import com.amplitude.id.IdentityStorageProvider

@OptIn(ExperimentalAmplitudeFeature::class)
open class Configuration @JvmOverloads constructor(
    apiKey: String,
    val context: Context,
    override var flushQueueSize: Int = FLUSH_QUEUE_SIZE,
    override var flushIntervalMillis: Int = FLUSH_INTERVAL_MILLIS,
    override var instanceName: String = DEFAULT_INSTANCE,
    override var optOut: Boolean = false,
    override var storageProvider: StorageProvider = AndroidStorageProvider(),
    override var loggerProvider: LoggerProvider = AndroidLoggerProvider(),
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
    var trackingOptions: TrackingOptions = TrackingOptions(),
    var enableCoppaControl: Boolean = false,
    var locationListening: Boolean = true,
    var flushEventsOnClose: Boolean = true,
    var minTimeBetweenSessionsMillis: Long = MIN_TIME_BETWEEN_SESSIONS_MILLIS,
    @Deprecated("Please use 'autocapture' instead and set 'AutocaptureOptions.SESSIONS' to enable the option.")
    var trackingSessionEvents: Boolean = true,
    @Suppress("DEPRECATION")
    @Deprecated("Please use 'autocapture' instead", ReplaceWith("autocapture"))
    var defaultTracking: DefaultTrackingOptions = DefaultTrackingOptions(),
    autocapture: Set<AutocaptureOption> = setOf(AutocaptureOption.SESSIONS),
    override var identifyBatchIntervalMillis: Long = IDENTIFY_BATCH_INTERVAL_MILLIS,
    override var identifyInterceptStorageProvider: StorageProvider = AndroidStorageProvider(),
    override var identityStorageProvider: IdentityStorageProvider = FileIdentityStorageProvider(),
    var migrateLegacyData: Boolean = true,
    override var offline: Boolean? = false,
    override var deviceId: String? = null,
    override var sessionId: Long? = null,
) : Configuration(
    apiKey,
    flushQueueSize,
    flushIntervalMillis,
    instanceName,
    optOut,
    storageProvider,
    loggerProvider,
    minIdLength,
    partnerId,
    callback,
    flushMaxRetries,
    useBatch,
    serverZone,
    serverUrl,
    plan,
    ingestionMetadata,
    identifyBatchIntervalMillis,
    identifyInterceptStorageProvider,
    identityStorageProvider,
    offline,
    deviceId,
    sessionId,
) {
    companion object {
        const val MIN_TIME_BETWEEN_SESSIONS_MILLIS: Long = 300000
    }

    val autocapture: Set<AutocaptureOption> = autocapture
        @Suppress("DEPRECATION")
        get() = autocaptureOptions {
            if (trackingSessionEvents && defaultTracking.sessions && AutocaptureOption.SESSIONS in field) {
                +sessions
            }
            if (defaultTracking.appLifecycles || AutocaptureOption.APP_LIFECYCLES in field) {
                +appLifecycles
            }
            if (defaultTracking.deepLinks || AutocaptureOption.DEEP_LINKS in field) {
                +deepLinks
            }
            if (defaultTracking.screenViews || AutocaptureOption.SCREEN_VIEWS in field) {
                +screenViews
            }
            if (AutocaptureOption.ELEMENT_INTERACTIONS in field) {
                +elementInteractions
            }
        }
}
