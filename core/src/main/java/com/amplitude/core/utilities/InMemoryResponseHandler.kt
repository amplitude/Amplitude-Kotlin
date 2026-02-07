package com.amplitude.core.utilities

import com.amplitude.core.Configuration
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(RestrictedAmplitudeFeature::class)
internal class InMemoryResponseHandler
    constructor(
        private val eventPipeline: EventPipeline,
        private val configuration: Configuration,
        private val scope: CoroutineScope,
        private val storageDispatcher: CoroutineDispatcher,
        private val diagnosticsClient: DiagnosticsClient?,
    ) : ResponseHandler {
        constructor(
            eventPipeline: EventPipeline,
            configuration: Configuration,
            scope: CoroutineScope,
            storageDispatcher: CoroutineDispatcher,
        ) : this(eventPipeline, configuration, scope, storageDispatcher, null)

        companion object {
            const val BACK_OFF: Long = 30000
        }

        override fun handleSuccessResponse(
            successResponse: SuccessResponse,
            events: Any,
            eventsString: String,
        ) {
            triggerEventsCallback(
                events as List<BaseEvent>,
                HttpStatus.SUCCESS.statusCode,
                "Event sent success.",
            )
        }

        override fun handleBadRequestResponse(
            badRequestResponse: BadRequestResponse,
            events: Any,
            eventsString: String,
        ): Boolean {
            val eventsList = events as List<BaseEvent>
            if (badRequestResponse.isInvalidApiKeyResponse()) {
                triggerEventsCallback(eventsList, HttpStatus.BAD_REQUEST.statusCode, badRequestResponse.error)
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
            triggerEventsCallback(eventsToDrop, HttpStatus.BAD_REQUEST.statusCode, badRequestResponse.error)
            eventsToRetry.forEach {
                eventPipeline.put(it)
            }
            return eventsToDrop.isEmpty()
        }

        override fun handlePayloadTooLargeResponse(
            payloadTooLargeResponse: PayloadTooLargeResponse,
            events: Any,
            eventsString: String,
        ) {
            val eventsList = events as List<BaseEvent>
            if (eventsList.size == 1) {
                triggerEventsCallback(
                    eventsList,
                    HttpStatus.PAYLOAD_TOO_LARGE.statusCode,
                    payloadTooLargeResponse.error,
                )
                return
            }

            scope.launch(storageDispatcher) {
                eventPipeline.flushSizeDivider.incrementAndGet()
                eventsList.forEach {
                    delay(BACK_OFF)
                    eventPipeline.put(it)
                }
            }
        }

        override fun handleTooManyRequestsResponse(
            tooManyRequestsResponse: TooManyRequestsResponse,
            events: Any,
            eventsString: String,
        ) {
            val eventsList = events as List<BaseEvent>
            val eventsToDrop = mutableListOf<BaseEvent>()
            val eventsToRetryNow = mutableListOf<BaseEvent>()
            val eventsToRetryLater = mutableListOf<BaseEvent>()
            eventsList.forEachIndexed { index, event ->
                if (tooManyRequestsResponse.isEventExceedDailyQuota(event)) {
                    eventsToDrop.add(event)
                } else if (tooManyRequestsResponse.throttledEvents.contains(index)) {
                    eventsToRetryLater.add(event)
                } else {
                    eventsToRetryNow.add(event)
                }
            }
            triggerEventsCallback(
                eventsToDrop,
                HttpStatus.TOO_MANY_REQUESTS.statusCode,
                tooManyRequestsResponse.error,
            )
            eventsToRetryNow.forEach {
                eventPipeline.put(it)
            }
            scope.launch(storageDispatcher) {
                delay(BACK_OFF)
                eventsToRetryLater.forEach {
                    eventPipeline.put(it)
                }
            }
        }

        override fun handleTimeoutResponse(
            timeoutResponse: TimeoutResponse,
            events: Any,
            eventsString: String,
        ) {
            val eventsList = events as List<BaseEvent>
            scope.launch(storageDispatcher) {
                delay(BACK_OFF)
                eventsList.forEach {
                    eventPipeline.put(it)
                }
            }
        }

        override fun handleFailedResponse(
            failedResponse: FailedResponse,
            events: Any,
            eventsString: String,
        ) {
            val eventsList = events as List<BaseEvent>
            val eventsToDrop = mutableListOf<BaseEvent>()
            val eventsToRetry = mutableListOf<BaseEvent>()
            eventsList.forEach { event ->
                if (event.attempts >= configuration.flushMaxRetries) {
                    eventsToDrop.add(event)
                } else {
                    eventsToRetry.add(event)
                }
            }
            triggerEventsCallback(eventsToDrop, HttpStatus.FAILED.statusCode, failedResponse.error)
            eventsToRetry.forEach {
                eventPipeline.put(it)
            }
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
                    it(event, status, message)
                }
                event.callback?.let {
                    it(event, status, message)
                }
            }
        }
    }
