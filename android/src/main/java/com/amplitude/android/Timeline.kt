package com.amplitude.android

import com.amplitude.android.utilities.Session
import com.amplitude.android.utilities.SystemTime
import com.amplitude.core.Storage
import com.amplitude.core.events.BaseEvent
import com.amplitude.core.platform.Timeline
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.concurrent.atomic.AtomicLong

class Timeline : Timeline() {
    companion object {
        const val DEFAULT_LAST_EVENT_ID = 0L
    }

    private val eventMessageChannel: Channel<EventQueueMessage> = Channel(Channel.UNLIMITED)
    internal lateinit var session: Session

    private val _lastEventId = AtomicLong(DEFAULT_LAST_EVENT_ID)

    internal var lastEventId: Long = DEFAULT_LAST_EVENT_ID
        get() = _lastEventId.get()

    internal var sessionId: Long = Session.EMPTY_SESSION_ID
        get() = if (session == null) Session.EMPTY_SESSION_ID else session.sessionId

    internal suspend fun start(timestamp: Long? = null) {
        this.session = Session(
            amplitude.configuration as Configuration,
            amplitude.storage,
            amplitude.store
        )

        val sessionEvents = session.startNewSessionIfNeeded(
            timestamp ?: SystemTime.getCurrentTimeMillis(),
            amplitude.configuration.sessionId
        )

        loadLastEventId()

        amplitude.amplitudeScope.launch(amplitude.storageIODispatcher) {
            // Wait until build (including possible legacy data migration) is finished.
            amplitude.isBuilt.await()

            for (message in eventMessageChannel) {
                processEventMessage(message)
            }
        }

        if (!amplitude.configuration.optOut) {
            runBlocking {
                sessionEvents?.forEach {
                    processImmediately(it)
                }
            }
        }
    }

    internal fun stop() {
        this.eventMessageChannel.cancel()
    }

    override fun process(incomingEvent: BaseEvent) {
        if (incomingEvent.timestamp == null) {
            incomingEvent.timestamp = SystemTime.getCurrentTimeMillis()
        }

        eventMessageChannel.trySend(EventQueueMessage(incomingEvent, (amplitude as Amplitude).inForeground))
    }

    private suspend fun processImmediately(incomingEvent: BaseEvent) {
        if (incomingEvent.timestamp == null) {
            incomingEvent.timestamp = SystemTime.getCurrentTimeMillis()
        }

        processEventMessage(EventQueueMessage(incomingEvent, (amplitude as Amplitude).inForeground))
    }

    private suspend fun processEventMessage(message: EventQueueMessage) {
        val event = message.event
        var sessionEvents: Iterable<BaseEvent>? = null
        val eventTimestamp = event.timestamp!!
        var skipEvent = false

        if (event.eventType == Amplitude.DUMMY_ENTER_FOREGROUND_EVENT) {
            skipEvent = true
            sessionEvents = session.startNewSessionIfNeeded(eventTimestamp)
        } else if (event.eventType == Amplitude.DUMMY_EXIT_FOREGROUND_EVENT) {
            skipEvent = true
            session.refreshSessionTime(eventTimestamp)
        } else {
            if (!message.inForeground) {
                sessionEvents = session.startNewSessionIfNeeded(eventTimestamp)
            } else {
                session.refreshSessionTime(eventTimestamp)
            }
        }

        if (!skipEvent && event.sessionId == null) {
            event.sessionId = session.sessionId
        }

        sessionEvents?.let {
            it.forEach { e ->
                e.eventId ?: let {
                    e.eventId = getAndSetNextEventId()
                }
            }
        }

        if (!skipEvent) {
            event.eventId ?: let {
                event.eventId = getAndSetNextEventId()
            }
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

    private fun loadLastEventId() {
        val lastEventId = amplitude.storage.read(Storage.Constants.LAST_EVENT_ID)?.toLongOrNull()
            ?: DEFAULT_LAST_EVENT_ID
        _lastEventId.set(lastEventId)
    }

    private suspend fun writeLastEventId(lastEventId: Long) {
        amplitude.storage.write(Storage.Constants.LAST_EVENT_ID, lastEventId.toString())
    }

    private suspend fun getAndSetNextEventId(): Long {
        val nextEventId = _lastEventId.incrementAndGet()
        writeLastEventId(nextEventId)

        return nextEventId
    }
}

data class EventQueueMessage(
    val event: BaseEvent,
    val inForeground: Boolean
)
