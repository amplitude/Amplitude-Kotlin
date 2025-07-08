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
import com.amplitude.core.State
import com.amplitude.core.platform.plugins.AmplitudeDestination
import com.amplitude.core.platform.plugins.GetAmpliExtrasPlugin
import com.amplitude.id.IdentityConfiguration
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
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
        if (AutocaptureOption.APP_LIFECYCLES in configuration.autocapture) {
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

        (timeline as Timeline).start()
    }

    /**
     * Reset identity:
     *  - reset userId to "null"
     *  - reset deviceId via AndroidContextPlugin
     * @return the Amplitude instance
     */
    override fun reset(): Amplitude {
        this.setUserId(null)
        amplitudeScope.launch(amplitudeDispatcher) {
            isBuilt.await()
            idContainer.identityManager.editIdentity().setDeviceId(null).commit()
            androidContextPlugin.initializeDeviceId(configuration as Configuration)
        }
        return this
    }

    @InternalAmplitudeFeature
    fun onEnterForeground(timestamp: Long) {
        (timeline as Timeline).onEnterForeground(timestamp)
    }

    @InternalAmplitudeFeature
    fun onExitForeground(timestamp: Long) {
        (timeline as Timeline).onExitForeground(timestamp)
    }

    private fun registerShutdownHook() {
        Runtime.getRuntime().addShutdownHook(
            object : Thread() {
                override fun run() {
                    (this@Amplitude.timeline as Timeline).stop()
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
