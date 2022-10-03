package com.amplitude.android

import android.content.Context
import com.amplitude.android.plugins.AndroidContextPlugin
import com.amplitude.android.plugins.AndroidLifecyclePlugin
import com.amplitude.core.Amplitude
import com.amplitude.core.Storage
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
    var sessionId: Long = -1
        private set
    internal var lastEventId: Long = 0
    var lastEventTime: Long = -1
    private var previousSessionId: Long = -1
    private lateinit var androidContextPlugin: AndroidContextPlugin

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
            previousSessionId = storage.read(Storage.Constants.PREVIOUS_SESSION_ID)?.toLong() ?: -1
            if (previousSessionId >= 0) {
                sessionId = previousSessionId
            }
            lastEventId = storage.read(Storage.Constants.LAST_EVENT_ID)?.toLong() ?: 0
            lastEventTime = storage.read(Storage.Constants.LAST_EVENT_TIME)?.toLong() ?: -1
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
        amplitudeScope.launch(amplitudeDispatcher) {
            isBuilt.await()
            startNewSessionIfNeeded(timestamp)
            inForeground = true
        }
    }

    fun onExitForeground() {
        amplitudeScope.launch(amplitudeDispatcher) {
            isBuilt.await()
            inForeground = false
            if ((configuration as Configuration).flushEventsOnClose) {
                flush()
            }
            storage.write(Storage.Constants.PREVIOUS_SESSION_ID, sessionId.toString())
            storage.write(Storage.Constants.LAST_EVENT_TIME, lastEventTime.toString())
        }
    }

    fun startNewSessionIfNeeded(timestamp: Long): Boolean {
        if (inSession()) {

            if (isWithinMinTimeBetweenSessions(timestamp)) {
                refreshSessionTime(timestamp)
                return false
            }

            startNewSession(timestamp)
            return true
        }

        // no current session - check for previous session
        if (isWithinMinTimeBetweenSessions(timestamp)) {
            if (previousSessionId == -1L) {
                startNewSession(timestamp)
                return true
            }

            // extend previous session
            setSessionId(previousSessionId)
            refreshSessionTime(timestamp)
            return false
        }

        startNewSession(timestamp)
        return true
    }

    private fun setSessionId(timestamp: Long) {
        sessionId = timestamp
        previousSessionId = timestamp
        amplitudeScope.launch(amplitudeDispatcher) {
            isBuilt.await()
            storage.write(Storage.Constants.PREVIOUS_SESSION_ID, timestamp.toString())
        }
    }

    private fun startNewSession(timestamp: Long) {
        // end previous session
        if ((configuration as Configuration).trackingSessionEvents) {
            sendSessionEvent(END_SESSION_EVENT)
        }

        // start new session
        setSessionId(timestamp)
        refreshSessionTime(timestamp)
        if (configuration.trackingSessionEvents) {
            sendSessionEvent(START_SESSION_EVENT)
        }
    }

    private fun sendSessionEvent(sessionEvent: String) {
        if (!inSession()) {
            return
        }
        track(sessionEvent)
    }

    fun refreshSessionTime(timestamp: Long) {
        if (!inSession()) {
            return
        }
        lastEventTime = timestamp
        amplitudeScope.launch(amplitudeDispatcher) {
            isBuilt.await()
            storage.write(Storage.Constants.LAST_EVENT_TIME, timestamp.toString())
        }
    }

    private fun isWithinMinTimeBetweenSessions(timestamp: Long): Boolean {
        val sessionLimit: Long = (configuration as Configuration).minTimeBetweenSessionsMillis
        return timestamp - lastEventTime < sessionLimit
    }

    private fun inSession(): Boolean {
        return sessionId >= 0
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
