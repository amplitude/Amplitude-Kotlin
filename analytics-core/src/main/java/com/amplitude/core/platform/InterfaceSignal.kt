package com.amplitude.core.platform

import com.amplitude.core.RestrictedAmplitudeFeature

/**
 * A timestamped interface change observed by an [InterfaceSignalProvider].
 *
 * [timestampMillis] uses Unix epoch milliseconds so consumers can compare it with interaction
 * timestamps. The signal is evidence that rendering changed, not proof that a specific interaction
 * caused the change.
 */
@RestrictedAmplitudeFeature
public interface InterfaceChangeSignal : Signal {
    /** Unix epoch time at which the interface change was observed. */
    public val timestampMillis: Long
}

/**
 * An Amplitude plugin that can report interface changes while [isProviding] is true.
 *
 * Availability is explicit because an absent signal can mean either that the interface did not
 * change or that observation is currently unavailable.
 */
@RestrictedAmplitudeFeature
public interface InterfaceSignalProvider : Plugin, SignalProvider {
    /** Whether this plugin is currently able to emit [InterfaceChangeSignal]s. */
    public val isProviding: Boolean
}
