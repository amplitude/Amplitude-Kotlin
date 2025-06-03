package com.amplitude.android

import com.amplitude.android.Amplitude.Companion.DUMMY_ENTER_FOREGROUND_EVENT
import com.amplitude.android.Amplitude.Companion.DUMMY_EXIT_FOREGROUND_EVENT
import com.amplitude.android.Amplitude.Companion.END_SESSION_EVENT
import com.amplitude.android.Amplitude.Companion.START_SESSION_EVENT
import com.amplitude.core.Storage
import com.amplitude.core.Storage.Constants
import com.amplitude.core.Storage.Constants.LAST_EVENT_ID
import com.amplitude.core.Storage.Constants.LAST_EVENT_TIME
import com.amplitude.core.Storage.Constants.PREVIOUS_SESSION_ID
import com.amplitude.core.events.BaseEvent
import com.amplitude.core.platform.Timeline
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicBoolean

class Timeline(
    private val initialSessionId: Long? = null,
) : Timeline() {
    private val eventMessageChannel: Channel<EventQueueMessage> = Channel(Channel.UNLIMITED)

    private val _sessionId = AtomicLong(initialSessionId ?: -1L)
    private val foreground = AtomicBoolean(false)
    val sessionId: Long
        get() {
            return _sessionId.get()
        }

    internal var lastEventId: Long = 0
    var lastEventTime: Long = -1L

    internal fun start() {
        with(amplitude) {
            amplitudeScope.launch(storageIODispatcher) {
                // Wait until build (including possible legacy data migration) is finished.
                isBuilt.await()

                if (initialSessionId == null) {
                    _sessionId.set(storage.readLong(PREVIOUS_SESSION_ID, -1))
                }
                lastEventId = storage.readLong(LAST_EVENT_ID, 0)
                lastEventTime = storage.readLong(LAST_EVENT_TIME, -1)

                for (message in eventMessageChannel) {
                    processEventMessage(message)
                }
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
        val eventTimestamp = event.timestamp!! // Guaranteed non-null by process()
        val eventSessionId = event.sessionId

        var localSessionEvents: List<BaseEvent> = emptyList()

        when (event.eventType) {
            START_SESSION_EVENT -> {
                setSessionId(eventSessionId ?: eventTimestamp)
                refreshSessionTime(eventTimestamp)
            }

            END_SESSION_EVENT -> {
                // No specific action needed before processing this event type
            }

            DUMMY_ENTER_FOREGROUND_EVENT -> {
                localSessionEvents = startNewSessionIfNeeded(eventTimestamp)
                foreground.set(true)
            }

            DUMMY_EXIT_FOREGROUND_EVENT -> {
                refreshSessionTime(eventTimestamp)
                foreground.set(false)
            }

            else -> {
                // Regular event
                if (!foreground.get()) {
                    localSessionEvents = startNewSessionIfNeeded(eventTimestamp)
                } else {
                    refreshSessionTime(eventTimestamp)
                }
            }
        }

        val initialLastEventId = lastEventId

        // Process any local session events first
        for (sessionEvent in localSessionEvents) {
            sessionEvent.eventId = sessionEvent.eventId ?: ++lastEventId
            super.process(sessionEvent)
        }

        // Process the incoming event
        val dummyEvent = event.eventType == DUMMY_ENTER_FOREGROUND_EVENT ||
            event.eventType == DUMMY_EXIT_FOREGROUND_EVENT
        if (!dummyEvent) {
            event.eventId = event.eventId ?: ++lastEventId
            // Assign sessionId to the current event if it's not a dummy event and doesn't have one
            if (event.sessionId == null) {
                event.sessionId = this.sessionId // Use this.sessionId for clarity
            }
            super.process(event)
        }

        // Persist lastEventId if it changed
        if (lastEventId > initialLastEventId) {
            amplitude.storage.write(LAST_EVENT_ID, lastEventId.toString())
        }
    }

    private suspend fun startNewSessionIfNeeded(timestamp: Long): List<BaseEvent> {
        if (inSession() && isWithinMinTimeBetweenSessions(timestamp)) {
            refreshSessionTime(timestamp)
            return emptyList()
        }
        return startNewSession(timestamp)
    }

    private suspend fun setSessionId(timestamp: Long) {
        _sessionId.set(timestamp)
        amplitude.storage.write(PREVIOUS_SESSION_ID, sessionId.toString())
    }

    private suspend fun startNewSession(timestamp: Long): List<BaseEvent> {
        val sessionEvents = mutableListOf<BaseEvent>()
        val configuration = amplitude.configuration as Configuration
        val trackingSessionEvents = AutocaptureOption.SESSIONS in configuration.autocapture

        // end previous session
        if (trackingSessionEvents && inSession()) {
            val sessionEndEvent = BaseEvent()
            sessionEndEvent.eventType = END_SESSION_EVENT
            sessionEndEvent.timestamp = if (lastEventTime > 0) lastEventTime else null
            sessionEndEvent.sessionId = sessionId
            sessionEvents.add(sessionEndEvent)
        }

        // start new session
        setSessionId(timestamp)
        refreshSessionTime(timestamp)
        if (trackingSessionEvents) {
            val sessionStartEvent = BaseEvent()
            sessionStartEvent.eventType = START_SESSION_EVENT
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
        amplitude.storage.write(LAST_EVENT_TIME, lastEventTime.toString())
    }

    private fun isWithinMinTimeBetweenSessions(timestamp: Long): Boolean {
        val sessionLimit: Long =
            (amplitude.configuration as Configuration).minTimeBetweenSessionsMillis
        return timestamp - lastEventTime < sessionLimit
    }

    private fun inSession(): Boolean {
        return sessionId >= 0
    }

    private fun Storage.readLong(key: Constants, default: Long): Long {
        return read(key)?.toLongOrNull() ?: default
    }
}

data class EventQueueMessage(
    val event: BaseEvent,
)
