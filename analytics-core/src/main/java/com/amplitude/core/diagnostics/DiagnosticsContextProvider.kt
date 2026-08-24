package com.amplitude.core.diagnostics

/**
 * Device and app context attached as basic diagnostics tags.
 *
 * @param manufacturer Device manufacturer.
 * @param model Device model.
 * @param osName Operating system name.
 * @param osVersion Operating system version.
 * @param platform Platform name.
 * @param appVersion Host app version name, if known.
 * @param appRelease Whether this is a release (non-debuggable) host app build.
 */
public data class DiagnosticsContextInfo(
    val manufacturer: String,
    val model: String,
    val osName: String,
    val osVersion: String,
    val platform: String,
    val appVersion: String?,
    val appRelease: Boolean,
)

public fun interface DiagnosticsContextProvider {
    public fun getContextInfo(): DiagnosticsContextInfo
}
