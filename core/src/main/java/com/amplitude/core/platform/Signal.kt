package com.amplitude.core.platform

/**
 * Events emitted by plugins to communicate internal state changes.
 */
interface Signal

/**
 * Provides signal emission capabilities for plugins.
 */
interface SignalProvider {
    var active: Boolean

    /**
     * Enables signal emission.
     */
    fun activate() {
        active = true
    }

    /**
     * Disables signal emission.
     */
    fun deactivate() {
        active = false
    }

    /**
     * Emits a signal if the provider is active.
     */
    fun Plugin.emitSignal(signal: Signal) {
        if (active) {
            amplitude.emitSignal(signal)
        }
    }
}
