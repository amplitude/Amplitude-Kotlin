package com.amplitude.android.plugins

import com.amplitude.analytics.connector.AnalyticsConnector
import com.amplitude.analytics.connector.Identity
import com.amplitude.core.Amplitude
import com.amplitude.core.platform.ObservePlugin
import com.amplitude.core.platform.plugins.AnalyticsIdentity

internal class AnalyticsConnectorIdentityPlugin : ObservePlugin() {
    override lateinit var amplitude: Amplitude
    private lateinit var connector: AnalyticsConnector

    override fun setup(amplitude: Amplitude) {
        super.setup(amplitude)
        connector = AnalyticsConnector.getInstance(amplitude.configuration.instanceName)
        // Set user ID and device ID in core identity store for use in Experiment SDK
        val identify = amplitude.identity
        connector.identityStore.setIdentity(
            Identity(
                identify.userId,
                identify.deviceId,
            ),
        )
    }

    override fun onIdentityChanged(identity: AnalyticsIdentity) {
        connector.identityStore.editIdentity()
            .setUserId(identity.userId)
            .setDeviceId(identity.deviceId)
            .commit()
    }
}
