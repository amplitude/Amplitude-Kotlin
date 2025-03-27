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

sealed class AnalyticsResponse(val status: HttpStatus) {
    companion object {
        fun create(
            responseCode: Int,
            responseBody: String?,
        ): AnalyticsResponse {
            return HttpResponse.createHttpResponse(responseCode, responseBody)
        }
    }
}

class SuccessResponse : AnalyticsResponse(HttpStatus.SUCCESS)

class BadRequestResponse(response: JSONObject) : AnalyticsResponse(HttpStatus.BAD_REQUEST) {
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

class PayloadTooLargeResponse(response: JSONObject) :
    AnalyticsResponse(HttpStatus.PAYLOAD_TOO_LARGE) {
    val error: String = response.getStringWithDefault("error", "")
}

class TooManyRequestsResponse(response: JSONObject) :
    AnalyticsResponse(HttpStatus.TOO_MANY_REQUESTS) {
    private var exceededDailyQuotaUsers: Set<String> = setOf()
    private var exceededDailyQuotaDevices: Set<String> = setOf()
    private var throttledDevices: Set<String> = setOf()
    private var throttledUsers: Set<String> = setOf()

    val error: String = response.getStringWithDefault("error", "")
    var throttledEvents = setOf<Int>()

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

class TimeoutResponse : AnalyticsResponse(HttpStatus.TIMEOUT)

class FailedResponse(response: JSONObject) : AnalyticsResponse(HttpStatus.FAILED) {
    val error: String = response.getStringWithDefault("error", "")
}

interface ResponseHandler {
    fun handle(
        response: AnalyticsResponse,
        events: Any,
        eventsString: String,
    ) {
        when (response) {
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

    fun handleSuccessResponse(
        successResponse: SuccessResponse,
        events: Any,
        eventsString: String,
    )

    fun handleBadRequestResponse(
        badRequestResponse: BadRequestResponse,
        events: Any,
        eventsString: String,
    )

    fun handlePayloadTooLargeResponse(
        payloadTooLargeResponse: PayloadTooLargeResponse,
        events: Any,
        eventsString: String,
    )

    fun handleTooManyRequestsResponse(
        tooManyRequestsResponse: TooManyRequestsResponse,
        events: Any,
        eventsString: String,
    )

    fun handleTimeoutResponse(
        timeoutResponse: TimeoutResponse,
        events: Any,
        eventsString: String,
    )

    fun handleFailedResponse(
        failedResponse: FailedResponse,
        events: Any,
        eventsString: String,
    )
}

enum class HttpStatus(val code: Int) {
    SUCCESS(200),
    BAD_REQUEST(400),
    TIMEOUT(408),
    PAYLOAD_TOO_LARGE(413),
    TOO_MANY_REQUESTS(429),
    FAILED(500),
}