package com.amplitude.android.anr

import com.amplitude.core.RestrictedAmplitudeFeature
import com.amplitude.core.diagnostics.DiagnosticsClient

private const val ANR_EVENT_NAME = "analytics.anr"
private const val ANR_REPORT_PROPERTY = "report"

/**
 * Records an ANR report from the previous app run as an `analytics.anr` counter and an
 * `analytics.anr` event carrying the report text.
 *
 * Upload follows the diagnostics client's own enabled and sampling rules.
 */
@OptIn(RestrictedAmplitudeFeature::class)
internal fun DiagnosticsClient.recordAnr(anrString: String) {
    increment(ANR_EVENT_NAME)
    recordEvent(
        ANR_EVENT_NAME,
        mapOf(ANR_REPORT_PROPERTY to anrString),
    )
}
