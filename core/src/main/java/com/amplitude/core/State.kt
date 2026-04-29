package com.amplitude.core

import com.amplitude.core.platform.ObservePlugin
import com.amplitude.core.platform.Plugin
import java.util.EnumSet

class State {
    /**
     * The kinds of identity values an [Amplitude] instance can mutate. Used by
     * [onIdentityChanged] to deliver one batched notification when callers like
     * [Amplitude.reset] flip multiple identity fields together.
     */
    internal enum class IdentityChangeType {
        USER_ID,
        DEVICE_ID,
    }

    /**
     * Internal hook wired by [Amplitude] to fan-out identity mutations to
     * timeline + observe plugins. The set tells the consumer which fields
     * changed in this notification (one or many).
     */
    internal var onIdentityChanged: ((State, EnumSet<IdentityChangeType>) -> Unit)? = null

    /**
     * When true, the per-field setters skip their own [onIdentityChanged] /
     * ObservePlugin notifications. The active batched update is responsible
     * for emitting one notification once all fields are written.
     */
    private var batching: Boolean = false

    var userId: String? = null
        set(value: String?) {
            field = value
            if (batching) return
            plugins.forEach { plugin ->
                plugin.onUserIdChanged(value)
            }
            onIdentityChanged?.invoke(this, EnumSet.of(IdentityChangeType.USER_ID))
        }

    var deviceId: String? = null
        set(value: String?) {
            field = value
            if (batching) return
            plugins.forEach { plugin ->
                plugin.onDeviceIdChanged(value)
            }
            onIdentityChanged?.invoke(this, EnumSet.of(IdentityChangeType.DEVICE_ID))
        }

    /**
     * Update userId and deviceId atomically and emit a single bundled
     * [onIdentityChanged] notification covering both fields. Used by
     * [Amplitude.reset] so plugins observe one identity change rather than
     * two interleaved ones.
     */
    internal fun setIdentity(
        userId: String?,
        deviceId: String?,
    ) {
        batching = true
        try {
            this.userId = userId
            this.deviceId = deviceId
        } finally {
            batching = false
        }
        plugins.forEach { plugin ->
            plugin.onUserIdChanged(userId)
            plugin.onDeviceIdChanged(deviceId)
        }
        onIdentityChanged?.invoke(
            this,
            EnumSet.of(IdentityChangeType.USER_ID, IdentityChangeType.DEVICE_ID),
        )
    }

    val plugins: MutableList<ObservePlugin> = mutableListOf()

    fun add(
        plugin: ObservePlugin,
        amplitude: Amplitude,
    ) = synchronized(plugins) {
        plugin.setup(amplitude)
        plugins.add(plugin)
    }

    fun remove(plugin: ObservePlugin) =
        synchronized(plugins) {
            plugins.removeAll { it === plugin }
        }

    /**
     * Remove every [ObservePlugin] whose [Plugin.name] matches [name] from the
     * store, calling [Plugin.teardown] on each. Returns true if at least one
     * plugin was removed.
     */
    internal fun removeByName(name: String): Boolean =
        synchronized(plugins) {
            val toRemove = plugins.filter { it.name == name }
            if (toRemove.isEmpty()) return@synchronized false
            plugins.removeAll(toRemove)
            toRemove.forEach { it.teardown() }
            true
        }
}
