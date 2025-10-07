package com.amplitude.android

import android.content.Context
import com.amplitude.android.storage.AndroidStorageContextV3
import com.amplitude.android.utilities.AndroidLoggerProvider
import com.amplitude.core.Configuration
import com.amplitude.core.EventCallBack
import com.amplitude.core.LoggerProvider
import com.amplitude.core.ServerZone
import com.amplitude.core.StorageProvider
import com.amplitude.core.events.IngestionMetadata
import com.amplitude.core.events.Plan
import com.amplitude.core.utilities.http.HttpClientInterface
import com.amplitude.id.IdentityStorageProvider
import java.io.File

open class Configuration(
    apiKey: String,
    val context: Context,
    override var flushQueueSize: Int = FLUSH_QUEUE_SIZE,
    override var flushIntervalMillis: Int = FLUSH_INTERVAL_MILLIS,
    override var instanceName: String = DEFAULT_INSTANCE,
    override var optOut: Boolean = false,
    override var storageProvider: StorageProvider = AndroidStorageContextV3.eventsStorageProvider,
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
    var locationListening: Boolean = false,
    var flushEventsOnClose: Boolean = true,
    var minTimeBetweenSessionsMillis: Long = MIN_TIME_BETWEEN_SESSIONS_MILLIS,
    autocapture: Set<AutocaptureOption> = setOf(AutocaptureOption.SESSIONS),
    override var identifyBatchIntervalMillis: Long = IDENTIFY_BATCH_INTERVAL_MILLIS,
    override var identifyInterceptStorageProvider: StorageProvider = AndroidStorageContextV3.identifyInterceptStorageProvider,
    override var identityStorageProvider: IdentityStorageProvider = AndroidStorageContextV3.identityStorageProvider,
    var migrateLegacyData: Boolean = true,
    override var offline: Boolean? = false,
    override var deviceId: String? = null,
    override var sessionId: Long? = null,
    override var httpClient: HttpClientInterface? = null,
    var interactionsOptions: InteractionsOptions = InteractionsOptions(),
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

    @Deprecated("Please use the 'autocapture' parameter instead.")
    @JvmOverloads
    constructor(
        apiKey: String,
        context: Context,
        flushQueueSize: Int = FLUSH_QUEUE_SIZE,
        flushIntervalMillis: Int = FLUSH_INTERVAL_MILLIS,
        instanceName: String = DEFAULT_INSTANCE,
        optOut: Boolean = false,
        storageProvider: StorageProvider = AndroidStorageContextV3.eventsStorageProvider,
        loggerProvider: LoggerProvider = AndroidLoggerProvider(),
        minIdLength: Int? = null,
        partnerId: String? = null,
        callback: EventCallBack? = null,
        flushMaxRetries: Int = FLUSH_MAX_RETRIES,
        useBatch: Boolean = false,
        serverZone: ServerZone = ServerZone.US,
        serverUrl: String? = null,
        plan: Plan? = null,
        ingestionMetadata: IngestionMetadata? = null,
        useAdvertisingIdForDeviceId: Boolean = false,
        useAppSetIdForDeviceId: Boolean = false,
        newDeviceIdPerInstall: Boolean = false,
        trackingOptions: TrackingOptions = TrackingOptions(),
        enableCoppaControl: Boolean = false,
        locationListening: Boolean = false,
        flushEventsOnClose: Boolean = true,
        minTimeBetweenSessionsMillis: Long = MIN_TIME_BETWEEN_SESSIONS_MILLIS,
        trackingSessionEvents: Boolean = true,
        @Suppress("DEPRECATION") defaultTracking: DefaultTrackingOptions = DefaultTrackingOptions(),
        identifyBatchIntervalMillis: Long = IDENTIFY_BATCH_INTERVAL_MILLIS,
        identifyInterceptStorageProvider: StorageProvider = AndroidStorageContextV3.identifyInterceptStorageProvider,
        identityStorageProvider: IdentityStorageProvider = AndroidStorageContextV3.identityStorageProvider,
        migrateLegacyData: Boolean = true,
        offline: Boolean? = false,
        deviceId: String? = null,
        sessionId: Long? = null,
        httpClient: HttpClientInterface? = null,
        interactionsOptions: InteractionsOptions = InteractionsOptions(),
    ) : this(
        apiKey,
        context,
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
        useAdvertisingIdForDeviceId,
        useAppSetIdForDeviceId,
        newDeviceIdPerInstall,
        trackingOptions,
        enableCoppaControl,
        locationListening,
        flushEventsOnClose,
        minTimeBetweenSessionsMillis,
        defaultTracking.autocaptureOptions,
        identifyBatchIntervalMillis,
        identifyInterceptStorageProvider,
        identityStorageProvider,
        migrateLegacyData,
        offline,
        deviceId,
        sessionId,
        httpClient,
        interactionsOptions,
    ) {
        if (!trackingSessionEvents) {
            defaultTracking.sessions = false
        }
        @Suppress("DEPRECATION")
        this.defaultTracking = defaultTracking
    }

    private var storageDirectory: File? = null

    // A backing property to store the autocapture options. Any changes to `trackingSessionEvents`
    // or the `defaultTracking` options will be reflected in this property.
    private var _autocapture: MutableSet<AutocaptureOption> = autocapture.toMutableSet()
    val autocapture: Set<AutocaptureOption> get() = _autocapture

    @Deprecated("Please use 'autocapture' instead and set 'AutocaptureOptions.SESSIONS' to enable the option.")
    var trackingSessionEvents: Boolean
        get() = AutocaptureOption.SESSIONS in _autocapture
        set(value) {
            if (value) {
                _autocapture.add(AutocaptureOption.SESSIONS)
            } else {
                _autocapture.remove(AutocaptureOption.SESSIONS)
            }
        }

    // Any changes to the default tracking options replace the recent autocapture options entirely.
    @Suppress("DEPRECATION")
    @Deprecated("Please use 'autocapture' instead", ReplaceWith("autocapture"))
    var defaultTracking: DefaultTrackingOptions = DefaultTrackingOptions { updateAutocaptureOnPropertyChange() }
        set(value) {
            field = value
            _autocapture = value.autocaptureOptions
            value.addPropertyChangeListener { updateAutocaptureOnPropertyChange() }
        }

    @Suppress("DEPRECATION")
    private fun DefaultTrackingOptions.updateAutocaptureOnPropertyChange() {
        _autocapture = autocaptureOptions
    }

    internal fun getStorageDirectory(): File {
        if (storageDirectory == null) {
            val dir = context.getDir("amplitude", Context.MODE_PRIVATE)
            storageDirectory = File(dir, "${context.packageName}/$instanceName/analytics/")
            storageDirectory?.mkdirs()
        }
        return storageDirectory!!
    }
}
