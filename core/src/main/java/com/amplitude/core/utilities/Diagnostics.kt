package com.amplitude.core.utilities

import java.util.Collections

class Diagnostics() {
    private var malformedEvents: MutableList<String>? = null
    private var errorLogs: MutableSet<String>? = null

    companion object {
        private const val MAX_ERROR_LOGS = 10
    }

    fun addMalformedEvent(event: String) {
        if (malformedEvents == null) {
            malformedEvents = Collections.synchronizedList(mutableListOf())
        }
        malformedEvents?.add(event)
    }

    fun addErrorLog(log: String) {
        if (errorLogs == null) {
            errorLogs = Collections.synchronizedSet(mutableSetOf())
        }
        errorLogs?.add(log)
    }

    fun hasDiagnostics(): Boolean {
        return (malformedEvents != null && malformedEvents!!.isNotEmpty()) || (errorLogs != null && errorLogs!!.isNotEmpty())
    }

    /**
     * Extracts the diagnostics as a JSON string.
     * @return JSON string of diagnostics or empty if no diagnostics are present.
     */
    fun extractDiagnostics(): String {
        if (!hasDiagnostics()) {
            return ""
        }
        val diagnostics = mutableMapOf<String, List<String>>()
        if (malformedEvents != null && malformedEvents!!.isNotEmpty()) {
            diagnostics["malformed_events"] = malformedEvents!!
        }
        if (errorLogs != null && errorLogs!!.isNotEmpty()) {
            // pick the first MAX_ERROR_LOGS from the set and convert to list
            val errorLogsToTake = errorLogs!!.take(MAX_ERROR_LOGS)
            diagnostics["error_logs"] = errorLogsToTake
            errorLogs?.removeAll(errorLogsToTake.toSet())
        }
        val result = diagnostics.toJSONObject().toString()
        malformedEvents?.clear()
        return result
    }
}
