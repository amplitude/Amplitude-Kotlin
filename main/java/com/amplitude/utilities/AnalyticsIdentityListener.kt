package com.amplitude.utilities

import com.amplitude.Identity
import com.amplitude.IdentityListener
import com.amplitude.IdentityUpdateType
import com.amplitude.State

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