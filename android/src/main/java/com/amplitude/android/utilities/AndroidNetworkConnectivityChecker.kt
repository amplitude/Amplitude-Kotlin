package com.amplitude.android.utilities

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import com.amplitude.common.Logger

class AndroidNetworkConnectivityChecker(private val context: Context, private val logger: Logger) {
    companion object {
        private const val ACCESS_NETWORK_STATE = "android.permission.ACCESS_NETWORK_STATE"
    }

    private val hasPermission: Boolean
    internal var isMarshmallowAndAbove: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M

    init {
        hasPermission = hasPermission(context, ACCESS_NETWORK_STATE)
        if (!hasPermission) {
            logger.warn(
                @Suppress("ktlint:standard:max-line-length")
                "No ACCESS_NETWORK_STATE permission, offline mode is not supported. To enable, add <uses-permission android:name=\"android.permission.ACCESS_NETWORK_STATE\" /> to your AndroidManifest.xml. Learn more at https://www.docs.developers.amplitude.com/data/sdks/android-kotlin/#offline-mode",
            )
        }
    }

    @SuppressLint("MissingPermission", "NewApi")
    fun isConnected(): Boolean {
        // Assume connection and proceed.
        // Events will be treated like online
        // regardless network connectivity
        if (!hasPermission) {
            return true
        }

        val result = ExceptionUtils.safeInvoke(logger) {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE)
            if (cm is ConnectivityManager) {
                if (isMarshmallowAndAbove) {
                    val network = cm.activeNetwork ?: return@safeInvoke false
                    val capabilities = cm.getNetworkCapabilities(network) ?: return@safeInvoke false

                    return@safeInvoke capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
                } else {
                    @SuppressLint("MissingPermission")
                    val networkInfo = cm.activeNetworkInfo
                    return@safeInvoke networkInfo != null && networkInfo.isConnectedOrConnecting
                }
            } else {
                logger.debug("Service is not an instance of ConnectivityManager. Offline mode is not supported")
                return@safeInvoke true
            }
        }

        // In case of any errors, default to online
        return result ?: true
    }

    private fun hasPermission(
        context: Context,
        permission: String,
    ): Boolean {
        return context.checkCallingOrSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
    }
}
