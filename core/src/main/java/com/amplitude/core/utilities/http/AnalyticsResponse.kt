package com.amplitude.core.utilities.http

import com.amplitude.core.events.BaseEvent
import com.amplitude.core.utilities.collectIndices
import com.amplitude.core.utilities.getStringWithDefault
import com.amplitude.core.utilities.toIntArray
import org.json.JSONObject
import java.lang.Exception

internal object HttpResponse {
    fun createHttpResponse(
        code: Int,
        responseBody: String?,
    ): AnalyticsResponse {
        return when (code) {
            HttpStatus.SUCCESS.code -> SuccessResponse()

            HttpStatus.BAD_REQUEST.code -> BadRequestResponse(JSONObject(responseBody))

            HttpStatus.PAYLOAD_TOO_LARGE.code -> PayloadTooLargeResponse(JSONObject(responseBody))

            HttpStatus.TOO_MANY_REQUESTS.code -> TooManyRequestsResponse(JSONObject(responseBody))

            HttpStatus.TIMEOUT.code -> TimeoutResponse()

            else -> FailedResponse(parseResponseBodyOrGetDefault(responseBody))
        }
    }

    private fun parseResponseBodyOrGetDefault(responseBody: String?): JSONObject {
        val defaultObject = JSONObject()
        if (responseBody.isNullOrEmpty()) {
            return defaultObject
        }

        return try {
            JSONObject(responseBody)
        } catch (ignored: Exception) {
            defaultObject.put("error", responseBody)
            defaultObject
        }
    }
}

interface AnalyticsResponse {
    val status: HttpStatus

    companion object {
        fun create(
            responseCode: Int,
            responseBody: String?,
        ): AnalyticsResponse {
            return HttpResponse.createHttpResponse(responseCode, responseBody)
        }
    }
}

class SuccessResponse : AnalyticsResponse {
    override val status: HttpStatus = HttpStatus.SUCCESS
}

class BadRequestResponse(response: JSONObject) : AnalyticsResponse {
    override val status: HttpStatus = HttpStatus.BAD_REQUEST
    val error: String = response.getStringWithDefault("error", "")
    private var eventsWithInvalidFields: Set<Int> = setOf()
    private var eventsWithMissingFields: Set<Int> = setOf()
    private var silencedEvents: Set<Int> = setOf()
    private var silencedDevices: Set<String> = setOf()

    init {
        if (response.has("events_with_invalid_fields")) {
            eventsWithInvalidFields =
                response.getJSONObject("events_with_invalid_fields").collectIndices()
        }
        if (response.has("events_with_missing_fields")) {
            eventsWithMissingFields =
                response.getJSONObject("events_with_missing_fields").collectIndices()
        }
        if (response.has("silenced_devices")) {
            silencedDevices = response.getJSONArray("silenced_devices").toSet() as Set<String>
        }
        if (response.has("silenced_events")) {
            silencedEvents = response.getJSONArray("silenced_events").toIntArray().toSet()
        }
    }

    fun getEventIndicesToDrop(): MutableSet<Int> {
        val dropIndexes = mutableSetOf<Int>()
        dropIndexes.addAll(eventsWithInvalidFields)
        dropIndexes.addAll(eventsWithMissingFields)
        dropIndexes.addAll(silencedEvents)
        return dropIndexes
    }

    fun isEventSilenced(event: BaseEvent): Boolean {
        val eventDeviceId = event.deviceId

        return if (eventDeviceId != null) {
            silencedDevices.contains(eventDeviceId)
        } else {
            false
        }
    }

    fun isInvalidApiKeyResponse(): Boolean {
        return error.lowercase().contains("invalid api key")
    }
}

class PayloadTooLargeResponse(response: JSONObject) : AnalyticsResponse {
    override val status: HttpStatus = HttpStatus.PAYLOAD_TOO_LARGE
    val error: String = response.getStringWithDefault("error", "")
}

