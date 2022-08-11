package com.amplitude.core.utilities

import com.amplitude.core.Configuration
import com.amplitude.core.events.BaseEvent
import com.amplitude.core.platform.EventPipeline
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
    private val eventFilePath: String,
    private val eventsString: String
) : ResponseHandler {

    override fun handleSuccessResponse(successResponse: SuccessResponse) {
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
    }

    override fun handleBadRequestResponse(badRequestResponse: BadRequestResponse) {
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
    }

    override fun handlePayloadTooLargeResponse(payloadTooLargeResponse: PayloadTooLargeResponse) {
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
    }

    override fun handleTooManyRequestsResponse(tooManyRequestsResponse: TooManyRequestsResponse) {
        // wait for next time to pick it up
    }

    override fun handleTimeoutResponse(timeoutResponse: TimeoutResponse) {
        // wait for next time to try again
    }

    override fun handleFailedResponse(failedResponse: FailedResponse) {
        // wait for next time to try again
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
}
