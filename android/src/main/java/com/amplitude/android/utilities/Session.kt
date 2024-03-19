package com.amplitude.android.utilities

import com.amplitude.android.Amplitude
import com.amplitude.android.Configuration
import com.amplitude.common.Logger
import com.amplitude.core.State
import com.amplitude.core.Storage
import com.amplitude.core.events.BaseEvent
import java.util.concurrent.atomic.AtomicLong

class Session(
    private var configuration: Configuration,
    private var storage: Storage? = null,
    private var state: State? = null,
) {
    private val _sessionId = AtomicLong(configuration.sessionId ?: -1L)

    val sessionId: Long
        get() {
            return _sessionId.get()
        }

    internal var lastEventId: Long = 0
    var lastEventTime: Long = -1L

    init {
        val currentSessionId = configuration.sessionId
            ?: (storage?.read(Storage.Constants.PREVIOUS_SESSION_ID)?.toLongOrNull() ?: -1)

        _sessionId.set(currentSessionId)
        state?.sessionId = currentSessionId
        lastEventId = storage?.read(Storage.Constants.LAST_EVENT_ID)?.toLongOrNull() ?: 0
        lastEventTime = storage?.read(Storage.Constants.LAST_EVENT_TIME)?.toLongOrNull() ?: -1
    }

    suspend fun startNewSessionIfNeeded(timestamp: Long): Iterable<BaseEvent>? {
        if (inSession() && isWithinMinTimeBetweenSessions(timestamp)) {
            refreshSessionTime(timestamp)
            return null
        }
        return startNewSession(timestamp)
    }

    suspend fun setSessionId(timestamp: Long) {
        _sessionId.set(timestamp)
        storage?.write(Storage.Constants.PREVIOUS_SESSION_ID, timestamp.toString())
        state?.sessionId = timestamp
    }

    suspend fun startNewSession(timestamp: Long): Iterable<BaseEvent> {
        val sessionEvents = mutableListOf<BaseEvent>()
        // If any trackingSessionEvents is false (default value is true), means it is manually set
        @Suppress("DEPRECATION")
        val trackingSessionEvents = configuration.trackingSessionEvents && configuration.defaultTracking.sessions

        // end previous session
        if (trackingSessionEvents && inSession()) {
            val sessionEndEvent = BaseEvent()
            sessionEndEvent.eventType = Amplitude.END_SESSION_EVENT
            sessionEndEvent.timestamp = if (lastEventTime > 0) lastEventTime else null
            sessionEndEvent.sessionId = sessionId
            sessionEvents.add(sessionEndEvent)
        }

        // start new session
        setSessionId(timestamp)
        refreshSessionTime(timestamp)
        if (trackingSessionEvents) {
            val sessionStartEvent = BaseEvent()
            sessionStartEvent.eventType = Amplitude.START_SESSION_EVENT
            sessionStartEvent.timestamp = timestamp
            sessionStartEvent.sessionId = sessionId
            sessionEvents.add(sessionStartEvent)
        }

        return sessionEvents
    }

    suspend fun refreshSessionTime(timestamp: Long) {
        if (!inSession()) {
            return
        }
        lastEventTime = timestamp
        storage?.write(Storage.Constants.LAST_EVENT_TIME, lastEventTime.toString())
    }

    fun isWithinMinTimeBetweenSessions(timestamp: Long): Boolean {
        val sessionLimit: Long = configuration.minTimeBetweenSessionsMillis
        return timestamp - lastEventTime < sessionLimit
    }

    private fun inSession(): Boolean {
        return sessionId >= 0
    }

    override fun toString(): String {
        return "Session(sessionId=$sessionId, lastEventId=$lastEventId, lastEventTime=$lastEventTime)"
    }
}
