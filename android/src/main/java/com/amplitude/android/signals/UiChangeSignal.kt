package com.amplitude.android.signals

import com.amplitude.core.platform.Signal
import java.util.Date

/**
 * A signal that indicates a change in the UI.
 * This is used to trigger UI-related events in the Amplitude SDK.
 *
 * @property timestamp The time when the UI change occurred.
 */
data class UiChangeSignal(val timestamp: Date) : Signal
