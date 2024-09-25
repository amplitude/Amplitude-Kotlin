package com.amplitude.android.migration

import com.amplitude.common.Logger
import com.amplitude.id.IdentityStorage

class IdentityStorageMigration(
    private val source: IdentityStorage,
    private val destination: IdentityStorage,
    private val logger: Logger
) {
    fun execute() {
        try {
            val identity = source.load()
            logger.debug("Loaded old identity: $identity")
            if (identity.userId != null) {
                destination.saveUserId(identity.userId)
            }
            if (identity.deviceId != null) {
                destination.saveDeviceId(identity.deviceId)
            }
            source.delete()
        } catch (e: Exception) {
            logger.error("Unable to migrate file identity storage: ${e.message}")
        }
    }
}