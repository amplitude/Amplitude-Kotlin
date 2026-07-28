package com.amplitude.android.utilities

import android.Manifest.permission.ACCESS_NETWORK_STATE
import android.net.ConnectivityManager
import android.net.ConnectivityManager.NetworkCallback
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET
import android.net.NetworkCapabilities.NET_CAPABILITY_VALIDATED
import android.net.NetworkRequest
import android.os.Build.VERSION
import android.os.Build.VERSION_CODES
import androidx.annotation.RequiresPermission
import java.lang.ref.WeakReference
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Bridges [ConnectivityManager] network callbacks to a [AndroidNetworkListener.NetworkChangeCallback]
 * held through a [WeakReference]. The system keeps registered callbacks strongly reachable, so a
 * strong reference here would pin the delegate — and the Amplitude instance behind it — after the
 * host app drops the instance without calling [AndroidNetworkListener.stopListening].
 *
 * The system also limits an app to 100 outstanding callbacks, so a callback whose delegate has
 * been garbage collected frees its registration slot: on the next network event it receives, or
 * when any new [AndroidNetworkListener] starts listening.
 */
internal class AndroidNetworkCallback(
    delegate: AndroidNetworkListener.NetworkChangeCallback,
    private val connectivityManager: ConnectivityManager,
) : NetworkCallback() {
    private val delegateRef = WeakReference(delegate)
    private var _networkState: AndroidNetworkState? = null

    /**
     * Returns the tracked network state, or null if no network is tracked yet or the delegate
     * was garbage collected (in which case this callback's registration is released).
     */
    private val networkState: AndroidNetworkState?
        get() = networkChangeCallback?.let { _networkState }

    /**
     * The delegate, or null — after releasing this callback's registration — if the delegate,
     * along with the Amplitude instance that owned it, was garbage collected.
     */
    private val networkChangeCallback: AndroidNetworkListener.NetworkChangeCallback?
        get() =
            delegateRef.get() ?: run {
                safeUnregister()
                null
            }

    /** Registers this callback with [ConnectivityManager] and starts tracking it. */
    fun register(networkRequest: NetworkRequest) {
        connectivityManager.registerNetworkCallback(networkRequest, this)
        registeredCallbacks.add(this)
    }

    @RequiresPermission(ACCESS_NETWORK_STATE)
    override fun onAvailable(network: Network) {
        networkChangeCallback ?: return

        // A default network is available; the new state notifies the delegate on creation
        val capabilities = connectivityManager.getNetworkCapabilities(network)
        _networkState =
            AndroidNetworkState(
                network = network,
                delegateRef = delegateRef,
                available = capabilities?.available() ?: true,
                blocked = false,
            )
    }

    override fun onUnavailable() {
        // no network is found
        networkChangeCallback?.onNetworkUnavailable()
    }

    override fun onLost(network: Network) {
        networkState?.update(network, available = false)
    }

    override fun onCapabilitiesChanged(
        network: Network,
        networkCapabilities: NetworkCapabilities,
    ) {
        networkState?.update(network, available = networkCapabilities.available())
    }

    override fun onBlockedStatusChanged(
        network: Network,
        blocked: Boolean,
    ) {
        networkState?.update(network, blocked = blocked)
    }

    /** Unregisters this callback from [ConnectivityManager] and stops tracking it. */
    fun unregister() {
        registeredCallbacks.remove(this)
        connectivityManager.unregisterNetworkCallback(this)
    }

    /** Unregisters, ignoring failures: already unregistered, or the system disallows it right now. */
    private fun safeUnregister() =
        runCatching {
            unregister()
        }

    // Best attempt to check if the network is available
    private fun NetworkCapabilities.available(): Boolean {
        val validated =
            if (VERSION.SDK_INT >= VERSION_CODES.M) {
                hasCapability(NET_CAPABILITY_VALIDATED)
            } else {
                true
            }
        return validated && hasCapability(NET_CAPABILITY_INTERNET)
    }

    companion object {
        /**
         * Callbacks currently registered with [ConnectivityManager] by this process.
         * Tracked so that registrations whose delegate was garbage collected can be swept
         * eagerly on new registrations, instead of waiting for a network event to reach them.
         */
        private val registeredCallbacks = CopyOnWriteArrayList<AndroidNetworkCallback>()

        /** Unregisters callbacks whose delegate has been garbage collected. */
        fun unregisterAbandonedCallbacks() {
            registeredCallbacks
                .filter { it.delegateRef.get() == null }
                .forEach {
                    it.safeUnregister()
                }
        }
    }
}
