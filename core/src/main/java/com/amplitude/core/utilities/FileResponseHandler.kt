package com.amplitude.core.utilities

import com.amplitude.common.Logger
import com.amplitude.core.Configuration
import com.amplitude.core.events.BaseEvent
import com.amplitude.core.platform.EventPipeline
import com.amplitude.core.utilities.http.BadRequestResponse
import com.amplitude.core.utilities.http.FailedResponse
import com.amplitude.core.utilities.http.HttpStatus
import com.amplitude.core.utilities.http.PayloadTooLargeResponse
import com.amplitude.core.utilities.http.ResponseHandler
import com.amplitude.core.utilities.http.SuccessResponse
import com.amplitude.core.utilities.http.TimeoutResponse
import com.amplitude.core.utilities.http.TooManyRequestsResponse
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONException

class FileResponseHandler(
    private val storage: EventsFileStorage,
    private val eventPipeline: EventPipeline,
    private val configuration: Configuration,
    private val scope: CoroutineScope,
    private val storageDispatcher: CoroutineDispatcher,
    private val logger: Logger?,
) : ResponseHandler {

    override fun handleSuccessResponse(
        successResponse: SuccessResponse,
        events: Any,
        eventsString: String,
    ) {
        val eventFilePath = events as String
        logger?.debug("Handle response, status: ${successResponse.status}")
        val eventsList = parseEvents(eventsString, eventFilePath).toEvents()
        triggerEventsCallback(eventsList, HttpStatus.SUCCESS.range.first, "Event sent success.")
        scope.launch(storageDispatcher) {
            storage.removeFile(eventFilePath)
        }
    }

    override fun handleBadRequestResponse(
        badRequestResponse: BadRequestResponse,
        events: Any,
        eventsString: String,
    ): Boolean {
        logger?.debug(
            "Handle response, status: ${badRequestResponse.status}, error: ${badRequestResponse.error}"
        )
        val eventFilePath = events as String
        val eventsList = parseEvents(eventsString, eventFilePath).toEvents()
        if (badRequestResponse.isInvalidApiKeyResponse()) {
            triggerEventsCallback(eventsList, HttpStatus.BAD_REQUEST.range.first, badRequestResponse.error)
            scope.launch(storageDispatcher) {
                storage.removeFile(eventFilePath)
            }
            return false
        }
        val droppedIndices = badRequestResponse.getEventIndicesToDrop()
        val eventsToDrop = mutableListOf<BaseEvent>()
        val eventsToRetry = mutableListOf<BaseEvent>()
        eventsList.forEachIndexed { index, event ->
            if (droppedIndices.contains(index) || badRequestResponse.isEventSilenced(event)) {
                eventsToDrop.add(event)
            } else {
                eventsToRetry.add(event)
            }
        }
        // shouldRetryUploadOnFailure is true if there are NO events to drop, this happens
        // when connected to a proxy and it returns 400 with w/o the eventsToDrop fields
        if (eventsToDrop.isEmpty()) {
            scope.launch(storageDispatcher) {
                storage.releaseFile(events)
            }
            return true
        }

        triggerEventsCallback(eventsToDrop, HttpStatus.BAD_REQUEST.range.first, badRequestResponse.error)
        eventsToRetry.forEach {
            eventPipeline.put(it)
        }
        scope.launch(storageDispatcher) {
            logger?.debug(
                "--> remove file: ${eventFilePath.split("-").takeLast(2)}, dropped events: ${eventsToDrop.size}, " +
                    "retry events: ${eventsToRetry.size}"
            )
            storage.removeFile(eventFilePath)
        }
        return false
    }

    override fun handlePayloadTooLargeResponse(
        payloadTooLargeResponse: PayloadTooLargeResponse,
        events: Any,
        eventsString: String,
    ) {
        logger?.debug(
            "Handle response, status: ${payloadTooLargeResponse.status}, error: ${payloadTooLargeResponse.error}"
        )
        val eventFilePath = events as String
        val rawEvents = parseEvents(eventsString, eventFilePath)
        if (rawEvents.length() == 1) {
            val eventsList = rawEvents.toEvents()
            triggerEventsCallback(
                eventsList, HttpStatus.PAYLOAD_TOO_LARGE.range.first, payloadTooLargeResponse.error
            )
            scope.launch(storageDispatcher) {
                storage.removeFile(eventFilePath)
            }
            return
        }
        // split file into two
        scope.launch(storageDispatcher) {
            storage.splitEventFile(eventFilePath, rawEvents)
        }
    }

    override fun handleTooManyRequestsResponse(
        tooManyRequestsResponse: TooManyRequestsResponse,
        events: Any,
        eventsString: String,
    ) {
        logger?.debug(
            "Handle response, status: ${tooManyRequestsResponse.status}, error: ${tooManyRequestsResponse.error}"
        )
        scope.launch(storageDispatcher) {
            storage.releaseFile(events as String)
        }
    }

    override fun handleTimeoutResponse(
        timeoutResponse: TimeoutResponse,
        events: Any,
        eventsString: String,
    ) {
        logger?.debug("Handle response, status: ${timeoutResponse.status}")
        scope.launch(storageDispatcher) {
            storage.releaseFile(events as String)
        }
    }

    override fun handleFailedResponse(
        failedResponse: FailedResponse,
        events: Any,
        eventsString: String,
    ) {
        logger?.debug(
            "Handle response, status: ${failedResponse.status}, error: ${failedResponse.error}"
        )
        // wait for next time to try again
        scope.launch(storageDispatcher) {
            storage.releaseFile(events as String)
        }
    }

    /**
     * Parse events from the [eventsString] at the given [eventFilePath].
     * If parsing fails, this removes the file at [eventFilePath], and
     * remove the callback by insert ID, and throws a [JSONException].
     */
    private fun parseEvents(
        eventsString: String,
        eventFilePath: String,
    ): JSONArray {
        val rawEvents: JSONArray
        try {
            rawEvents = JSONArray(eventsString)
        } catch (e: JSONException) {
            scope.launch(storageDispatcher) {
                storage.removeFile(eventFilePath)
            }
            removeCallbackByInsertId(eventsString)
            throw e
        }
        return rawEvents
    }

    private fun triggerEventsCallback(
        events: List<BaseEvent>,
        status: Int,
        message: String,
    ) {
        events.forEach { event ->
            configuration.callback?.let {
                it(event, status, message)
            }
            event.insertId?.let { insertId ->
                scope.launch(storageDispatcher) {
                    storage.getEventCallback(insertId)?.let {
                        it(event, status, message)
                        storage.removeEventCallback(insertId)
                    }
                }
            }
        }
    }

    private fun removeCallbackByInsertId(eventsString: String) {
        val regex = """"insert_id":"(.{36})",""".toRegex()
        regex.findAll(eventsString).forEach {
            scope.launch(storageDispatcher) {
                storage.removeEventCallback(it.groupValues[1])
            }
        }
    }
}
