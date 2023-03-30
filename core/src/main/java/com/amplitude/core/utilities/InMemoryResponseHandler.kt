package com.amplitude.core.utilities

import com.amplitude.core.Configuration
import com.amplitude.core.events.BaseEvent
import com.amplitude.core.platform.EventPipeline
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal class InMemoryResponseHandler(
    val eventPipeline: EventPipeline,
    val configuration: Configuration,
    val scope: CoroutineScope,
    val dispatcher: CoroutineDispatcher
) : ResponseHandler {

    companion object {
        const val BACK_OFF: Long = 30000
    }

    override fun handleSuccessResponse(successResponse: SuccessResponse, events: Any, eventsString: String) {
        triggerEventsCallback(events as List<BaseEvent>, HttpStatus.SUCCESS.code, "Event sent success.")
    }

    override fun handleBadRequestResponse(badRequestResponse: BadRequestResponse, eventsRaw: Any, eventsString: String) {
        val events = eventsRaw as List<BaseEvent>
        if (events.size == 1) {
            triggerEventsCallback(events, HttpStatus.BAD_REQUEST.code, badRequestResponse.error)
            return
        }
        val droppedIndices = badRequestResponse.getEventIndicesToDrop()
        val eventsToDrop = mutableListOf<BaseEvent>()
        val eventsToRetry = mutableListOf<BaseEvent>()
        events.forEachIndexed { index, event ->
            if (droppedIndices.contains(index) || badRequestResponse.isEventSilenced(event)) {
                eventsToDrop.add(event)
            } else {
                eventsToRetry.add(event)
            }
        }
        triggerEventsCallback(eventsToDrop, HttpStatus.BAD_REQUEST.code, badRequestResponse.error)
        eventsToRetry.forEach {
            eventPipeline.put(it)
        }
    }

    override fun handlePayloadTooLargeResponse(payloadTooLargeResponse: PayloadTooLargeResponse, eventsRaw: Any, eventsString: String) {
        val events = eventsRaw as List<BaseEvent>
        if (events.size == 1) {
            triggerEventsCallback(events, HttpStatus.PAYLOAD_TOO_LARGE.code, payloadTooLargeResponse.error)
            return
        }
        eventPipeline.flushSizeDivider.incrementAndGet()
        events.forEach {
            eventPipeline.put(it)
        }
    }

    override fun handleTooManyRequestsResponse(tooManyRequestsResponse: TooManyRequestsResponse, eventsRaw: Any, eventsString: String) {
        val events = eventsRaw as List<BaseEvent>
        val eventsToDrop = mutableListOf<BaseEvent>()
        val eventsToRetryNow = mutableListOf<BaseEvent>()
        val eventsToRetryLater = mutableListOf<BaseEvent>()
        events.forEachIndexed { index, event ->
            if (tooManyRequestsResponse.isEventExceedDailyQuota(event)) {
                eventsToDrop.add(event)
            } else if (tooManyRequestsResponse.throttledEvents.contains(index)) {
                eventsToRetryLater.add(event)
            } else {
                eventsToRetryNow.add(event)
            }
        }
        triggerEventsCallback(eventsToDrop, HttpStatus.TOO_MANY_REQUESTS.code, tooManyRequestsResponse.error)
        eventsToRetryNow.forEach {
            eventPipeline.put(it)
        }
        scope.launch(dispatcher) {
            delay(BACK_OFF)
            eventsToRetryLater.forEach {
                eventPipeline.put(it)
            }
        }
    }

    override fun handleTimeoutResponse(timeoutResponse: TimeoutResponse, eventsRaw: Any, eventsString: String) {
        val events = eventsRaw as List<BaseEvent>
        scope.launch(dispatcher) {
            delay(BACK_OFF)
            events.forEach {
                eventPipeline.put(it)
            }
        }
    }

    override fun handleFailedResponse(failedResponse: FailedResponse, eventsRaw: Any, eventsString: String) {
        val events = eventsRaw as List<BaseEvent>
        val eventsToDrop = mutableListOf<BaseEvent>()
        val eventsToRetry = mutableListOf<BaseEvent>()
        events.forEach { event ->
            if (event.attempts >= configuration.flushMaxRetries) {
                eventsToDrop.add(event)
            } else {
                eventsToRetry.add(event)
            }
        }
        triggerEventsCallback(eventsToDrop, HttpStatus.FAILED.code, failedResponse.error)
        eventsToRetry.forEach {
            eventPipeline.put(it)
        }
    }

    private fun triggerEventsCallback(events: List<BaseEvent>, status: Int, message: String) {
        events.forEach { event ->
            configuration.callback?.let {
                it(event, status, message)
            }
            event.callback?.let {
                it(event, status, message)
            }
        }
    }
}
