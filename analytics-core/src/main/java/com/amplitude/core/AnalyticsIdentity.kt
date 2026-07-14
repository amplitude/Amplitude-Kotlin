package com.amplitude.core

/**
 * A snapshot of the host's current identity.
 *
 * @property userId the current user id, or `null` if none is set.
 * @property deviceId the current device id, or `null` if none is set.
 * @property userProperties the current user properties, or an empty map when the host does
 * not maintain a user-property map.
 */
interface AnalyticsIdentity {
    val userId: String?
    val deviceId: String?

    val userProperties: Map<String, Any?>
        get() = emptyMap()
}
