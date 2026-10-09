package com.amplitude.core.utilities

public class Diagnostics() {
    private val lock = Any()
    private val malformedEvents = mutableListOf<String>()
    private val errorLogs = mutableSetOf<String>()

    public companion object {
        private const val MAX_ERROR_LOGS = 10
        private const val MAX_MALFORMED_EVENTS = 10
        private const val MAX_MALFORMED_EVENT_LENGTH = 1_000
    }

    public fun addMalformedEvent(event: String) {
        synchronized(lock) {
            if (malformedEvents.size == MAX_MALFORMED_EVENTS) malformedEvents.removeAt(0)
            malformedEvents.add(event.take(MAX_MALFORMED_EVENT_LENGTH))
        }
    }

    public fun addErrorLog(log: String) {
        synchronized(lock) {
            errorLogs.add(log)
            if (errorLogs.size > MAX_ERROR_LOGS) errorLogs.remove(errorLogs.first())
        }
    }

    public fun hasDiagnostics(): Boolean =
        synchronized(lock) {
            malformedEvents.isNotEmpty() || errorLogs.isNotEmpty()
        }

    /**
     * Extracts the diagnostics as a JSON string.
     * @return JSON string of diagnostics or empty if no diagnostics are present.
     */
    public fun extractDiagnostics(): String? =
        synchronized(lock) {
            val diagnostics = mutableMapOf<String, List<String>>()
            if (malformedEvents.isNotEmpty()) diagnostics["malformed_events"] = malformedEvents.toList()
            if (errorLogs.isNotEmpty()) diagnostics["error_logs"] = errorLogs.toList()
            if (diagnostics.isEmpty()) return@synchronized null
            val result = diagnostics.toJSONObject().toString()
            malformedEvents.clear()
            errorLogs.clear()
            result
        }
}
