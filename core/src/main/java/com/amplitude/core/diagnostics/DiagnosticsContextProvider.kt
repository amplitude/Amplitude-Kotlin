package com.amplitude.core.diagnostics

data class DiagnosticsContextInfo(
    val manufacturer: String,
    val model: String,
    val osName: String,
    val osVersion: String,
    val platform: String,
    val appVersion: String,
)

fun interface DiagnosticsContextProvider {
    suspend fun getContextInfo(): DiagnosticsContextInfo
}
