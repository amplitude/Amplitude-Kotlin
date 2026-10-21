package com.amplitude.core

/**
 * A snapshot of the host's current identity.
 *
 * @property userId the current user id, or `null` if none is set.
 * @property deviceId the current device id, or `null` if none is set.
 * @property userProperties locally known user properties. Hosts must maintain this map; an
 * empty map means no properties. Amplitude keeps these in memory for this instance, applying `$set`,
 * `$unset`, `$clearAll`, and plain property values before processing Identify events. User
 * properties on other events and other Identify operations are sent to the server without
 * changing this map. Properties survive user-id changes and are cleared by reset; they are
 * not restored from the server or persisted.
 */
public interface AnalyticsIdentity {
    public val userId: String?
    public val deviceId: String?

    public val userProperties: Map<String, Any?>
        get() = emptyMap()
}
