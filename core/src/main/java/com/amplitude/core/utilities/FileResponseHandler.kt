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
    private val dispatcher: CoroutineDispatcher,
    private val logger: Logger?,
) : ResponseHandler {

    override fun handleSuccessResponse(
        successResponse: SuccessResponse,
        events: Any,
        eventsString: String,
    ) {
        val eventFilePath = events as String
        logger?.debug("Handle response, status: ${successResponse.status}")
        val eventsList: List<BaseEvent>
        try {
            eventsList = JSONArray(eventsString).toEvents()
        } catch (e: JSONException) {
            storage.removeFile(eventFilePath)
            removeCallbackByInsertId(eventsString)
            throw e
        }
        triggerEventsCallback(eventsList, HttpStatus.SUCCESS.code, "Event sent success.")
        scope.launch(dispatcher) {
            storage.removeFile(eventFilePath)
        }
    }

    override fun handleBadRequestResponse(
        badRequestResponse: BadRequestResponse,
        events: Any,
        eventsString: String,
    ) {
        logger?.debug(
            "Handle response, status: ${badRequestResponse.status}, error: ${badRequestResponse.error}"
        )
        val eventFilePath = events as String
        val eventsList: List<BaseEvent>
        try {
            eventsList = JSONArray(eventsString).toEvents()
        } catch (e: JSONException) {
            storage.removeFile(eventFilePath)
            removeCallbackByInsertId(eventsString)
            throw e
        }
        if (eventsList.size == 1 || badRequestResponse.isInvalidApiKeyResponse()) {
            triggerEventsCallback(eventsList, HttpStatus.BAD_REQUEST.code, badRequestResponse.error)
            storage.removeFile(eventFilePath)
            return
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
        triggerEventsCallback(eventsToDrop, HttpStatus.BAD_REQUEST.code, badRequestResponse.error)
        eventsToRetry.forEach {
            eventPipeline.put(it)
        }
        scope.launch(dispatcher) {
            storage.removeFile(eventFilePath)
        }
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
        val rawEvents: JSONArray
        try {
            rawEvents = JSONArray(eventsString)
        } catch (e: JSONException) {
            storage.removeFile(eventFilePath)
            removeCallbackByInsertId(eventsString)
            throw e
        }
        if (rawEvents.length() == 1) {
            val eventsList = rawEvents.toEvents()
            triggerEventsCallback(
                eventsList, HttpStatus.PAYLOAD_TOO_LARGE.code, payloadTooLargeResponse.error
            )
            scope.launch(dispatcher) {
                storage.removeFile(eventFilePath)
            }
            return
        }
        // split file into two
        scope.launch(dispatcher) {
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
        storage.releaseFile(events as String)
    }

    override fun handleTimeoutResponse(
        timeoutResponse: TimeoutResponse,
        events: Any,
        eventsString: String,
    ) {
        logger?.debug("Handle response, status: ${timeoutResponse.status}")
        storage.releaseFile(events as String)
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
        storage.releaseFile(events as String)
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
                storage.getEventCallback(insertId)?.let {
                    it(event, status, message)
                    storage.removeEventCallback(insertId)
                }
            }
        }
    }

    private fun removeCallbackByInsertId(eventsString: String) {
        val regex = """"insert_id":"(.{36})",""".toRegex()
        regex.findAll(eventsString).forEach {
            storage.removeEventCallback(it.groupValues[1])
        }
    }
}
