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
 * The observe-plugin registry below is **deprecated**. All plugins — including [ObservePlugin]s —
 * are now registered and managed by the [com.amplitude.core.platform.Timeline]. These members
 * delegate to [Amplitude] / [Timeline] for binary compatibility and will be removed in the
 * next major version.
 *
 * A [State] is owned by a single [Amplitude]; sharing one instance across multiple [Amplitude]s is
 * unsupported (the deprecated delegates resolve against the most recently constructed owner).
 */
class State {
    @Volatile
    var userId: String? = null

    @Volatile
    var deviceId: String? = null

    /** Back-reference set by [Amplitude] immediately after [Timeline] creation. */
    internal var owner: Amplitude? = null

    /**
     * A detached snapshot of the observe plugins registered in the
     * [com.amplitude.core.platform.Timeline]. Mutating the returned list has no effect —
     * register/unregister via [Amplitude.add] / [Amplitude.remove].
     */
    @Deprecated(
        "Observe plugins are managed by Amplitude/Timeline; this registry will be removed in the " +
            "next major version. Register via Amplitude.add(plugin).",
    )
    val plugins: MutableList<ObservePlugin>
        get() = owner?.observePluginsSnapshot()?.toMutableList() ?: mutableListOf()

    @Deprecated(
        "Register observe plugins via Amplitude.add(plugin). State's registry will be removed in " +
            "the next major version.",
        ReplaceWith("amplitude.add(plugin)"),
    )
    fun add(
        plugin: ObservePlugin,
        amplitude: Amplitude,
    ): Boolean {
        amplitude.add(plugin)
        // Reflect the real outcome: first-wins name dedup may have skipped a duplicate.
        return amplitude.observePluginsSnapshot().any { it === plugin }
    }

    @Deprecated(
        "Remove observe plugins via Amplitude.remove(plugin). State's registry will be removed in " +
            "the next major version.",
    )
    fun remove(plugin: ObservePlugin): Boolean {
        val amplitude = owner ?: return false
        val wasRegistered = amplitude.observePluginsSnapshot().any { it === plugin }
        amplitude.remove(plugin)
        return wasRegistered
    }
}
