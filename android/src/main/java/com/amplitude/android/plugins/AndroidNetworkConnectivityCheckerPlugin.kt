package com.amplitude.android.plugins

import AndroidNetworkListener
import com.amplitude.android.Configuration
import com.amplitude.android.utilities.AndroidNetworkConnectivityChecker
import com.amplitude.core.Amplitude
import com.amplitude.core.platform.Plugin
import kotlinx.coroutines.launch

class AndroidNetworkConnectivityCheckerPlugin : Plugin {
    override val type: Plugin.Type = Plugin.Type.Before
    override lateinit var amplitude: Amplitude
    internal lateinit var networkConnectivityChecker: AndroidNetworkConnectivityChecker
    internal lateinit var networkListener: AndroidNetworkListener

    companion object {
        val Disabled = null
    }

    override fun setup(amplitude: Amplitude) {
        super.setup(amplitude)
        amplitude.logger.debug("Installing AndroidNetworkConnectivityPlugin, offline feature should be supported.")
        networkConnectivityChecker = AndroidNetworkConnectivityChecker((amplitude.configuration as Configuration).context, amplitude.logger)
        amplitude.amplitudeScope.launch(amplitude.storageIODispatcher) {
            amplitude.configuration.offline = !networkConnectivityChecker.isConnected()
        }
        val networkChangeHandler =
            object : AndroidNetworkListener.NetworkChangeCallback {
                override fun onNetworkAvailable() {
                    amplitude.logger.debug("AndroidNetworkListener, onNetworkAvailable.")
                    amplitude.configuration.offline = false
                    amplitude.flush()
                }

                override fun onNetworkUnavailable() {
                    amplitude.logger.debug("AndroidNetworkListener, onNetworkUnavailable.")
                    amplitude.configuration.offline = true
                }
            }
        networkListener = AndroidNetworkListener((amplitude.configuration as Configuration).context)
        networkListener.setNetworkChangeCallback(networkChangeHandler)
        networkListener.startListening()
    }

    override fun teardown() {
        networkListener.stopListening()
    }
}
