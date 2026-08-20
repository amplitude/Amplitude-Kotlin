package com.amplitude.android.crash

import com.amplitude.core.RestrictedAmplitudeFeature
import com.amplitude.core.diagnostics.DiagnosticsClient

private const val CRASH_EVENT_NAME = "analytics.crash"
private const val CRASH_REPORT_PROPERTY = "report"

/**
 * Records a crash report from the previous app run as an `analytics.crash` counter and an
 * `analytics.crash` event carrying the report text, matching the iOS diagnostics schema.
 *
 * The report is buffered regardless of sampling; upload stays gated on the session being
 * sampled in, so a crash consumed before remote config arrives is not lost.
 */
@OptIn(RestrictedAmplitudeFeature::class)
internal fun DiagnosticsClient.recordCrash(crashString: String) {
    increment(CRASH_EVENT_NAME)
    recordEvent(
        CRASH_EVENT_NAME,
        mapOf(CRASH_REPORT_PROPERTY to crashString),
    )
}
