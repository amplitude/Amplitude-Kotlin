package com.amplitude.core

import com.amplitude.core.platform.ObservePlugin

/**
 * Runtime mirror of identity (userId / deviceId) for synchronous reads — event enrichment
 * and the values handed to plugin state-change callbacks.
 *
 * Identity fields are written **silently**; notification is owned by [Amplitude] so a single
 * fan-out reaches every registered plugin. Mutate identity via [Amplitude.setUserId] /
 * [Amplitude.setDeviceId] / [Amplitude.reset], not these setters.
 *
 * The [ObservePlugin] registry below is **deprecated**. Now that every plugin receives the
 * identity/state callbacks through [Amplitude]'s fan-out, observe plugins no longer need a
 * dedicated registry here — register and remove them via [Amplitude.add] / [Amplitude.remove]
 * like any other plugin. The registry is retained for binary compatibility and will be removed
 * in the next major version, when observe plugins move fully into the
 * [com.amplitude.core.platform.Timeline].
 */
class State {
    @Volatile
    var userId: String? = null

    @Volatile
    var deviceId: String? = null

    @Deprecated(
        "Observe plugins are managed by Amplitude/Timeline; this registry will be removed in the " +
            "next major version. Register via Amplitude.add(plugin).",
    )
    val plugins: MutableList<ObservePlugin> = mutableListOf()

    @Deprecated(
        "Register observe plugins via Amplitude.add(plugin). State's registry will be removed in " +
            "the next major version.",
        ReplaceWith("amplitude.add(plugin)"),
    )
    fun add(
        plugin: ObservePlugin,
        amplitude: Amplitude,
    ): Boolean {
        // setup() runs outside the monitor — never hold a lock across plugin code.
        plugin.setup(amplitude)
        @Suppress("DEPRECATION")
        return synchronized(plugins) {
            plugins.add(plugin)
        }
    }

    @Deprecated(
        "Remove observe plugins via Amplitude.remove(plugin). State's registry will be removed in " +
            "the next major version.",
    )
    fun remove(plugin: ObservePlugin): Boolean {
        @Suppress("DEPRECATION")
        val removed =
            synchronized(plugins) {
                plugins.removeAll { it === plugin }
            }
        // teardown() runs outside the monitor — never hold a lock across plugin code.
        if (removed) plugin.teardown()
        return removed
    }
}
