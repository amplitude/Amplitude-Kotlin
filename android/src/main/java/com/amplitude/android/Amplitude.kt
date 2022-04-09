package com.amplitude.android

import android.content.Context
import com.amplitude.android.plugins.AndroidContextPlugin
import com.amplitude.android.plugins.AndroidLifecyclePlugin
import com.amplitude.core.Amplitude
import com.amplitude.core.Storage
import com.amplitude.core.platform.plugins.AmplitudeDestination
import com.amplitude.core.utilities.AnalyticsIdentityListener
import com.amplitude.core.utilities.FileStorage
import com.amplitude.id.FileIdentityStorageProvider
import com.amplitude.id.IdentityConfiguration
import com.amplitude.id.IdentityContainer
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

    override fun build() {
        val storageDirectory = (configuration as Configuration).context.getDir("${FileStorage.STORAGE_PREFIX}-${configuration.instanceName}", Context.MODE_PRIVATE)
        idContainer = IdentityContainer.getInstance(
            IdentityConfiguration(
                instanceName = configuration.instanceName,
                apiKey = configuration.apiKey,
                identityStorageProvider = FileIdentityStorageProvider(),
                storageDirectory = storageDirectory
            )
        )
        idContainer.identityManager.addIdentityListener(AnalyticsIdentityListener(store))
        amplitudeScope.launch(amplitudeDispatcher) {
            previousSessionId = storage.read(Storage.Constants.PREVIOUS_SESSION_ID) ?.let {
                it.toLong()
            } ?: -1
            if (previousSessionId >= 0) {
                sessionId = previousSessionId
            }
            lastEventId = storage.read(Storage.Constants.LAST_EVENT_ID) ?. let {
                it.toLong()
            } ?: 0
            lastEventTime = storage.read(Storage.Constants.LAST_EVENT_TIME) ?. let {
                it.toLong()
            } ?: -1
            add(AndroidContextPlugin())
        }
        add(AmplitudeDestination())
        add(AndroidLifecyclePlugin())
    }

    fun onEnterForeground(timestamp: Long) {
        amplitudeScope.launch(amplitudeDispatcher) {
            startNewSessionIfNeeded(timestamp)
            inForeground = true
        }
    }

    fun onExitForeground(timestamp: Long) {
        amplitudeScope.launch(amplitudeDispatcher) {
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
        if ((configuration as Configuration).trackingSessionEvents) {
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
