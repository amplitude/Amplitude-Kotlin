package com.amplitude.core.platform

/**
 * Events emitted by plugins to communicate internal state changes.
 */
public interface Signal

/**
 * Provides signal emission capabilities for plugins.
 */
public interface SignalProvider {
    public var active: Boolean

    /**
     * Enables signal emission.
     */
    public fun activate() {
        active = true
    }

    /**
     * Disables signal emission.
     */
    public fun deactivate() {
        active = false
    }

    /**
     * Emits a signal if the provider is active.
     */
    public fun Plugin.emitSignal(signal: Signal) {
        if (active) {
            amplitude.emitSignal(signal)
        }
    }
}
