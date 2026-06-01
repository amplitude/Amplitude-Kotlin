package com.amplitude.core

import com.amplitude.id.IdentityManager
import java.util.EnumSet

/**
 * Coordinates identity (userId/deviceId) updates between [State] and [IdentityManager].
 *
 * All mutations go through synchronized blocks to ensure atomic state + persistence.
 * Before build, only [State] is updated and pending flags are set.
 * During [bootstrap], persisted identity is reconciled with any pre-build changes.
 *
 * ## Reentrancy safety
 *
 * Plugin callbacks (e.g. [com.amplitude.core.platform.Plugin.onUserIdChanged]) can call
 * back into [setUserId]/[setDeviceId] on the same thread. Because [synchronized] is
 * reentrant on the JVM, a naive implementation that fires [State.onIdentityChanged] while
 * holding the lock allows the inner call to fully commit, then the outer call's commit
 * overwrites the inner value — a silent data-loss bug.
 *
 * Fix: under the lock we write to [State]'s backing fields silently (no notification) and
 * commit to [IdentityManager]. After the lock is released we dispatch the notification.
 * If a callback then calls [setUserId] again, that call acquires the lock, updates state,
 * and dispatches its own notification — without any outer commit following it.
 */
internal class IdentityCoordinator internal constructor(private val state: State) {
    private val lock = Any()
    private var identityManager: IdentityManager? = null
    private var pendingUserId: Pending<String?>? = null
    private var pendingDeviceId: Pending<String>? = null

    @JvmInline
    private value class Pending<T>(val value: T)

    private fun <T> Pending<T>?.getOrElse(default: T?) = if (this != null) value else default

    fun setUserId(userId: String?) {
        synchronized(lock) {
            state.setUserIdSilent(userId)
            identityManager?.editIdentity()?.setUserId(userId)?.commit()
                ?: run { pendingUserId = Pending(userId) }
        }
        // Dispatch notification OUTSIDE the lock so plugin callbacks cannot
        // re-enter this coordinator while it holds the lock.
        state.notifyIdentityChanged(EnumSet.of(State.IdentityChangeType.USER_ID))
    }

    fun setDeviceId(deviceId: String) {
        synchronized(lock) {
            state.setDeviceIdSilent(deviceId)
            identityManager?.editIdentity()?.setDeviceId(deviceId)?.commit()
                ?: run { pendingDeviceId = Pending(deviceId) }
        }
        // Dispatch notification OUTSIDE the lock.
        state.notifyIdentityChanged(EnumSet.of(State.IdentityChangeType.DEVICE_ID))
    }

    /**
     * Reset the identity atomically: userId is cleared, deviceId rotates to
     * [newDeviceId]. Plugins observe a single bundled identity-change
     * notification rather than two (one for userId, one for deviceId).
     */
    fun resetIdentity(newDeviceId: String) {
        synchronized(lock) {
            state.setUserIdSilent(null)
            state.setDeviceIdSilent(newDeviceId)
            identityManager?.editIdentity()
                ?.setUserId(null)
                ?.setDeviceId(newDeviceId)
                ?.commit()
                ?: run {
                    pendingUserId = Pending(null)
                    pendingDeviceId = Pending(newDeviceId)
                }
        }
        // Dispatch a single bundled notification OUTSIDE the lock.
        state.notifyIdentityChanged(
            EnumSet.of(State.IdentityChangeType.USER_ID, State.IdentityChangeType.DEVICE_ID),
        )
    }

    /**
     * Called during [com.amplitude.core.Amplitude.createIdentityContainer] to reconcile
     * persisted identity with any pre-build changes. User intent wins over persisted values.
     */
    internal fun bootstrap(identityManager: IdentityManager) {
        synchronized(lock) {
            this.identityManager = identityManager
            val persisted = identityManager.getIdentity()

            val userId = pendingUserId.getOrElse(persisted.userId)
            val deviceId = pendingDeviceId.getOrElse(persisted.deviceId)

            // Write silently under the lock; notify after release (same
            // reentrancy contract as setUserId/setDeviceId) so a plugin
            // callback that re-enters setUserId can't be overwritten by the
            // remainder of bootstrap.
            state.setUserIdSilent(userId)
            state.setDeviceIdSilent(deviceId)

            identityManager.editIdentity()
                .setUserId(userId)
                .setDeviceId(deviceId)
                .commit()

            pendingUserId = null
            pendingDeviceId = null
        }
        // Dispatch the bundled notification OUTSIDE the lock.
        state.notifyIdentityChanged(
            EnumSet.of(State.IdentityChangeType.USER_ID, State.IdentityChangeType.DEVICE_ID),
        )
    }
}
