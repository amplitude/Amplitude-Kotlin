package com.amplitude.core.utilities

import com.amplitude.core.State
import com.amplitude.id.Identity
import com.amplitude.id.IdentityListener
import com.amplitude.id.IdentityUpdateType

@Deprecated(
    message =
        "Identity state is now managed by IdentityCoordinator. " +
            "This listener is no longer functional and will be removed in a future major version.",
)
class AnalyticsIdentityListener(private val state: State) : IdentityListener {
    override fun onUserIdChange(userId: String?) {
        // No-op: State is written directly by Amplitude.setUserId()
    }

    override fun onDeviceIdChange(deviceId: String?) {
        // No-op: State is written directly by Amplitude.setDeviceId()
    }

    override fun onIdentityChanged(
        identity: Identity,
        updateType: IdentityUpdateType,
    ) {
        // No-op: Identity bootstrap is handled directly in Amplitude.createIdentityContainer()
    }
}
