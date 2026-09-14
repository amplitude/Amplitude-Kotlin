package com.amplitude.android.signals

import com.amplitude.core.RestrictedAmplitudeFeature
import com.amplitude.core.platform.InterfaceChangeSignal
import java.util.Date

/**
 * A signal that indicates a change in the UI.
 * This is used to trigger UI-related events in the Amplitude SDK.
 *
 * @property timestamp The time when the UI change occurred.
 */
@OptIn(RestrictedAmplitudeFeature::class)
public data class UiChangeSignal(val timestamp: Date) : InterfaceChangeSignal {
    override val timestampMillis: Long
        get() = timestamp.time
}
