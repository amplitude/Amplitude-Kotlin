package com.amplitude.android

import android.app.Application
import android.content.Context
import com.amplitude.android.migration.MigrationManager
import com.amplitude.android.plugins.AnalyticsConnectorIdentityPlugin
import com.amplitude.android.plugins.AnalyticsConnectorPlugin
import com.amplitude.android.plugins.AndroidContextPlugin
import com.amplitude.android.plugins.AndroidLifecyclePlugin
import com.amplitude.android.plugins.AndroidNetworkConnectivityCheckerPlugin
import com.amplitude.android.storage.AndroidStorageContextV3
import com.amplitude.android.utilities.ActivityLifecycleObserver
import com.amplitude.core.platform.plugins.AmplitudeDestination
import com.amplitude.core.platform.plugins.GetAmpliExtrasPlugin
import com.amplitude.id.IdentityConfiguration
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import java.util.concurrent.Executors
import com.amplitude.core.Amplitude as CoreAmplitude

open class Amplitude(
    configuration: Configuration,
    amplitudeScope: CoroutineScope = CoroutineScope(SupervisorJob()),
    amplitudeDispatcher: CoroutineDispatcher = Executors.newCachedThreadPool().asCoroutineDispatcher(),
    networkIODispatcher: CoroutineDispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher(),
    storageIODispatcher: CoroutineDispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher(),
    private val activityLifecycleCallbacks: ActivityLifecycleObserver = ActivityLifecycleObserver(),
) : CoreAmplitude(
        configuration = configuration,
        amplitudeScope = amplitudeScope,
        amplitudeDispatcher = amplitudeDispatcher,
        networkIODispatcher = networkIODispatcher,
        storageIODispatcher = storageIODispatcher,
    ) {
    private lateinit var androidContextPlugin: AndroidContextPlugin

    override fun track(
        eventType: String,
        eventProperties: Map<String, Any>?,
    ) {
        track(
            eventType = eventType,
            eventProperties = eventProperties,
            options = null,
        )
    }

    init {
        registerShutdownHook()
        if (AutocaptureOption.APP_LIFECYCLES in configuration.autocapture) {
            with(configuration.context as Application) {
                registerActivityLifecycleCallbacks(activityLifecycleCallbacks)
            }
        }
    }

    override fun createTimeline(): Timeline {
        return Timeline(this)
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

        createIdentityContainer(identityConfiguration)

        if (this.configuration.offline != AndroidNetworkConnectivityCheckerPlugin.Disabled) {
            add(AndroidNetworkConnectivityCheckerPlugin())
        }
        androidContextPlugin =
            object : AndroidContextPlugin() {
                override fun setDeviceId(deviceId: String) {
                    // call internal method to set deviceId immediately i.e. dont' wait for build() to complete
                    this@Amplitude.setDeviceIdInternal(deviceId)
                }
            }
        add(androidContextPlugin)
        add(GetAmpliExtrasPlugin())
        add(AndroidLifecyclePlugin(activityLifecycleCallbacks))
        add(AnalyticsConnectorIdentityPlugin())
        add(AnalyticsConnectorPlugin())
        add(AmplitudeDestination())

        timeline.start()
    }

    /**
     * Reset identity:
     *  - reset userId to "null"
     *  - reset deviceId via AndroidContextPlugin
     * @return the Amplitude instance
     */
    override fun reset(): Amplitude {
        amplitudeScope.launch(amplitudeDispatcher) {
            isBuilt.await()
            idContainer.identityManager.editIdentity {
                setUserId(null)
                clearUserProperties()
            }
            androidContextPlugin.initializeDeviceId(forceReset = true)
        }
        return this
    }

    @Deprecated("This method is deprecated and a no-op.")
    fun onEnterForeground(timestamp: Long) {
        // no-op
    }

    @Deprecated("This method is deprecated and a no-op.")
    fun onExitForeground(timestamp: Long) {
        // no-op
    }

    private fun registerShutdownHook() {
        Runtime.getRuntime().addShutdownHook(
            object : Thread() {
                override fun run() {
                    timeline.stop()
                }
            },
        )
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
