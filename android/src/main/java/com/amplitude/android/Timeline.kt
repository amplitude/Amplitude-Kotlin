package com.amplitude.android

import com.amplitude.android.Amplitude.Companion.END_SESSION_EVENT
import com.amplitude.android.Amplitude.Companion.START_SESSION_EVENT
import com.amplitude.core.Storage
import com.amplitude.core.Storage.Constants
import com.amplitude.core.Storage.Constants.LAST_EVENT_ID
import com.amplitude.core.Storage.Constants.LAST_EVENT_TIME
import com.amplitude.core.Storage.Constants.PREVIOUS_SESSION_ID
import com.amplitude.core.events.BaseEvent
import com.amplitude.core.platform.DEFAULT_SESSION_ID
import com.amplitude.core.platform.Timeline
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

private const val DEFAULT = 0L

class Timeline(
    amplitude: Amplitude,
) : Timeline(amplitude) {
    private val eventMessageChannel: Channel<EventQueueMessage> = Channel(Channel.UNLIMITED)

    private val initialSessionId = amplitude.configuration.sessionId
    private val _sessionId = AtomicLong(initialSessionId ?: DEFAULT_SESSION_ID)
    private val foreground = AtomicBoolean(false)

    override var sessionId: Long
        get() {
            return _sessionId.get()
        }
        set(value) {
            with(amplitude) {
                amplitudeScope.launch(storageIODispatcher) {
                    isBuilt.await()
                    _sessionId.set(value)
                    amplitude.storage.write(PREVIOUS_SESSION_ID, value.toString())
                }
            }
        }

    internal var lastEventId: Long = DEFAULT
        private set
    internal var lastEventTime: Long = DEFAULT
        private set

    override fun start() {
        with(amplitude) {
            amplitudeScope.launch(storageIODispatcher) {
                // Wait until build (including possible legacy data migration) is finished.
                isBuilt.await()

                if (initialSessionId == null) {
                    _sessionId.set(storage.readLong(PREVIOUS_SESSION_ID, DEFAULT_SESSION_ID))
                }
                lastEventId = storage.readLong(LAST_EVENT_ID, DEFAULT)
                lastEventTime = storage.readLong(LAST_EVENT_TIME, DEFAULT)

                for (message in eventMessageChannel) {
                    processEventMessage(message)
                }
            }
        }
    }

    override fun stop() {
        this.eventMessageChannel.cancel()
    }

    override fun process(incomingEvent: BaseEvent) {
        if (incomingEvent.timestamp == null) {
            incomingEvent.timestamp = System.currentTimeMillis()
        }

        eventMessageChannel.trySend(EventQueueMessage(incomingEvent))
    }

    internal fun onEnterForeground(timestamp: Long) {
        amplitude.amplitudeScope.launch(amplitude.storageIODispatcher) {
            val localSessionEvents = startNewSessionIfNeeded(timestamp)
            foreground.set(true)

            // Process any local session events
            processAndPersistEvents(localSessionEvents)
        }
    }

    internal fun onExitForeground(timestamp: Long) {
        amplitude.amplitudeScope.launch(amplitude.storageIODispatcher) {
            refreshSessionTime(timestamp)
            foreground.set(false)
        }
    }

    private suspend fun processEventMessage(message: EventQueueMessage) {
        val event = message.event
        val eventTimestamp = event.timestamp!! // Guaranteed non-null by process()
        val eventSessionId = event.sessionId

        when (event.eventType) {
            START_SESSION_EVENT -> {
                setSessionId(eventSessionId ?: eventTimestamp)
                refreshSessionTime(eventTimestamp)
            }

            END_SESSION_EVENT -> {
                // No specific action needed before processing this event type
            }

            else -> {
                if (!foreground.get()) {
                    val localSessionEvents = startNewSessionIfNeeded(eventTimestamp)
                    processAndPersistEvents(localSessionEvents)
                } else {
                    refreshSessionTime(eventTimestamp)
                }
            }
        }

        // Assign sessionId to the current event if it doesn't have one
        event.sessionId = event.sessionId ?: this.sessionId

        // Process the incoming event
        processAndPersistEvents(listOf(event))
    }

    private suspend fun processAndPersistEvents(events: List<BaseEvent>) {
        if (events.isEmpty()) return

        val initialLastEventId = lastEventId
        for (event in events) {
            event.eventId = event.eventId ?: ++lastEventId
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
        amplitude.storage.write(PREVIOUS_SESSION_ID, timestamp.toString())
    }

    private suspend fun startNewSession(timestamp: Long): List<BaseEvent> {
        val sessionEvents = mutableListOf<BaseEvent>()
        val configuration = amplitude.configuration as Configuration
        val trackingSessionEvents = AutocaptureOption.SESSIONS in configuration.autocapture

        // end previous session
        if (trackingSessionEvents && inSession()) {
            val sessionEndEvent = BaseEvent()
            sessionEndEvent.eventType = END_SESSION_EVENT
            sessionEndEvent.timestamp = lastEventTime.takeIf { lastEventTime > DEFAULT }
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
        return sessionId > DEFAULT_SESSION_ID
    }

    private fun Storage.readLong(key: Constants, default: Long): Long {
        return read(key)?.toLongOrNull() ?: default
    }
}

data class EventQueueMessage(
    val event: BaseEvent,
)
