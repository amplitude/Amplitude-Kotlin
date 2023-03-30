package com.amplitude.core.utilities

import com.amplitude.common.Logger
import com.amplitude.core.Configuration
import com.amplitude.core.events.BaseEvent
import com.amplitude.core.platform.EventPipeline
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class FileResponseHandler(
    private val storage: EventsFileStorage,
    private val eventPipeline: EventPipeline,
    private val configuration: Configuration,
    private val scope: CoroutineScope,
    private val dispatcher: CoroutineDispatcher,
    private val logger: Logger?
) : ResponseHandler {

    private var retries = AtomicInteger(0)
    private var currentFlushInterval = configuration.flushIntervalMillis.toLong()
        set(value) {
            field = value
            eventPipeline.flushInterval = value
        }
    private var backoff = AtomicBoolean(false)
    private var currentFlushQueueSize = configuration.flushQueueSize
        set(value) {
            field = value
            eventPipeline.flushQueueSize = value
        }
    private val maxQueueSize = 50

    override fun handleSuccessResponse(successResponse: SuccessResponse, events: Any, eventsString: String) {
        val eventFilePath = events as String
        logger?.debug("Handle response, status: ${successResponse.status}")
        val events: List<BaseEvent>
        try {
            events = JSONArray(eventsString).toEvents()
        } catch (e: JSONException) {
            storage.removeFile(eventFilePath)
            removeCallbackByInsertId(eventsString)
            throw e
        }
        triggerEventsCallback(events, HttpStatus.SUCCESS.code, "Event sent success.")
        scope.launch(dispatcher) {
            storage.removeFile(eventFilePath)
        }
        resetBackOff()
    }

    override fun handleBadRequestResponse(badRequestResponse: BadRequestResponse, eventsRaw: Any, eventsString: String) {
        logger?.debug("Handle response, status: ${badRequestResponse.status}, error: ${badRequestResponse.error}")
        val eventFilePath = eventsRaw as String
        val events: List<BaseEvent>
        try {
            events = JSONArray(eventsString).toEvents()
        } catch (e: JSONException) {
            storage.removeFile(eventFilePath)
            removeCallbackByInsertId(eventsString)
            throw e
        }
        if (events.size == 1) {
            triggerEventsCallback(events, HttpStatus.BAD_REQUEST.code, badRequestResponse.error)
            storage.removeFile(eventFilePath)
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
        scope.launch(dispatcher) {
            storage.removeFile(eventFilePath)
        }
        triggerBackOff(false)
    }

    override fun handlePayloadTooLargeResponse(payloadTooLargeResponse: PayloadTooLargeResponse, eventsRaw: Any, eventsString: String) {
        logger?.debug("Handle response, status: ${payloadTooLargeResponse.status}, error: ${payloadTooLargeResponse.error}")
        val eventFilePath = eventsRaw as String
        val rawEvents: JSONArray
        try {
            rawEvents = JSONArray(eventsString)
        } catch (e: JSONException) {
            storage.removeFile(eventFilePath)
            removeCallbackByInsertId(eventsString)
            throw e
        }
        if (rawEvents.length() == 1) {
            val events = rawEvents.toEvents()
            triggerEventsCallback(events, HttpStatus.PAYLOAD_TOO_LARGE.code, payloadTooLargeResponse.error)
            scope.launch(dispatcher) {
                storage.removeFile(eventFilePath)
            }
            return
        }
        // split file into two
        scope.launch(dispatcher) {
            storage.splitEventFile(eventFilePath, rawEvents)
        }
        triggerBackOff(false)
    }

    override fun handleTooManyRequestsResponse(tooManyRequestsResponse: TooManyRequestsResponse, events: Any, eventsString: String) {
        logger?.debug("Handle response, status: ${tooManyRequestsResponse.status}, error: ${tooManyRequestsResponse.error}")
        // trigger exponential backoff
        storage.releaseFile(events as String)
        triggerBackOff(true)
    }

    override fun handleTimeoutResponse(timeoutResponse: TimeoutResponse, events: Any, eventsString: String) {
        logger?.debug("Handle response, status: ${timeoutResponse.status}")
        // trigger exponential backoff
        storage.releaseFile(events as String)
        triggerBackOff(true)
    }

    override fun handleFailedResponse(failedResponse: FailedResponse, events: Any, eventsString: String) {
        logger?.debug("Handle response, status: ${failedResponse.status}, error: ${failedResponse.error}")
        // wait for next time to try again
        // trigger exponential backoff
        storage.releaseFile(events as String)
        triggerBackOff(true)
    }

    private fun triggerEventsCallback(events: List<BaseEvent>, status: Int, message: String) {
        events.forEach { event ->
            configuration.callback?.let {
                it(event, status, message)
            }
            event.insertId?.let { insertId ->
                storage.getEventCallback(insertId)?.let {
                    it(event, status, message)
                    storage.removeEventCallback(insertId)
                }
            }
        }
    }

    private fun removeCallbackByInsertId(eventsString: String) {
        val regx = """"insert_id":"(.{36})",""".toRegex()
        regx.findAll(eventsString).forEach {
            storage.removeEventCallback(it.groupValues[1])
        }
    }

    private fun triggerBackOff(withSizeUpdate: Boolean = false) {
        logger?.debug("back off to retry sending events later.")
        backoff.set(true)
        if (retries.incrementAndGet() <= configuration.flushMaxRetries) {
            currentFlushInterval *= 2
            if (withSizeUpdate) {
                currentFlushQueueSize = (currentFlushQueueSize * 2).coerceAtMost(maxQueueSize)
            }
        } else {
            // stop scheduling new calls since max retries exceeded
            eventPipeline.exceededRetries = true
            logger?.debug("Max retries ${configuration.flushMaxRetries} exceeded, temporarily stop scheduling new events sending out.")
        }
    }

    private fun resetBackOff() {
        if (backoff.get()) {
            backoff.set(false)
            retries.getAndSet(0)
            currentFlushInterval = configuration.flushIntervalMillis.toLong()
            currentFlushQueueSize = configuration.flushQueueSize
            eventPipeline.exceededRetries = false
        }
    }
}