class TooManyRequestsResponse(response: JSONObject) : AnalyticsResponse {
    override val status: HttpStatus = HttpStatus.TOO_MANY_REQUESTS
    val error: String = response.getStringWithDefault("error", "")
    var exceededDailyQuotaUsers: Set<String> = setOf()
    var exceededDailyQuotaDevices: Set<String> = setOf()
    var throttledEvents: Set<Int> = setOf()
    var throttledDevices: Set<String> = setOf()
    var throttledUsers: Set<String> = setOf()

    init {
        if (response.has("exceeded_daily_quota_users")) {
            exceededDailyQuotaUsers = response.getJSONObject("exceeded_daily_quota_users").keySet()
        }
        if (response.has("exceeded_daily_quota_devices")) {
            exceededDailyQuotaDevices =
                response.getJSONObject("exceeded_daily_quota_devices").keySet()
        }
        if (response.has("throttled_events")) {
            throttledEvents = response.getJSONArray("throttled_events").toIntArray().toSet()
        }
        if (response.has("throttled_users")) {
            throttledUsers = response.getJSONObject("throttled_users").keySet()
        }
        if (response.has("throttled_devices")) {
            throttledDevices = response.getJSONObject("throttled_devices").keySet()
        }
    }

    fun isEventExceedDailyQuota(event: BaseEvent): Boolean {
        return (event.userId != null && exceededDailyQuotaUsers.contains(event.userId)) ||
            (event.deviceId != null && exceededDailyQuotaDevices.contains(event.deviceId))
    }
}

class TimeoutResponse : AnalyticsResponse {
    override val status: HttpStatus = HttpStatus.TIMEOUT
}

class FailedResponse(response: JSONObject) : AnalyticsResponse {
    override val status: HttpStatus = HttpStatus.FAILED
    val error: String = response.getStringWithDefault("error", "")
}

/**
 * Handle different types of responses from the server after an upload.
 *
 * The response is considered handled if:
 * - we have finished the handling of the response
 * - we have successfully recovered from the response (e.g. removed the offending bad event file, split the event file that is too large)
 *
 * @return true if response was handled or we have successfully recovered, false otherwise
 */
interface ResponseHandler {
    fun handle(
        response: AnalyticsResponse,
        events: Any,
        eventsString: String,
    ): Boolean {
        return when (response) {
            is SuccessResponse ->
                handleSuccessResponse(response, events, eventsString)

            is BadRequestResponse ->
                handleBadRequestResponse(response, events, eventsString)

            is PayloadTooLargeResponse ->
                handlePayloadTooLargeResponse(response, events, eventsString)

            is TooManyRequestsResponse ->
                handleTooManyRequestsResponse(response, events, eventsString)

            is TimeoutResponse ->
                handleTimeoutResponse(response, events, eventsString)

            else ->
                handleFailedResponse(response as FailedResponse, events, eventsString)
        }
    }

    /**
     * Handle a [HttpStatus.SUCCESS] response.
     */
    fun handleSuccessResponse(
        successResponse: SuccessResponse,
        events: Any,
        eventsString: String,
    ): Boolean

    /**
     * Handle a [HttpStatus.BAD_REQUEST] response.
     */
    fun handleBadRequestResponse(
        badRequestResponse: BadRequestResponse,
        events: Any,
        eventsString: String,
    ): Boolean

    /**
     * Handle a [HttpStatus.PAYLOAD_TOO_LARGE] response.
     */
    fun handlePayloadTooLargeResponse(
        payloadTooLargeResponse: PayloadTooLargeResponse,
        events: Any,
        eventsString: String,
    ): Boolean

    /**
     * Handle a [HttpStatus.TOO_MANY_REQUESTS] response.
     */
    fun handleTooManyRequestsResponse(
        tooManyRequestsResponse: TooManyRequestsResponse,
        events: Any,
        eventsString: String,
    ): Boolean

    /**
     * Handle a [HttpStatus.TIMEOUT] response.
     */
    fun handleTimeoutResponse(
        timeoutResponse: TimeoutResponse,
        events: Any,
        eventsString: String,
    ): Boolean

    /**
     * Handle a [HttpStatus.FAILED] response.
     */
    fun handleFailedResponse(
        failedResponse: FailedResponse,
        events: Any,
        eventsString: String,
    ): Boolean
}
