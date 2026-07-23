package com.amplitude.core.utilities.http

import com.amplitude.core.events.BaseEvent
import com.amplitude.core.utilities.collectIndices
import com.amplitude.core.utilities.getStringWithDefault
import com.amplitude.core.utilities.http.HttpStatus.BAD_REQUEST
import com.amplitude.core.utilities.http.HttpStatus.PAYLOAD_TOO_LARGE
import com.amplitude.core.utilities.http.HttpStatus.SUCCESS
import com.amplitude.core.utilities.http.HttpStatus.TIMEOUT
import com.amplitude.core.utilities.http.HttpStatus.TOO_MANY_REQUESTS
import com.amplitude.core.utilities.toIntArray
import org.json.JSONObject

public sealed class AnalyticsResponse(public val status: HttpStatus) {
    public companion object {
        public fun create(
            responseCode: Int,
            responseBody: String?,
        ): AnalyticsResponse {
            return when (responseCode) {
                in SUCCESS.range -> SuccessResponse()
                in BAD_REQUEST.range -> BadRequestResponse(JSONObject(responseBody))
                in PAYLOAD_TOO_LARGE.range -> PayloadTooLargeResponse(JSONObject(responseBody))
                in TOO_MANY_REQUESTS.range -> TooManyRequestsResponse(JSONObject(responseBody))
                in TIMEOUT.range -> TimeoutResponse()
                else -> FailedResponse(parseResponseBodyOrGetDefault(responseBody))
            }
        }

        private fun parseResponseBodyOrGetDefault(responseBody: String?): JSONObject {
            val defaultObject = JSONObject()
            if (responseBody.isNullOrEmpty()) return defaultObject

            return try {
                JSONObject(responseBody)
            } catch (ignored: Exception) {
                defaultObject.put("error", responseBody)
                defaultObject
            }
        }
    }
}

public class SuccessResponse : AnalyticsResponse(SUCCESS)

public class BadRequestResponse(response: JSONObject) : AnalyticsResponse(BAD_REQUEST) {
    public val error: String = response.getStringWithDefault("error", "")
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

    public fun getEventIndicesToDrop(): MutableSet<Int> {
        val dropIndexes = mutableSetOf<Int>()
        dropIndexes.addAll(eventsWithInvalidFields)
        dropIndexes.addAll(eventsWithMissingFields)
        dropIndexes.addAll(silencedEvents)
        return dropIndexes
    }

    public fun isEventSilenced(event: BaseEvent): Boolean {
        val eventDeviceId = event.deviceId

        return if (eventDeviceId != null) {
            silencedDevices.contains(eventDeviceId)
        } else {
            false
        }
    }

    public fun isInvalidApiKeyResponse(): Boolean {
        return error.lowercase().contains("invalid api key")
    }
}

public class PayloadTooLargeResponse(response: JSONObject) :
    AnalyticsResponse(PAYLOAD_TOO_LARGE) {
    public val error: String = response.getStringWithDefault("error", "")
}

public class TooManyRequestsResponse(response: JSONObject) :
    AnalyticsResponse(TOO_MANY_REQUESTS) {
    private var exceededDailyQuotaUsers: Set<String> = setOf()
    private var exceededDailyQuotaDevices: Set<String> = setOf()
    private var throttledDevices: Set<String> = setOf()
    private var throttledUsers: Set<String> = setOf()

    public val error: String = response.getStringWithDefault("error", "")
    public var throttledEvents: Set<Int> = setOf<Int>()

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

    public fun isEventExceedDailyQuota(event: BaseEvent): Boolean {
        return (event.userId != null && exceededDailyQuotaUsers.contains(event.userId)) ||
            (event.deviceId != null && exceededDailyQuotaDevices.contains(event.deviceId))
    }
}

public class TimeoutResponse : AnalyticsResponse(TIMEOUT)

public class FailedResponse(response: JSONObject) : AnalyticsResponse(HttpStatus.FAILED) {
    public val error: String = response.getStringWithDefault("error", "")
}

/**
 * Handle different types of responses from the server after an upload.
 *
 * A response may be recoverable, and we try to handle it on the client side.
 * - e.g. we remove the offending bad event file, split the event file that is too large, etc.
 *
 */
public interface ResponseHandler {
    /**
     * Main entry point to handle a response after an upload
     * @return true if we shouldRetryUploadOnFailure, false if we should not retry, or null if not applicable
     */
    public fun handle(
        response: AnalyticsResponse,
        events: Any,
        eventsString: String,
    ): Boolean? {
        val shouldRetryUploadOnFailure =
            when (response) {
                is SuccessResponse -> {
                    handleSuccessResponse(response, events, eventsString)
                    // N/A
                    null
                }

                is BadRequestResponse -> {
                    // RETRY if bad events are removed and there's nothing to retry
                    // DON'T RETRY if it's a response that comes from a proxy
                    handleBadRequestResponse(response, events, eventsString)
                }

                is PayloadTooLargeResponse -> {
                    handlePayloadTooLargeResponse(response, events, eventsString)
                    // RETRY as large event files will be split and retried individually
                    true
                }

                is TooManyRequestsResponse -> {
                    handleTooManyRequestsResponse(response, events, eventsString)
                    // Always RETRY
                    true
                }

                is TimeoutResponse -> {
                    handleTimeoutResponse(response, events, eventsString)
                    // Always RETRY
                    true
                }

                else -> {
                    handleFailedResponse(response as FailedResponse, events, eventsString)
                    // Always RETRY
                    true
                }
            }

        return shouldRetryUploadOnFailure
    }

    /**
     * Handle a [HttpStatus.SUCCESS] response.
     */
    public fun handleSuccessResponse(
        successResponse: SuccessResponse,
        events: Any,
        eventsString: String,
    )

    /**
     * Handle a [HttpStatus.BAD_REQUEST] response.
     *
     * @return true if we should retry the upload (e.g. no events dropped), else false as we have discarded the events
     */
    public fun handleBadRequestResponse(
        badRequestResponse: BadRequestResponse,
        events: Any,
        eventsString: String,
    ): Boolean

    /**
     * Handle a [HttpStatus.PAYLOAD_TOO_LARGE] response.
     */
    public fun handlePayloadTooLargeResponse(
        payloadTooLargeResponse: PayloadTooLargeResponse,
        events: Any,
        eventsString: String,
    )

    /**
     * Handle a [HttpStatus.TOO_MANY_REQUESTS] response.
     */
    public fun handleTooManyRequestsResponse(
        tooManyRequestsResponse: TooManyRequestsResponse,
        events: Any,
        eventsString: String,
    )

    /**
     * Handle a [HttpStatus.TIMEOUT] response.
     */
    public fun handleTimeoutResponse(
        timeoutResponse: TimeoutResponse,
        events: Any,
        eventsString: String,
    )

    /**
     * Handle a [HttpStatus.FAILED] response.
     */
    public fun handleFailedResponse(
        failedResponse: FailedResponse,
        events: Any,
        eventsString: String,
    )
}

/**
 * Enum class to represent the HTTP status codes and whether the upload should be retried on failure.
 * A request requires a retry if the event file/s are still present and we want to attempt to upload them again.
 */
public enum class HttpStatus(
    code: Int,
    public val range: IntRange = code..code,
) {
    SUCCESS(200, range = 200..299),
    BAD_REQUEST(400),
    TIMEOUT(408),
    PAYLOAD_TOO_LARGE(413),
    TOO_MANY_REQUESTS(429),
    FAILED(500, 500..599),
    ;

    public val statusCode: Int
        get() = range.first
}
