package com.amplitude.core.utilities

import com.amplitude.core.diagnostics.DiagnosticsClient
import com.amplitude.core.events.BaseEvent

/**
 * Records the outcome of an event upload attempt for diagnostics.
 * Increments sent/dropped counters and records detailed event information for drops.
 *
 * @param events The list of events that were uploaded
 * @param status The HTTP status code of the response
 * @param message The response message or error description
 */
internal fun DiagnosticsClient.recordEventOutcome(
    events: List<BaseEvent>,
    status: Int,
    message: String,
) {
    if (events.isEmpty()) return

    if (status in 200..299) {
        increment(name = "analytics.events.sent", size = events.size.toLong())
    } else {
        increment(name = "analytics.events.dropped", size = events.size.toLong())
        recordEvent(
            name = "analytics.events.dropped",
            properties =
                mapOf(
                    "events" to events.map { it.eventType },
                    "count" to events.size,
                    "code" to status,
                    "message" to message,
                ),
        )
    }
}
