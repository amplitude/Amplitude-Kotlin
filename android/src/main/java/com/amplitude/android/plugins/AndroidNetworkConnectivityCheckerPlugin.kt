package com.amplitude.android.plugins

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

    override fun setup(amplitude: Amplitude) {
        super.setup(amplitude)
        networkConnectivityChecker = AndroidNetworkConnectivityChecker((amplitude.configuration as Configuration).context, amplitude.logger)
    }

    override fun execute(event: BaseEvent): BaseEvent? {
        amplitude.amplitudeScope.launch(amplitude.storageIODispatcher) {
            amplitude.configuration.isNetworkConnected = networkConnectivityChecker.isConnected()
        }
        return super.execute(event)
    }
}
