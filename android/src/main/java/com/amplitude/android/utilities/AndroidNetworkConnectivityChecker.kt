package com.amplitude.android.utilities

import android.Manifest.permission.ACCESS_NETWORK_STATE
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.NetworkCapabilities.TRANSPORT_CELLULAR
import android.net.NetworkCapabilities.TRANSPORT_WIFI
import android.os.Build
import com.amplitude.common.Logger

/**
 * Checks whether the Android device has [TRANSPORT_WIFI] or [TRANSPORT_CELLULAR] capability.
 */
class AndroidNetworkConnectivityChecker(private val context: Context, private val logger: Logger) {
    companion object {
        internal fun hasNetworkPermission(context: Context): Boolean {
            return context.checkCallingOrSelfPermission(ACCESS_NETWORK_STATE) == PERMISSION_GRANTED
        }
    }

    init {
        if (!hasNetworkPermission(context)) {
            logger.warn(
                @Suppress("ktlint:standard:max-line-length")
                "No ACCESS_NETWORK_STATE permission, offline mode is not supported. To enable, add <uses-permission android:name=\"android.permission.ACCESS_NETWORK_STATE\" /> to your AndroidManifest.xml. Learn more at https://www.docs.developers.amplitude.com/data/sdks/android-kotlin/#offline-mode",
            )
        }
    }

    /**
     * Checks whether the device has network transport capabilities (either Wi-Fi or Cellular).
     * @return true if the device has Wi-Fi or Cellular transport, or there is no permission to check network state.
     */
    @SuppressLint("MissingPermission")
    fun isConnected(): Boolean {
        // Assume connection and proceed.
        // Events will be treated like online
        // regardless network connectivity
        if (!hasNetworkPermission(context)) {
            return true
        }

        try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE)
            if (cm is ConnectivityManager) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    val network = cm.activeNetwork ?: return false
                    val capabilities = cm.getNetworkCapabilities(network) ?: return false
                    return hasNetworkTransport(capabilities)
                } else {
                    @Suppress("DEPRECATION")
                    val networks = cm.allNetworks
                    return networks.any { network ->
                        val capabilities = cm.getNetworkCapabilities(network)
                        capabilities != null && hasNetworkTransport(capabilities)
                    }
                }
            } else {
                logger.debug("Service is not an instance of ConnectivityManager. Offline mode is not supported")
                return true
            }
        } catch (throwable: Throwable) {
            // We've seen issues where we see exceptions being thrown by connectivity manager
            // which crashes an app. Its safe to ignore these exceptions since we try our best
            // to mark a device as offline
            // Github Issues:
            // https://github.com/amplitude/Amplitude-Kotlin/issues/220
            // https://github.com/amplitude/Amplitude-Kotlin/issues/197
            logger.warn("Error checking network connectivity: ${throwable.message}")
            logger.warn(throwable.stackTraceToString())
            return true
        }
    }

    private fun hasNetworkTransport(capabilities: NetworkCapabilities): Boolean {
        return capabilities.hasTransport(TRANSPORT_WIFI) ||
            capabilities.hasTransport(TRANSPORT_CELLULAR)
    }
}
