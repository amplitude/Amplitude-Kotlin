package com.amplitude.core.diagnostics

public data class DiagnosticsContextInfo(
    val manufacturer: String,
    val model: String,
    val osName: String,
    val osVersion: String,
    val platform: String,
    val appVersion: String?,
)

public fun interface DiagnosticsContextProvider {
    public fun getContextInfo(): DiagnosticsContextInfo
}
