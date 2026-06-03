package com.amplitude.core

import com.amplitude.core.platform.ObservePlugin

/**
 * Runtime mirror of identity (userId / deviceId) for synchronous reads (event enrichment,
 * plugin callback values) plus the registry of [ObservePlugin]s.
 *
 * Identity fields are written **silently** — notification is owned by [Amplitude] so that a
 * single fan-out reaches every plugin (timeline and store). Mutate identity via
 * [Amplitude.setUserId] / [Amplitude.setDeviceId] / [Amplitude.reset], not these setters.
 */
class State {
    @Volatile
    var userId: String? = null

    @Volatile
    var deviceId: String? = null

    val plugins: MutableList<ObservePlugin> = mutableListOf()

    fun add(
        plugin: ObservePlugin,
        amplitude: Amplitude,
    ): Boolean {
        // setup() runs outside the monitor — never hold a lock across plugin code.
        plugin.setup(amplitude)
        return synchronized(plugins) {
            plugins.add(plugin)
        }
    }

    fun remove(plugin: ObservePlugin): Boolean {
        val removed =
            synchronized(plugins) {
                plugins.removeAll { it === plugin }
            }
        // teardown() runs outside the monitor — never hold a lock across plugin code.
        if (removed) plugin.teardown()
        return removed
    }

    internal fun removeByName(name: String): List<ObservePlugin> =
        synchronized(plugins) {
            val removed = plugins.filter { it.name == name }
            plugins.removeAll(removed)
            removed
        }

    internal fun pluginsSnapshot(): List<ObservePlugin> =
        synchronized(plugins) {
            plugins.toList()
        }
}
