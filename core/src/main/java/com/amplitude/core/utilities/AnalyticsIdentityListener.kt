package com.amplitude.core.utilities

import com.amplitude.core.State
import com.amplitude.id.Identity
import com.amplitude.id.IdentityListener
import com.amplitude.id.IdentityUpdateType

class AnalyticsIdentityListener(private val state: State) : IdentityListener {

    override fun onUserIdChange(userId: String?) {
        state.userId = userId
    }

    override fun onDeviceIdChange(deviceId: String?) {
        state.deviceId = deviceId
    }

    override fun onIdentityChanged(identity: Identity, updateType: IdentityUpdateType) {
        if (updateType == IdentityUpdateType.Initialized) {
            state.userId = identity.userId
            state.deviceId = identity.deviceId
            // TODO("update device id based on configuration")
        }
    }
}
