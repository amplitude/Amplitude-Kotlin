package com.amplitude.android

import com.amplitude.core.Storage
import com.amplitude.core.events.BaseEvent
import com.amplitude.core.platform.Timeline
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong

class Timeline(
    private val initialSessionId: Long? = null,
) : Timeline() {
    private val eventMessageChannel: Channel<EventQueueMessage> = Channel(Channel.UNLIMITED)

    private val _sessionId = AtomicLong(initialSessionId ?: -1L)
    private var _foreground = false
    val sessionId: Long
        get() {
            return _sessionId.get()
        }

    internal var lastEventId: Long = 0
    var lastEventTime: Long = -1L

    internal fun start() {
        amplitude.amplitudeScope.launch(amplitude.storageIODispatcher) {
            // Wait until build (including possible legacy data migration) is finished.
            amplitude.isBuilt.await()

            if (initialSessionId == null) {
                _sessionId.set(
                    amplitude.storage.read(Storage.Constants.PREVIOUS_SESSION_ID)?.toLongOrNull()
                        ?: -1
                )
            }
            lastEventId = amplitude.storage.read(Storage.Constants.LAST_EVENT_ID)?.toLongOrNull() ?: 0
            lastEventTime = amplitude.storage.read(Storage.Constants.LAST_EVENT_TIME)?.toLongOrNull() ?: -1

            for (message in eventMessageChannel) {
                processEventMessage(message)
            }
        }
    }

    internal fun stop() {
        this.eventMessageChannel.cancel()
    }

    override fun process(incomingEvent: BaseEvent) {
        if (incomingEvent.timestamp == null) {
            incomingEvent.timestamp = System.currentTimeMillis()
        }

        eventMessageChannel.trySend(EventQueueMessage(incomingEvent))
    }

    private suspend fun processEventMessage(message: EventQueueMessage) {
        val event = message.event
        var sessionEvents: Iterable<BaseEvent>? = null
        val eventTimestamp = event.timestamp!!
        val eventSessionId = event.sessionId
        var skipEvent = false

        if (event.eventType == Amplitude.START_SESSION_EVENT) {
            setSessionId(eventSessionId ?: eventTimestamp)
            refreshSessionTime(eventTimestamp)
        } else if (event.eventType == Amplitude.END_SESSION_EVENT) {
            // do nothing
        } else if (event.eventType == Amplitude.DUMMY_ENTER_FOREGROUND_EVENT) {
            skipEvent = true
            sessionEvents = startNewSessionIfNeeded(eventTimestamp)
            _foreground = true
        } else if (event.eventType == Amplitude.DUMMY_EXIT_FOREGROUND_EVENT) {
            skipEvent = true
            refreshSessionTime(eventTimestamp)
            _foreground = false
        } else {
            if (!_foreground) {
                sessionEvents = startNewSessionIfNeeded(eventTimestamp)
            } else {
                refreshSessionTime(eventTimestamp)
            }
        }

        if (!skipEvent && event.sessionId == null) {
            event.sessionId = sessionId
        }

        val savedLastEventId = lastEventId

        sessionEvents?.let {
            it.forEach { e ->
                e.eventId ?: let {
                    val newEventId = lastEventId + 1
                    e.eventId = newEventId
                    lastEventId = newEventId
                }
            }
        }

        if (!skipEvent) {
            event.eventId ?: let {
                val newEventId = lastEventId + 1
                event.eventId = newEventId
                lastEventId = newEventId
            }
        }

        if (lastEventId > savedLastEventId) {
            amplitude.storage.write(Storage.Constants.LAST_EVENT_ID, lastEventId.toString())
        }

        sessionEvents?.let {
            it.forEach { e ->
                super.process(e)
            }
        }

        if (!skipEvent) {
            super.process(event)
        }
    }

    private suspend fun startNewSessionIfNeeded(timestamp: Long): Iterable<BaseEvent>? {
        if (inSession() && isWithinMinTimeBetweenSessions(timestamp)) {
            refreshSessionTime(timestamp)
            return null
        }
        return startNewSession(timestamp)
    }

    private suspend fun setSessionId(timestamp: Long) {
        _sessionId.set(timestamp)
        amplitude.storage.write(Storage.Constants.PREVIOUS_SESSION_ID, sessionId.toString())
    }

    private suspend fun startNewSession(timestamp: Long): Iterable<BaseEvent> {
        val sessionEvents = mutableListOf<BaseEvent>()
        val configuration = amplitude.configuration as Configuration

        // end previous session
        if (configuration.autocapture.sessions && inSession()) {
            val sessionEndEvent = BaseEvent()
            sessionEndEvent.eventType = Amplitude.END_SESSION_EVENT
            sessionEndEvent.timestamp = if (lastEventTime > 0) lastEventTime else null
            sessionEndEvent.sessionId = sessionId
            sessionEvents.add(sessionEndEvent)
        }

        // start new session
        setSessionId(timestamp)
        refreshSessionTime(timestamp)
        if (configuration.autocapture.sessions) {
            val sessionStartEvent = BaseEvent()
            sessionStartEvent.eventType = Amplitude.START_SESSION_EVENT
            sessionStartEvent.timestamp = timestamp
            sessionStartEvent.sessionId = sessionId
            sessionEvents.add(sessionStartEvent)
        }

        return sessionEvents
    }

    private suspend fun refreshSessionTime(timestamp: Long) {
        if (!inSession()) {
            return
        }
        lastEventTime = timestamp
        amplitude.storage.write(Storage.Constants.LAST_EVENT_TIME, lastEventTime.toString())
    }

    private fun isWithinMinTimeBetweenSessions(timestamp: Long): Boolean {
        val sessionLimit: Long = (amplitude.configuration as Configuration).minTimeBetweenSessionsMillis
        return timestamp - lastEventTime < sessionLimit
    }

    private fun inSession(): Boolean {
        return sessionId >= 0
    }
}

data class EventQueueMessage(
    val event: BaseEvent,
)
