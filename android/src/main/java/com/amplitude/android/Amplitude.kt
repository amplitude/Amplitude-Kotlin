package com.amplitude.android

import android.content.Context
import com.amplitude.android.plugins.AndroidContextPlugin
import com.amplitude.android.plugins.AndroidLifecyclePlugin
import com.amplitude.core.Amplitude
import com.amplitude.core.events.BaseEvent
import com.amplitude.core.platform.plugins.AmplitudeDestination
import com.amplitude.core.platform.plugins.GetAmpliExtrasPlugin
import com.amplitude.core.utilities.AnalyticsIdentityListener
import com.amplitude.core.utilities.FileStorage
import com.amplitude.id.FileIdentityStorageProvider
import com.amplitude.id.IdentityConfiguration
import com.amplitude.id.IdentityContainer
import com.amplitude.id.IdentityUpdateType
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.launch

open class Amplitude(
    configuration: Configuration
) : Amplitude(configuration) {

    internal var inForeground = false
    private lateinit var androidContextPlugin: AndroidContextPlugin

    init {
        (timeline as Timeline).start()
        registerShutdownHook()
    }

    override fun createTimeline(): Timeline {
        return Timeline().also { it.amplitude = this }
    }

    override fun build(): Deferred<Boolean> {
        val client = this

        val built = amplitudeScope.async(amplitudeDispatcher) {
            storage = configuration.storageProvider.getStorage(client)

            val storageDirectory = (configuration as Configuration).context.getDir("${FileStorage.STORAGE_PREFIX}-${configuration.instanceName}", Context.MODE_PRIVATE)
            idContainer = IdentityContainer.getInstance(
                IdentityConfiguration(
                    instanceName = configuration.instanceName,
                    apiKey = configuration.apiKey,
                    identityStorageProvider = FileIdentityStorageProvider(),
                    storageDirectory = storageDirectory
                )
            )
            val listener = AnalyticsIdentityListener(store)
            idContainer.identityManager.addIdentityListener(listener)
            if (idContainer.identityManager.isInitialized()) {
                listener.onIdentityChanged(idContainer.identityManager.getIdentity(), IdentityUpdateType.Initialized)
            }
            androidContextPlugin = AndroidContextPlugin()
            add(androidContextPlugin)
            add(GetAmpliExtrasPlugin())
            add(AndroidLifecyclePlugin())
            true
        }
        add(AmplitudeDestination())
        return built
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

        val dummySessionStartEvent = BaseEvent()
        dummySessionStartEvent.eventType = START_SESSION_EVENT
        dummySessionStartEvent.timestamp = timestamp
        dummySessionStartEvent.sessionId = -1
        timeline.process(dummySessionStartEvent)
    }

    fun onExitForeground() {
        inForeground = false

        amplitudeScope.launch(amplitudeDispatcher) {
            isBuilt.await()
            if ((configuration as Configuration).flushEventsOnClose) {
                flush()
            }
        }
    }

    private fun registerShutdownHook() {
        Runtime.getRuntime().addShutdownHook(object : Thread() {
            override fun run() {
                (this@Amplitude.timeline as Timeline).stop()
            }
        })
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
fun Amplitude(apiKey: String, context: Context, configs: Configuration.() -> Unit): com.amplitude.android.Amplitude {
    val config = Configuration(apiKey, context)
    configs.invoke(config)
    return com.amplitude.android.Amplitude(config)
}
