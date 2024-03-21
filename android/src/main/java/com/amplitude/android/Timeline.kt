package com.amplitude.android

import com.amplitude.android.utilities.Session
import com.amplitude.android.utilities.SystemTime
import com.amplitude.core.events.BaseEvent
import com.amplitude.core.platform.Timeline
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class Timeline : Timeline() {
    private val eventMessageChannel: Channel<EventQueueMessage> = Channel(Channel.UNLIMITED)
    internal lateinit var session: Session

    internal var sessionId: Long = Session.EMPTY_SESSION_ID
        get() = if (session == null) Session.EMPTY_SESSION_ID else session.sessionId

    internal suspend fun start() {
        this.session = Session(
            amplitude.configuration as Configuration,
            amplitude.storage,
            amplitude.store
        )

        val sessionEvents = session.startNewSessionIfNeeded(
            SystemTime.getCurrentTimeMillis(),
            amplitude.configuration.sessionId
        )

        amplitude.amplitudeScope.launch(amplitude.storageIODispatcher) {
            // Wait until build (including possible legacy data migration) is finished.
            amplitude.isBuilt.await()

            for (message in eventMessageChannel) {
                processEventMessage(message)
            }
        }

        runBlocking {
            sessionEvents?.forEach {
                processImmediately(it)
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
        val eventSessionId = event.sessionId
        var skipEvent = false

        if (event.eventType == Amplitude.START_SESSION_EVENT) {
            session.setSessionId(eventSessionId ?: eventTimestamp)
            session.refreshSessionTime(eventTimestamp)
        } else if (event.eventType == Amplitude.END_SESSION_EVENT) {
            // do nothing
        } else if (event.eventType == Amplitude.DUMMY_ENTER_FOREGROUND_EVENT) {
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
                    e.eventId = session.getAndSetNextEventId()
                }
            }
        }

        if (!skipEvent) {
            event.eventId ?: let {
                event.eventId = session.getAndSetNextEventId()
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
}

data class EventQueueMessage(
    val event: BaseEvent,
    val inForeground: Boolean
)
