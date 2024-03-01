package com.amplitude.core.utilities

import java.util.Collections

class Diagnostics() {
    private var malformedEvents: MutableList<String>? = null
    private var errorLogs: MutableList<String>? = null

    fun addMalformedEvent(event: String) {
        if (malformedEvents == null) {
            malformedEvents = Collections.synchronizedList(mutableListOf())
        }
        malformedEvents?.add(event)
    }

    fun addErrorLog(log: String) {
        if (errorLogs == null) {
            errorLogs = Collections.synchronizedList(mutableListOf())
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
            diagnostics["error_logs"] = errorLogs!!
        }
        val result = diagnostics.toJSONObject().toString()
        malformedEvents?.clear()
        errorLogs?.clear()
        return result
    }
}
