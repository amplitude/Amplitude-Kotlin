package com.amplitude.android.utilities

import android.Manifest.permission.ACCESS_NETWORK_STATE
import android.annotation.SuppressLint
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET
import android.net.NetworkRequest
import androidx.annotation.VisibleForTesting
import com.amplitude.android.utilities.AndroidNetworkConnectivityChecker.Companion.hasNetworkPermission
import com.amplitude.common.Logger

/**
 * [ACCESS_NETWORK_STATE] permission should be added manually by users to enable this feature.
 */
public class AndroidNetworkListener(
    private val context: Context,
    private val logger: Logger,
    private val networkCallback: NetworkChangeCallback,
) {
    private var registeredNetworkCallback: AndroidNetworkCallback? = null

    public interface NetworkChangeCallback {
        public fun onNetworkAvailable()

        public fun onNetworkUnavailable()
    }

    @SuppressLint("MissingPermission")
    public fun startListening() {
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
        if (registeredNetworkCallback != null) return

        val connectivityManager =
            context
                .getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        // Free the registration slots of callbacks whose owner was garbage collected
        // before consuming a new one.
        AndroidNetworkCallback.unregisterAbandonedCallbacks()
        val networkRequest =
            NetworkRequest.Builder()
                .addCapability(NET_CAPABILITY_INTERNET)
                .build()

        registeredNetworkCallback =
            AndroidNetworkCallback(networkCallback, connectivityManager).also { callback ->
                callback.register(networkRequest)
            }
    }

    public fun stopListening() {
        val callback = registeredNetworkCallback ?: return
        registeredNetworkCallback = null
        try {
            callback.unregister()
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
}
