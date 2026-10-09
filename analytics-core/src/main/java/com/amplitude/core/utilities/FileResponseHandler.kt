package com.amplitude.core.utilities

import com.amplitude.common.Logger
import com.amplitude.core.Configuration
import com.amplitude.core.EventCallBack
import com.amplitude.core.RestrictedAmplitudeFeature
import com.amplitude.core.diagnostics.DiagnosticsClient
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

@OptIn(RestrictedAmplitudeFeature::class)
public class FileResponseHandler
    constructor(
        private val storage: EventsFileStorage,
        private val eventPipeline: EventPipeline,
        private val configuration: Configuration,
        private val scope: CoroutineScope,
        private val storageDispatcher: CoroutineDispatcher,
        private val logger: Logger?,
        private val diagnosticsClient: DiagnosticsClient?,
    ) : ResponseHandler {
        public constructor(
            storage: EventsFileStorage,
            eventPipeline: EventPipeline,
            configuration: Configuration,
            scope: CoroutineScope,
            storageDispatcher: CoroutineDispatcher,
            logger: Logger?,
        ) : this(storage, eventPipeline, configuration, scope, storageDispatcher, logger, null)

        override fun handleSuccessResponse(
            successResponse: SuccessResponse,
            events: Any,
            eventsString: String,
        ) {
            val eventFilePath = events as String
            logger?.debug("Handle response, status: ${successResponse.status}")
            // Remove acknowledged events before callback parsing or another upload can run.
            if (!storage.removeFile(eventFilePath)) {
                logger?.warn("Failed to remove uploaded event file: $eventFilePath")
            }
            val eventsList = parseEvents(eventsString, eventFilePath).toEvents()
            triggerEventsCallback(eventsList, HttpStatus.SUCCESS.statusCode, "Event sent success.")
        }

        override fun handleBadRequestResponse(
            badRequestResponse: BadRequestResponse,
            events: Any,
            eventsString: String,
        ): Boolean {
            logger?.debug(
                "Handle response, status: ${badRequestResponse.status}, error: ${badRequestResponse.error}",
            )
            val eventFilePath = events as String
            val eventsList = parseEvents(eventsString, eventFilePath).toEvents()
            if (badRequestResponse.isInvalidApiKeyResponse()) {
                triggerEventsCallback(eventsList, HttpStatus.BAD_REQUEST.statusCode, badRequestResponse.error)
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

            triggerEventsCallback(eventsToDrop, HttpStatus.BAD_REQUEST.statusCode, badRequestResponse.error)
            eventsToRetry.forEach {
                eventPipeline.put(it)
            }
            scope.launch(storageDispatcher) {
                logger?.debug(
                    "--> remove file: ${eventFilePath.split("-").takeLast(2)}, dropped events: ${eventsToDrop.size}, " +
                        "retry events: ${eventsToRetry.size}",
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
                "Handle response, status: ${payloadTooLargeResponse.status}, error: ${payloadTooLargeResponse.error}",
            )
            val eventFilePath = events as String
            val rawEvents = parseEvents(eventsString, eventFilePath)
            if (rawEvents.length() == 1) {
                val eventsList = rawEvents.toEvents()
                triggerEventsCallback(
                    eventsList,
                    HttpStatus.PAYLOAD_TOO_LARGE.statusCode,
                    payloadTooLargeResponse.error,
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
                "Handle response, status: ${tooManyRequestsResponse.status}, error: ${tooManyRequestsResponse.error}",
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
                "Handle response, status: ${failedResponse.status}, error: ${failedResponse.error}",
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
            if (events.isNotEmpty()) {
                diagnosticsClient?.recordEventOutcome(events, status, message)
            }
            events.forEach { event ->
                configuration.callback?.let {
                    invokeCallback(it, event, status, message)
                }
                event.insertId?.let { insertId ->
                    scope.launch(storageDispatcher) {
                        storage.getEventCallback(insertId)?.let {
                            try {
                                invokeCallback(it, event, status, message)
                            } finally {
                                storage.removeEventCallback(insertId)
                            }
                        }
                    }
                }
            }
        }

        private fun invokeCallback(
            callback: EventCallBack,
            event: BaseEvent,
            status: Int,
            message: String,
        ) {
            try {
                callback(event, status, message)
            } catch (e: Exception) {
                logger?.let { e.logWithStackTrace(it, "Event callback failed") }
            }
        }

        private fun removeCallbackByInsertId(eventsString: String) {
            // Recover complete 36-character insert IDs from malformed JSON for callback cleanup.
            // Allow whitespace around ':' and no trailing comma: the ID may be the last property
            // or the payload may end after its value. Exclude quotes and backslashes from the ID.
            val regex = """"insert_id"\s*:\s*"([^"\\]{36})"""".toRegex()
            regex.findAll(eventsString).forEach {
                scope.launch(storageDispatcher) {
                    storage.removeEventCallback(it.groupValues[1])
                }
            }
        }
    }
