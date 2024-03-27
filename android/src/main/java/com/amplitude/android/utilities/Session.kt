package com.amplitude.android.utilities

import com.amplitude.android.Amplitude
import com.amplitude.android.Configuration
import com.amplitude.core.State
import com.amplitude.core.Storage
import com.amplitude.core.events.BaseEvent
import java.util.concurrent.atomic.AtomicLong

class Session(
    private var configuration: Configuration,
    internal var storage: Storage? = null,
    internal var state: State? = null,
) {
    companion object {
        const val EMPTY_SESSION_ID = -1L
        const val DEFAULT_LAST_EVENT_TIME = -1L
    }

    private val _sessionId = AtomicLong(EMPTY_SESSION_ID)
    private val _lastEventTime = AtomicLong(DEFAULT_LAST_EVENT_TIME)

    val sessionId: Long
        get() {
            return _sessionId.get()
        }

    var lastEventTime: Long = DEFAULT_LAST_EVENT_TIME
        get() = _lastEventTime.get()

    init {
        loadFromStorage()
    }

    private fun loadFromStorage() {
        _sessionId.set(storage?.read(Storage.Constants.PREVIOUS_SESSION_ID)?.toLongOrNull() ?: EMPTY_SESSION_ID)
        _lastEventTime.set(storage?.read(Storage.Constants.LAST_EVENT_TIME)?.toLongOrNull() ?: DEFAULT_LAST_EVENT_TIME)
    }

    /**
     * startNewSessionIfNeeded
     *
     * @param timestamp By default this is used as both `sessionId` and `timestamp`
     * @param sessionId If set, this is used as `sessionId`
     */
    suspend fun startNewSessionIfNeeded(timestamp: Long, sessionId: Long? = null): Iterable<BaseEvent>? {
        if (sessionId != null && this.sessionId != sessionId) {
            return startNewSession(timestamp, sessionId)
        }
        if (inSession() && isWithinMinTimeBetweenSessions(timestamp)) {
            refreshSessionTime(timestamp)
            return null
        }
        return startNewSession(timestamp, sessionId)
    }

    suspend fun setSessionId(timestamp: Long) {
        _sessionId.set(timestamp)
        storage?.write(Storage.Constants.PREVIOUS_SESSION_ID, timestamp.toString())
        state?.sessionId = timestamp
    }

    private suspend fun startNewSession(timestamp: Long, sessionId: Long? = null): Iterable<BaseEvent> {
        val _sessionId = sessionId ?: timestamp
        val sessionEvents = mutableListOf<BaseEvent>()
        // If any trackingSessionEvents is false (default value is true), means it is manually set
        @Suppress("DEPRECATION")
        val trackingSessionEvents = configuration.trackingSessionEvents && configuration.defaultTracking.sessions

        // end previous session
        if (trackingSessionEvents && inSession()) {
            val sessionEndEvent = BaseEvent()
            sessionEndEvent.eventType = Amplitude.END_SESSION_EVENT
            sessionEndEvent.timestamp = if (lastEventTime > 0) lastEventTime else null
            sessionEndEvent.sessionId = this.sessionId
            sessionEvents.add(sessionEndEvent)
        }

        // start new session
        setSessionId(_sessionId)
        refreshSessionTime(timestamp)
        if (trackingSessionEvents) {
            val sessionStartEvent = BaseEvent()
            sessionStartEvent.eventType = Amplitude.START_SESSION_EVENT
            sessionStartEvent.timestamp = timestamp
            sessionStartEvent.sessionId = _sessionId
            sessionEvents.add(sessionStartEvent)
        }

        return sessionEvents
    }

    suspend fun refreshSessionTime(timestamp: Long) {
        if (!inSession()) {
            return
        }
        _lastEventTime.set(timestamp)
        storage?.write(Storage.Constants.LAST_EVENT_TIME, timestamp.toString())
    }

    fun isWithinMinTimeBetweenSessions(timestamp: Long): Boolean {
        val sessionLimit: Long = configuration.minTimeBetweenSessionsMillis
        return timestamp - lastEventTime < sessionLimit
    }

    private fun inSession(): Boolean {
        return sessionId >= 0
    }

    override fun toString(): String {
        return "Session(sessionId=$sessionId, lastEventTime=$lastEventTime)"
    }
}
