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

    // Private backing fields allow silent writes (without firing onIdentityChanged)
    // so that IdentityCoordinator can update state under its lock and dispatch
    // the notification afterwards, outside the lock.
    private var _userId: String? = null
    private var _deviceId: String? = null

    // Supported identity mutation goes through Amplitude.setUserId/setDeviceId
    // (via IdentityCoordinator). These public setters remain for backward
    // compatibility; they fire onIdentityChanged, whose wiring fans out through
    // Amplitude.notifyAllPlugins — so each plugin callback is isolated
    // (a throwing plugin can't propagate back to the setter's caller). They do
    // NOT run under the IdentityCoordinator lock, so prefer the Amplitude API.
    var userId: String?
        get() = _userId
        set(value) {
            _userId = value
            onIdentityChanged?.invoke(this, EnumSet.of(IdentityChangeType.USER_ID))
        }

    var deviceId: String?
        get() = _deviceId
        set(value) {
            _deviceId = value
            onIdentityChanged?.invoke(this, EnumSet.of(IdentityChangeType.DEVICE_ID))
        }

    /**
     * Write [value] to the userId backing field without firing [onIdentityChanged].
     * Callers must invoke [notifyIdentityChanged] afterwards (outside any lock)
     * to dispatch the notification. Used by [com.amplitude.core.IdentityCoordinator]
     * to prevent reentrancy: field mutation happens under the coordinator's lock,
     * notification dispatch happens after the lock is released.
     */
    internal fun setUserIdSilent(value: String?) {
        _userId = value
    }

    /**
     * Write [value] to the deviceId backing field without firing [onIdentityChanged].
     * See [setUserIdSilent] for the reentrancy rationale.
     */
    internal fun setDeviceIdSilent(value: String?) {
        _deviceId = value
    }

    /**
     * Fire [onIdentityChanged] for the given [changes]. Called by
     * [com.amplitude.core.IdentityCoordinator] after releasing its lock so that
     * plugin callbacks cannot re-enter the coordinator while it holds the lock.
     */
    internal fun notifyIdentityChanged(changes: EnumSet<IdentityChangeType>) {
        onIdentityChanged?.invoke(this, changes)
    }

    val plugins: MutableList<ObservePlugin> = mutableListOf()

    fun add(
        plugin: ObservePlugin,
        amplitude: Amplitude,
    ): Boolean {
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
        if (removed) plugin.teardown()
        return removed
    }

    /**
     * Remove every [ObservePlugin] whose [Plugin.name] matches [name] from the
     * store, calling [Plugin.teardown] on each. Returns true if at least one
     * plugin was removed.
     */
    internal fun removeByName(name: String): Boolean {
        val toRemove =
            synchronized(plugins) {
                val matches = plugins.filter { it.name == name }
                if (matches.isEmpty()) return false
                plugins.removeAll(matches)
                matches
            }
        toRemove.forEach {
            try {
                it.teardown()
            } catch (_: Exception) {
            }
        }
        return true
    }
}
