package com.amplitude.android.utilities

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import com.amplitude.core.platform.NetworkConnectivityChecker

class AndroidNetworkConnectivityChecker(private val context: Context) : NetworkConnectivityChecker {

    companion object {
        private const val ACCESS_NETWORK_STATE = "android.permission.ACCESS_NETWORK_STATE"
    }

    override suspend fun isConnected(): Boolean {
        // Assume connection and proceed.
        // Events will be treated like online
        // regardless network connectivity
        if (!hasPermission(context, ACCESS_NETWORK_STATE)) {
            return true
        }

        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = cm.activeNetwork ?: return false
            val capabilities = cm.getNetworkCapabilities(network) ?: return false
            return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } else {
            @SuppressLint("MissingPermission")
            val networkInfo = cm.activeNetworkInfo
            return networkInfo != null && networkInfo.isConnectedOrConnecting
        }
    }

    private fun hasPermission(context: Context, permission: String): Boolean {
        return context.checkCallingOrSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
    }
}
