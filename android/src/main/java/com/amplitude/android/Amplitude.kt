package com.amplitude.android

import android.content.Context
import com.amplitude.android.plugins.AndroidContextPlugin
import com.amplitude.android.plugins.AndroidLifecyclePlugin
import com.amplitude.core.Amplitude
import com.amplitude.core.Storage
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
    var sessionId: Long
        private set
    internal var lastEventId: Long
    var lastEventTime: Long
    private lateinit var androidContextPlugin: AndroidContextPlugin

    init {
        storage = configuration.storageProvider.getStorage(this)

        this.sessionId = storage.read(Storage.Constants.PREVIOUS_SESSION_ID)?.toLong() ?: -1
        this.lastEventId = storage.read(Storage.Constants.LAST_EVENT_ID)?.toLong() ?: 0
        this.lastEventTime = storage.read(Storage.Constants.LAST_EVENT_TIME)?.toLong() ?: -1
    }

    override fun build(): Deferred<Boolean> {
        val built = amplitudeScope.async(amplitudeDispatcher) {
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

    override fun processEvent(event: BaseEvent): Iterable<BaseEvent>? {
        val eventTimestamp = event.timestamp ?: System.currentTimeMillis()
        event.timestamp = eventTimestamp
        var sessionEvents: Iterable<BaseEvent>? = null

        if (!(event.eventType == START_SESSION_EVENT || event.eventType == END_SESSION_EVENT)) {
            if (!inForeground) {
                sessionEvents = startNewSessionIfNeeded(eventTimestamp)
            } else {
                refreshSessionTime(eventTimestamp)
            }
        }

        if (event.sessionId < 0) {
            event.sessionId = sessionId
        }

        val savedLastEventId = lastEventId

        sessionEvents ?. let {
            it.forEach { e ->
                e.eventId ?: let {
                    val newEventId = lastEventId + 1
                    e.eventId = newEventId
                    lastEventId = newEventId
                }
            }
        }

        event.eventId ?: let {
            val newEventId = lastEventId + 1
            event.eventId = newEventId
            lastEventId = newEventId
        }

        if (lastEventId > savedLastEventId) {
            amplitudeScope.launch(amplitudeDispatcher) {
                storage.write(Storage.Constants.LAST_EVENT_ID, lastEventId.toString())
            }
        }

        return sessionEvents
    }

    fun onEnterForeground(timestamp: Long) {
        startNewSessionIfNeeded(timestamp) ?. let {
            it.forEach { event -> process(event) }
        }
        inForeground = true
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

    fun startNewSessionIfNeeded(timestamp: Long): Iterable<BaseEvent>? {
        if (inSession()) {

            if (isWithinMinTimeBetweenSessions(timestamp)) {
                refreshSessionTime(timestamp)
                return null
            }

            return startNewSession(timestamp)
        }

        return startNewSession(timestamp)
    }

    private fun setSessionId(timestamp: Long) {
        sessionId = timestamp
        amplitudeScope.launch(amplitudeDispatcher) {
            storage.write(Storage.Constants.PREVIOUS_SESSION_ID, sessionId.toString())
        }
    }

    private fun startNewSession(timestamp: Long): Iterable<BaseEvent> {
        val sessionEvents = mutableListOf<BaseEvent>()

        // end previous session
        if ((configuration as Configuration).trackingSessionEvents && inSession()) {
            val sessionEndEvent = BaseEvent()
            sessionEndEvent.eventType = END_SESSION_EVENT
            sessionEndEvent.timestamp = if (lastEventTime > 0) lastEventTime else null
            sessionEndEvent.sessionId = sessionId
            sessionEvents.add(sessionEndEvent)
        }

        // start new session
        setSessionId(timestamp)
        refreshSessionTime(timestamp)
        if (configuration.trackingSessionEvents) {
            val sessionStartEvent = BaseEvent()
            sessionStartEvent.eventType = START_SESSION_EVENT
            sessionStartEvent.timestamp = timestamp
            sessionStartEvent.sessionId = sessionId
            sessionEvents.add(sessionStartEvent)
        }

        return sessionEvents
    }

    fun refreshSessionTime(timestamp: Long) {
        if (!inSession()) {
            return
        }
        lastEventTime = timestamp
        amplitudeScope.launch(amplitudeDispatcher) {
            storage.write(Storage.Constants.LAST_EVENT_TIME, lastEventTime.toString())
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
