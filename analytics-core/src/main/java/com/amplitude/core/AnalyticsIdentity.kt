package com.amplitude.core

/**
 * A snapshot of the host's current identity.
 *
 * @property userId the current user id, or `null` if none is set.
 * @property deviceId the current device id, or `null` if none is set.
 * @property userProperties the current user properties, or an empty map when the host does
 * not maintain a user-property map.
 */
public interface AnalyticsIdentity {
    public val userId: String?
    public val deviceId: String?

    public val userProperties: Map<String, Any?>
        get() = emptyMap()
}
