package com.amplitude.android.plugins

import AndroidNetworkListener
import com.amplitude.android.Configuration
import com.amplitude.android.utilities.AndroidNetworkConnectivityChecker
import com.amplitude.core.Amplitude
import com.amplitude.core.events.BaseEvent
import com.amplitude.core.platform.Plugin
import kotlinx.coroutines.launch

class AndroidNetworkConnectivityCheckerPlugin : Plugin {
    override val type: Plugin.Type = Plugin.Type.Before
    override lateinit var amplitude: Amplitude
    private lateinit var networkConnectivityChecker: AndroidNetworkConnectivityChecker
    private lateinit var networkListener: AndroidNetworkListener
    private val networkChangeHandler =
        object : AndroidNetworkListener.NetworkChangeCallback {
            override fun onNetworkAvailable() {
                println("AndroidNetworkListener, onNetworkAvailable")
                amplitude.configuration.offline = false
                amplitude.flush()
            }

            override fun onNetworkUnavailable() {
                println("AndroidNetworkListener, onNetworkUnavailable")
                amplitude.configuration.offline = true
            }
        }

    override fun setup(amplitude: Amplitude) {
        super.setup(amplitude)
        networkConnectivityChecker = AndroidNetworkConnectivityChecker((amplitude.configuration as Configuration).context, amplitude.logger)
        networkListener = AndroidNetworkListener((amplitude.configuration as Configuration).context)
        networkListener.setNetworkChangeCallback(networkChangeHandler)
        networkListener.startListening()
    }

    override fun execute(event: BaseEvent): BaseEvent? {
        amplitude.amplitudeScope.launch(amplitude.storageIODispatcher) {
            amplitude.configuration.offline = !networkConnectivityChecker.isConnected()
        }
        return super.execute(event)
    }
}
