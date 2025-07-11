package com.amplitude.android.utilities

import android.Manifest.permission.ACCESS_NETWORK_STATE
import android.annotation.SuppressLint
import android.content.Context
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
import androidx.annotation.VisibleForTesting
import com.amplitude.android.utilities.AndroidNetworkConnectivityChecker.Companion.hasNetworkPermission
import com.amplitude.common.Logger

/**
 * [ACCESS_NETWORK_STATE] permission should be added manually by users to enable this feature.
 */
class AndroidNetworkListener(
    private val context: Context,
    private val logger: Logger,
    private val networkCallback: NetworkChangeCallback,
) {
    private var networkCallbackForApiLevel21Plus: NetworkCallback? = null

    interface NetworkChangeCallback {
        fun onNetworkAvailable()

        fun onNetworkUnavailable()
    }

    @SuppressLint("MissingPermission")
    fun startListening() {
        if (!hasNetworkPermission(context)) {
            logger.debug(
                "ACCESS_NETWORK_STATE permission not granted, skipping network listener setup",
            )
            return
        }

        try {
            setupNetworkCallback()
        } catch (throwable: Throwable) {
            // We've seen issues where we see exceptions being thrown by connectivity manager
            // which crashes an app. Its safe to ignore these exceptions since we try our best
            // to mark a device as offline
            // Github Issues:
            // https://github.com/amplitude/Amplitude-Kotlin/issues/220
            // https://github.com/amplitude/Amplitude-Kotlin/issues/197
            logger.warn("Error starting network listener: ${throwable.message}")
        }
    }

    @SuppressLint("MissingPermission")
    @VisibleForTesting
    internal fun setupNetworkCallback() {
        val connectivityManager =
            context
                .getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val networkRequest =
            NetworkRequest.Builder()
                .addCapability(NET_CAPABILITY_INTERNET)
                .build()

        networkCallbackForApiLevel21Plus =
            object : NetworkCallback() {
                private var networkState: NetworkState? = null

                @RequiresPermission(ACCESS_NETWORK_STATE)
                override fun onAvailable(network: Network) {
                    // A default network is available, set the network state and callback
                    val capabilities = connectivityManager.getNetworkCapabilities(network)
                    networkState =
                        NetworkState(
                            network = network,
                            networkCallback = networkCallback,
                            available = capabilities?.available() ?: true,
                            blocked = false,
                        )
                }

                override fun onUnavailable() {
                    // no network is found
                    networkCallback.onNetworkUnavailable()
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

                // Best attempt to check if the network is available
                private fun NetworkCapabilities.available(): Boolean {
                    val validated =
                        if (VERSION.SDK_INT >= VERSION_CODES.M) {
                            hasCapability(NET_CAPABILITY_VALIDATED)
                        } else {
                            true
                        }
                    return hasCapability(NET_CAPABILITY_INTERNET) && validated
                }
            }.also { callback ->
                connectivityManager.registerNetworkCallback(
                    networkRequest,
                    callback,
                )
            }
    }

    fun stopListening() {
        try {
            val connectivityManager =
                context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            networkCallbackForApiLevel21Plus?.let {
                connectivityManager.unregisterNetworkCallback(it)
            }
        } catch (e: IllegalArgumentException) {
            // callback was already unregistered.
        } catch (e: IllegalStateException) {
            // shutdown process is in progress and certain operations are not allowed.
        } catch (throwable: Throwable) {
            // We've seen issues where we see exceptions being thrown by connectivity manager
            // which crashes an app. Its safe to ignore these exceptions since we try our best
            // to mark a device as offline
            // Github Issues:
            // https://github.com/amplitude/Amplitude-Kotlin/issues/220
            // https://github.com/amplitude/Amplitude-Kotlin/issues/197
            logger.warn("Error stopping network listener: ${throwable.message}")
        }
    }

    /**
     * NetworkState is used to track the state of a network connection.
     * It considers the availability and blocked status of the network before notifying the callback.
     *
     * On initialization, it checks if the network is available and not blocked.
     */
    private class NetworkState(
        private val network: Network,
        private val networkCallback: NetworkChangeCallback,
        private var available: Boolean,
        private var blocked: Boolean,
    ) {
        init {
            notifyNetworkCallback()
        }

        /**
         * Notify the network callback based on the current state of the network.
         * Ideally called only when the state changes (e.g. initialized, available or blocked toggled).
         */
        private fun notifyNetworkCallback() {
            if (available && !blocked) {
                networkCallback.onNetworkAvailable()
            } else {
                networkCallback.onNetworkUnavailable()
            }
        }

        /**
         * Update the availability/blocked state and notify the callback if necessary.
         * Checks if we're on the same network, else just ignore.
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
                notifyNetworkCallback()
            }
        }
    }
}
