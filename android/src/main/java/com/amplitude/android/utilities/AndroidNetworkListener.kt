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
import java.lang.IllegalArgumentException

class AndroidNetworkListener(private val context: Context) {
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            setupNetworkCallback()
        } else {
            setupBroadcastReceiver()
        }
    }

    @SuppressLint("NewApi")
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
        }
    }
}
