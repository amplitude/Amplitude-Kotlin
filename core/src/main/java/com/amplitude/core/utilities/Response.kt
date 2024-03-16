package com.amplitude.core.utilities

import com.amplitude.core.events.BaseEvent
import org.json.JSONObject
import java.lang.Exception

internal object HttpResponse {
    fun createHttpResponse(code: Int, responseBody: String?): Response {
        when (code) {
            HttpStatus.SUCCESS.code -> {
                return SuccessResponse()
            }
            HttpStatus.BAD_REQUEST.code -> {
                return BadRequestResponse(JSONObject(responseBody))
            }
            HttpStatus.PAYLOAD_TOO_LARGE.code -> {
                return PayloadTooLargeResponse(JSONObject(responseBody))
            }
            HttpStatus.TOO_MANY_REQUESTS.code -> {
                return TooManyRequestsResponse(JSONObject(responseBody))
            }
            HttpStatus.TIMEOUT.code -> {
                return TimeoutResponse()
            }
            else -> {
                return FailedResponse(parseResponseBodyOrGetDefault(responseBody))
            }
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

interface Response {
    val status: HttpStatus
}

class SuccessResponse() : Response {
    override val status: HttpStatus = HttpStatus.SUCCESS
}

class BadRequestResponse(response: JSONObject) : Response {
    override val status: HttpStatus = HttpStatus.BAD_REQUEST
    val error: String = response.getStringWithDefault("error", "")
    var eventsWithInvalidFields: Set<Int> = setOf()
    var eventsWithMissingFields: Set<Int> = setOf()
    var silencedEvents: Set<Int> = setOf()
    var silencedDevices: Set<String> = setOf()

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
        event.deviceId?.let {
            return silencedDevices.contains(it)
        } ?: let {
            return false
        }
    }
}

class PayloadTooLargeResponse(response: JSONObject) : Response {
    override val status: HttpStatus = HttpStatus.PAYLOAD_TOO_LARGE
    val error: String = response.getStringWithDefault("error", "")
}

class TooManyRequestsResponse(response: JSONObject) : Response {
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

class TimeoutResponse() : Response {
    override val status: HttpStatus = HttpStatus.TIMEOUT
}

class FailedResponse(response: JSONObject) : Response {
    override val status: HttpStatus = HttpStatus.FAILED
    val error: String = response.getStringWithDefault("error", "")
}

interface ResponseHandler {
    fun handle(response: Response, events: Any, eventsString: String) {
        when (response) {
            is SuccessResponse -> {
                handleSuccessResponse(response, events, eventsString)
            }
            is BadRequestResponse -> {
                handleBadRequestResponse(response, events, eventsString)
            }
            is PayloadTooLargeResponse -> {
                handlePayloadTooLargeResponse(response, events, eventsString)
            }
            is TooManyRequestsResponse -> {
                handleTooManyRequestsResponse(response, events, eventsString)
            }
            is TimeoutResponse -> {
                handleTimeoutResponse(response, events, eventsString)
            }
            else -> {
                handleFailedResponse(response as FailedResponse, events, eventsString)
            }
        }
    }

    fun handleSuccessResponse(successResponse: SuccessResponse, events: Any, eventsString: String)
    fun handleBadRequestResponse(badRequestResponse: BadRequestResponse, events: Any, eventsString: String)
    fun handlePayloadTooLargeResponse(payloadTooLargeResponse: PayloadTooLargeResponse, events: Any, eventsString: String)
    fun handleTooManyRequestsResponse(tooManyRequestsResponse: TooManyRequestsResponse, events: Any, eventsString: String)
    fun handleTimeoutResponse(timeoutResponse: TimeoutResponse, events: Any, eventsString: String)
    fun handleFailedResponse(failedResponse: FailedResponse, events: Any, eventsString: String)
}
