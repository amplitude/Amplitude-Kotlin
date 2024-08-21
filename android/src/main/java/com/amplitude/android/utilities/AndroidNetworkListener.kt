package com.amplitude.android.utilities

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import com.amplitude.common.Logger

class AndroidNetworkListener(private val context: Context, private val logger: Logger) {
    private var networkCallback: NetworkChangeCallback? = null
    private var networkCallbackForLowerApiLevels: BroadcastReceiver? = null
    private var networkCallbackForHigherApiLevels: ConnectivityManager.NetworkCallback? = null

    interface NetworkChangeCallback {
        fun onNetworkAvailable()

        fun onNetworkUnavailable()
    }

    fun setNetworkChangeCallback(callback: NetworkChangeCallback) {
        this.networkCallback = callback
    }

    fun startListening() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                setupNetworkCallback()
            } else {
                setupBroadcastReceiver()
            }
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

    @SuppressLint("NewApi", "MissingPermission")
    // startListening() checks API level
    // ACCESS_NETWORK_STATE permission should be added manually by users to enable this feature
    private fun setupNetworkCallback() {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val networkRequest =
            NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()

        networkCallbackForHigherApiLevels =
            object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    networkCallback?.onNetworkAvailable()
                }

                override fun onLost(network: Network) {
                    networkCallback?.onNetworkUnavailable()
                }
            }

        connectivityManager.registerNetworkCallback(networkRequest, networkCallbackForHigherApiLevels!!)
    }

    private fun setupBroadcastReceiver() {
        networkCallbackForLowerApiLevels =
            object : BroadcastReceiver() {
                @SuppressLint("MissingPermission")
                override fun onReceive(
                    context: Context,
                    intent: Intent,
                ) {
                    if (ConnectivityManager.CONNECTIVITY_ACTION == intent.action) {
                        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                        val activeNetwork = connectivityManager.activeNetworkInfo
                        val isConnected = activeNetwork?.isConnectedOrConnecting == true

                        if (isConnected) {
                            networkCallback?.onNetworkAvailable()
                        } else {
                            networkCallback?.onNetworkUnavailable()
                        }
                    }
                }
            }

        val filter = IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION)
        context.registerReceiver(networkCallbackForLowerApiLevels, filter)
    }

    fun stopListening() {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                    networkCallbackForHigherApiLevels?.let { connectivityManager.unregisterNetworkCallback(it) }
                } else {
                    networkCallbackForLowerApiLevels?.let { context.unregisterReceiver(it) }
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

}
