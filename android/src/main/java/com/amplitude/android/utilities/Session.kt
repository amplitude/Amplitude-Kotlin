package com.amplitude.android.utilities

import com.amplitude.android.Amplitude
import com.amplitude.android.Configuration
import com.amplitude.core.State
import com.amplitude.core.Storage
import com.amplitude.core.events.BaseEvent
import java.util.concurrent.atomic.AtomicLong

class Session(
    private var configuration: Configuration,
    private var storage: Storage? = null,
    private var state: State? = null,
) {
    companion object {
        const val EMPTY_SESSION_ID = -1L
        const val DEFAULT_LAST_EVENT_TIME = -1L
        const val DEFAULT_LAST_EVENT_ID = 0L
    }

    private val _sessionId = AtomicLong(EMPTY_SESSION_ID)
    private val _lastEventId = AtomicLong(DEFAULT_LAST_EVENT_ID)
    private val _lastEventTime = AtomicLong(DEFAULT_LAST_EVENT_TIME)

    val sessionId: Long
        get() {
            return _sessionId.get()
        }

    internal var lastEventId: Long = DEFAULT_LAST_EVENT_ID
        get() = _lastEventId.get()
    var lastEventTime: Long = DEFAULT_LAST_EVENT_TIME
        get() = _lastEventTime.get()

    init {
        loadFromStorage()
        state?.sessionId = _sessionId.get()
    }

    private fun loadFromStorage() {
        _sessionId.set(storage?.read(Storage.Constants.PREVIOUS_SESSION_ID)?.toLongOrNull() ?: EMPTY_SESSION_ID)
        _lastEventId.set(storage?.read(Storage.Constants.LAST_EVENT_ID)?.toLongOrNull() ?: DEFAULT_LAST_EVENT_ID)
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

    suspend fun setLastEventId(lastEventId: Long) {
        _lastEventId.set(lastEventId)
        storage?.write(Storage.Constants.LAST_EVENT_ID, lastEventId.toString())
    }

    suspend fun getAndSetNextEventId(): Long {
        val nextEventId = _lastEventId.incrementAndGet()
        storage?.write(Storage.Constants.LAST_EVENT_ID, lastEventId.toString())

        return nextEventId
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
        return "Session(sessionId=$sessionId, lastEventId=$lastEventId, lastEventTime=$lastEventTime)"
    }
}
