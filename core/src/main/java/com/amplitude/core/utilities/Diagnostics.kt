package com.amplitude.core.utilities

import java.util.Collections

class Diagnostics() {
    private var malformedEvents: MutableList<String>? = null
    private var errorLogs: MutableSet<String> = Collections.synchronizedSet(mutableSetOf())

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
        errorLogs.add(log)
        while (errorLogs.size > MAX_ERROR_LOGS) {
            errorLogs.remove(errorLogs.first())
        }
    }

    fun hasDiagnostics(): Boolean {
        return (malformedEvents != null && malformedEvents!!.isNotEmpty()) || errorLogs.isNotEmpty()
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
        if (errorLogs.isNotEmpty()) {
            diagnostics["error_logs"] = errorLogs.toList()
        }
        val result = diagnostics.toJSONObject().toString()
        malformedEvents?.clear()
        errorLogs.clear()
        return result
    }
}
