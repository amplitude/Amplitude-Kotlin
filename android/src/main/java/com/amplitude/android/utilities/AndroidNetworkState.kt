package com.amplitude.android.utilities

import android.net.Network
import java.lang.ref.WeakReference

/**
 * AndroidNetworkState is used to track the state of a network connection.
 * It considers the availability and blocked status of the network before notifying the delegate.
 *
 * On initialization and whenever an update toggles the connected state, it notifies the delegate.
 * The delegate is held through a [WeakReference] (shared with [AndroidNetworkCallback]) so this
 * state cannot pin an abandoned Amplitude instance.
 */
internal class AndroidNetworkState(
    private val network: Network,
    private val delegateRef: WeakReference<AndroidNetworkListener.NetworkChangeCallback>,
    private var available: Boolean,
    private var blocked: Boolean,
) {
    init {
        notifyDelegate()
    }

    /**
     * Update the availability/blocked state and notify the delegate if the connected state
     * toggled. Updates for other networks are ignored.
     */
    fun update(
        network: Network,
        available: Boolean = this.available,
        blocked: Boolean = this.blocked,
    ) {
        if (this.network != network) return
        val toggled = this.available != available || this.blocked != blocked
        this.available = available
        this.blocked = blocked
        if (toggled) {
            notifyDelegate()
        }
    }

    private fun notifyDelegate() {
        val delegate = delegateRef.get() ?: return
        if (available && !blocked) {
            delegate.onNetworkAvailable()
        } else {
            delegate.onNetworkUnavailable()
        }
    }
}
