package com.amplitude.android.plugins

import com.amplitude.analytics.connector.AnalyticsConnector
import com.amplitude.core.Amplitude
import com.amplitude.core.platform.ObservePlugin
import com.amplitude.analytics.connector.Identity as ConnectorIdentity

internal class AnalyticsConnectorIdentityPlugin : ObservePlugin() {
    override lateinit var amplitude: Amplitude
    private lateinit var connector: AnalyticsConnector

    override fun setup(amplitude: Amplitude) {
        super.setup(amplitude)
        val instanceName = amplitude.configuration.instanceName
        connector = AnalyticsConnector.getInstance(instanceName)
        // Use unified identity access
        val identity = amplitude.store.identity
        connector.identityStore.setIdentity(ConnectorIdentity(identity.userId, identity.deviceId))
    }

    override fun onUserIdChanged(userId: String?) {
        connector.identityStore.editIdentity().setUserId(userId).commit()
    }

    override fun onDeviceIdChanged(deviceId: String?) {
        connector.identityStore.editIdentity().setDeviceId(deviceId).commit()
    }
}
