package com.amplitude.core.platform

import java.util.Date

/**
 * A UI or interface mutation observed by an [InterfaceSignalProvider].
 *
 * Session Replay emits these so frustration autocapture can tell a click produced a visible
 * change. The payload is only a timestamp; receivers decide what to do with it.
 *
 * @property time when the interface change was observed.
 */
public open class InterfaceChangeSignal(
    public val time: Date,
)

/**
 * Pull-model source of [InterfaceChangeSignal]s. Session Replay implements this on its
 * [UniversalPlugin]; [com.amplitude.core.Amplitude] holds the provider when the plugin is added
 * and clears it on remove.
 *
 * Analytics never pushes into the plugin. Receivers register here and the provider notifies them
 * when it starts, stops, or observes a change.
 */
public interface InterfaceSignalProvider {
    /** Whether the provider is currently emitting interface changes. */
    public val isProviding: Boolean

    /** Registers [receiver] to observe interface changes. Duplicate adds are ignored. */
    public fun addInterfaceSignalReceiver(receiver: InterfaceSignalReceiver)

    /** Unregisters [receiver]. No-op if it is not registered. */
    public fun removeInterfaceSignalReceiver(receiver: InterfaceSignalReceiver)
}

/**
 * Observer of an [InterfaceSignalProvider].
 */
public interface InterfaceSignalReceiver {
    /** A visible interface change occurred at [signal].time. */
    public fun onInterfaceChanged(signal: InterfaceChangeSignal)

    /** The provider began emitting interface changes. */
    public fun onStartProviding()

    /** The provider stopped emitting interface changes. */
    public fun onStopProviding()
}
