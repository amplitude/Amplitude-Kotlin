package com.amplitude.android

import android.app.Application
import android.content.Context
import com.amplitude.android.diagnostics.AndroidDiagnosticsContextProvider
import com.amplitude.android.migration.MigrationManager
import com.amplitude.android.plugins.AnalyticsConnectorIdentityPlugin
import com.amplitude.android.plugins.AnalyticsConnectorPlugin
import com.amplitude.android.plugins.AndroidContextPlugin
import com.amplitude.android.plugins.AndroidContextPlugin.Companion.validDeviceId
import com.amplitude.android.plugins.AndroidLifecyclePlugin
import com.amplitude.android.plugins.AndroidNetworkConnectivityCheckerPlugin
import com.amplitude.android.storage.AndroidStorageContextV3
import com.amplitude.android.utilities.ActivityLifecycleObserver
import com.amplitude.core.State
import com.amplitude.core.diagnostics.DiagnosticsContextProvider
import com.amplitude.core.platform.plugins.AmplitudeDestination
import com.amplitude.core.platform.plugins.GetAmpliExtrasPlugin
import com.amplitude.id.IdentityConfiguration
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import java.io.File
import java.util.concurrent.Executors
import com.amplitude.core.Amplitude as CoreAmplitude

open class Amplitude internal constructor(
    configuration: Configuration,
    state: State,
    amplitudeScope: CoroutineScope = CoroutineScope(SupervisorJob()),
    amplitudeDispatcher: CoroutineDispatcher = Executors.newCachedThreadPool().asCoroutineDispatcher(),
    networkIODispatcher: CoroutineDispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher(),
    storageIODispatcher: CoroutineDispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher(),
) : CoreAmplitude(
        configuration = configuration,
        store = state,
        amplitudeScope = amplitudeScope,
        amplitudeDispatcher = amplitudeDispatcher,
        networkIODispatcher = networkIODispatcher,
        storageIODispatcher = storageIODispatcher,
    ) {
    constructor(configuration: Configuration) : this(configuration, State())

    private lateinit var androidContextPlugin: AndroidContextPlugin

    val sessionId: Long
        get() {
            return (timeline as Timeline).sessionId
        }

    private lateinit var activityLifecycleCallbacks: ActivityLifecycleObserver

    /**
     * This build call is initiated by parent class and happens before this class
     * init block
     */
    override fun build(): Deferred<Boolean> {
        activityLifecycleCallbacks = ActivityLifecycleObserver()
        return super.build()
    }

    init {
        registerShutdownHook()

        if (configuration.autocapture.any { it in AutocaptureOption.REQUIRES_ACTIVITY_CALLBACKS }) {
            with(configuration.context as Application) {
                registerActivityLifecycleCallbacks(activityLifecycleCallbacks)
            }
        }
    }

    override fun createTimeline(): Timeline {
        return Timeline(configuration.sessionId).also { it.amplitude = this }
    }

    override fun createIdentityConfiguration(): IdentityConfiguration {
        val configuration = configuration as Configuration

        return IdentityConfiguration(
            instanceName = configuration.instanceName,
            apiKey = configuration.apiKey,
            identityStorageProvider = configuration.identityStorageProvider,
            storageDirectory = AndroidStorageContextV3.getIdentityStorageDirectory(configuration),
            logger = configuration.loggerProvider.getLogger(this),
            fileName = AndroidStorageContextV3.getIdentityStorageFileName(),
        )
    }

    override suspend fun buildInternal(identityConfiguration: IdentityConfiguration) {
        val migrationManager = MigrationManager(this)
        migrationManager.migrateOldStorage()

        this.createIdentityContainer(identityConfiguration)

        if (this.configuration.offline != AndroidNetworkConnectivityCheckerPlugin.Disabled) {
            add(AndroidNetworkConnectivityCheckerPlugin())
        }
        androidContextPlugin = AndroidContextPlugin()
        add(androidContextPlugin)
        val deviceId =
            configuration.deviceId
                ?: store.deviceId?.takeIf { validDeviceId(it, allowAppSetId = false) }
                ?: androidContextPlugin.createDeviceId()
        setDeviceId(deviceId)
        add(GetAmpliExtrasPlugin())
        add(AndroidLifecyclePlugin(activityLifecycleCallbacks))
        add(AnalyticsConnectorIdentityPlugin())
        add(AnalyticsConnectorPlugin())
        add(AmplitudeDestination())

        (timeline as Timeline).start()
    }

    override fun diagnosticsContextProvider(): DiagnosticsContextProvider? {
        val configuration = configuration as Configuration
        return AndroidDiagnosticsContextProvider(configuration.context)
    }

    override fun diagnosticsStorageDirectory(): File {
        val configuration = configuration as Configuration
        return configuration.getStorageDirectory()
    }

    /**
     * Reset identity:
     *  - reset userId to null
     *  - generate and set a new deviceId
     * @return the Amplitude instance
     */
    override fun reset(): Amplitude {
        if (!isBuilt.isCompleted) {
            logger.error("Cannot reset identity before Amplitude is initialized.")
            return this
        }
        setUserId(null)
        setDeviceId(androidContextPlugin.createDeviceId())
        return this
    }

    override fun setUserId(userId: String?): Amplitude {
        store.userId = userId
        super.setUserId(userId)
        return this
    }

    override fun setDeviceId(deviceId: String): Amplitude {
        store.deviceId = deviceId
        super.setDeviceId(deviceId)
        return this
    }

    @GuardedAmplitudeFeature
    fun onEnterForeground(timestamp: Long) {
        (timeline as Timeline).onEnterForeground(timestamp)
    }

    @GuardedAmplitudeFeature
    fun onExitForeground(timestamp: Long) {
        (timeline as Timeline).onExitForeground(timestamp)
    }

    private fun registerShutdownHook() {
        // close the stream if the app shuts down
        try {
            Runtime.getRuntime().addShutdownHook(
                object : Thread() {
                    override fun run() {
                        (this@Amplitude.timeline as Timeline).stop()
                    }
                },
            )
        } catch (e: IllegalStateException) {
            // Once the shutdown sequence has begun it is impossible to register a shutdown hook,
            // so we just ignore the IllegalStateException that's thrown.
            // https://developer.android.com/reference/java/lang/Runtime#addShutdownHook(java.lang.Thread)
        }
    }

    companion object {
        /**
         * The event type for start session events.
         */
        const val START_SESSION_EVENT = "session_start"

        /**
         * The event type for end session events.
         */
        const val END_SESSION_EVENT = "session_end"
    }
}

/**
 * constructor function to build amplitude in dsl format with config options
 * Usage: Amplitude("123", context) {
 *            this.flushQueueSize = 10
 *        }
 *
 * NOTE: this method should only be used for Android application.
 *
 * @param apiKey Api Key
 * @param context Android Context
 * @param configs Configuration
 * @return Amplitude Android Instance
 */
fun Amplitude(
    apiKey: String,
    context: Context,
    configs: Configuration.() -> Unit,
): Amplitude {
    val config = Configuration(apiKey, context)
    configs.invoke(config)
    return Amplitude(config)
}
