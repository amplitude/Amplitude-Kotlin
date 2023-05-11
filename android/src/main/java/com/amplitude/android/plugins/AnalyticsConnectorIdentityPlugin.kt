package com.amplitude.android.plugins

import com.amplitude.analytics.connector.AnalyticsConnector
import com.amplitude.analytics.connector.Identity
import com.amplitude.core.Amplitude
import com.amplitude.core.platform.ObservePlugin

internal class AnalyticsConnectorIdentityPlugin : ObservePlugin() {

    override lateinit var amplitude: Amplitude
    private lateinit var connector: AnalyticsConnector

    override fun setup(amplitude: Amplitude) {
        super.setup(amplitude)
        val instanceName = amplitude.configuration.instanceName
        connector = AnalyticsConnector.getInstance(instanceName)
        // Set user ID and device ID in core identity store for use in Experiment SDK
        connector.identityStore.setIdentity(Identity(amplitude.store.userId, amplitude.store.deviceId))
    }

    override fun onUserIdChanged(userId: String?) {
        connector.identityStore.editIdentity().setUserId(userId).commit()
    }

    override fun onDeviceIdChanged(deviceId: String?) {
        connector.identityStore.editIdentity().setDeviceId(deviceId).commit()
    }
}
