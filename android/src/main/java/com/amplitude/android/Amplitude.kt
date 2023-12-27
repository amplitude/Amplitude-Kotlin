package com.amplitude.android

import AndroidNetworkListener
import android.content.Context
import com.amplitude.android.migration.ApiKeyStorageMigration
import com.amplitude.android.migration.RemnantDataMigration
import com.amplitude.android.plugins.AnalyticsConnectorIdentityPlugin
import com.amplitude.android.plugins.AnalyticsConnectorPlugin
import com.amplitude.android.plugins.AndroidContextPlugin
import com.amplitude.android.plugins.AndroidLifecyclePlugin
import com.amplitude.android.utilities.AndroidNetworkConnectivityChecker
import com.amplitude.core.Amplitude
import com.amplitude.core.events.BaseEvent
import com.amplitude.core.platform.plugins.AmplitudeDestination
import com.amplitude.core.platform.plugins.GetAmpliExtrasPlugin
import com.amplitude.core.utilities.FileStorage
import com.amplitude.id.IdentityConfiguration
import kotlinx.coroutines.launch

open class Amplitude(
    configuration: Configuration,
) : Amplitude(configuration), AndroidNetworkListener.NetworkChangeCallback {
    internal var inForeground = false
    private lateinit var androidContextPlugin: AndroidContextPlugin
    private var networkListener: AndroidNetworkListener
    private val networkChangeHandler =
        object : AndroidNetworkListener.NetworkChangeCallback {
            override fun onNetworkAvailable() {
                flush()
            }

            override fun onNetworkUnavailable() {
                // Nothing to do so far
            }
        }

    val sessionId: Long
        get() {
            return (timeline as Timeline).sessionId
        }

    init {
        registerShutdownHook()
        networkListener = AndroidNetworkListener((this.configuration as Configuration).context)
        networkListener.setNetworkChangeCallback(networkChangeHandler)
        networkListener.startListening()
    }

    override fun createTimeline(): Timeline {
        return Timeline().also { it.amplitude = this }
    }

    override fun createIdentityConfiguration(): IdentityConfiguration {
        val configuration = configuration as Configuration
        val storageDirectory = configuration.context.getDir("${FileStorage.STORAGE_PREFIX}-${configuration.instanceName}", Context.MODE_PRIVATE)

        return IdentityConfiguration(
            instanceName = configuration.instanceName,
            apiKey = configuration.apiKey,
            identityStorageProvider = configuration.identityStorageProvider,
            storageDirectory = storageDirectory,
            logger = configuration.loggerProvider.getLogger(this)
        )
    }

    override suspend fun buildInternal(identityConfiguration: IdentityConfiguration) {
        ApiKeyStorageMigration(this).execute()

        if ((this.configuration as Configuration).migrateLegacyData) {
            RemnantDataMigration(this).execute()
        }
        this.createIdentityContainer(identityConfiguration)

        androidContextPlugin = AndroidContextPlugin()
        add(androidContextPlugin)
        add(GetAmpliExtrasPlugin())
        add(AndroidLifecyclePlugin())
        add(AnalyticsConnectorIdentityPlugin())
        add(AnalyticsConnectorPlugin())
        add(AmplitudeDestination(AndroidNetworkConnectivityChecker(this.configuration.context, this.logger)))

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

    fun onEnterForeground(timestamp: Long) {
        inForeground = true

        if ((configuration as Configuration).optOut) {
            return
        }

        val dummyEnterForegroundEvent = BaseEvent()
        dummyEnterForegroundEvent.eventType = DUMMY_ENTER_FOREGROUND_EVENT
        dummyEnterForegroundEvent.timestamp = timestamp
        timeline.process(dummyEnterForegroundEvent)
    }

    fun onExitForeground(timestamp: Long) {
        inForeground = false

        val dummyExitForegroundEvent = BaseEvent()
        dummyExitForegroundEvent.eventType = DUMMY_EXIT_FOREGROUND_EVENT
        dummyExitForegroundEvent.timestamp = timestamp
        timeline.process(dummyExitForegroundEvent)

        amplitudeScope.launch(amplitudeDispatcher) {
            isBuilt.await()
            if ((configuration as Configuration).flushEventsOnClose) {
                flush()
            }
        }
    }

    private fun registerShutdownHook() {
        Runtime.getRuntime().addShutdownHook(
            object : Thread() {
                override fun run() {
                    (this@Amplitude.timeline as Timeline).stop()
                    (this@Amplitude.networkListener as AndroidNetworkListener).stopListening()
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

        /**
         * The event type for dummy enter foreground events.
         */
        internal const val DUMMY_ENTER_FOREGROUND_EVENT = "dummy_enter_foreground"
        /**
         * The event type for dummy exit foreground events.
         */
        internal const val DUMMY_EXIT_FOREGROUND_EVENT = "dummy_exit_foreground"
    }

    override fun onNetworkAvailable() {
        networkChangeHandler.onNetworkAvailable()
    }

    override fun onNetworkUnavailable() {
        networkChangeHandler.onNetworkUnavailable()
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
fun Amplitude(apiKey: String, context: Context, configs: Configuration.() -> Unit): com.amplitude.android.Amplitude {
    val config = Configuration(apiKey, context)
    configs.invoke(config)
    return com.amplitude.android.Amplitude(config)
}
