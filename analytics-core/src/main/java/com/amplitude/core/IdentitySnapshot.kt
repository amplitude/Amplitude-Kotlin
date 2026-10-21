package com.amplitude.core

import java.lang.reflect.Array
import java.util.Collections

/** A detached JSON snapshot. Arrays become lists so every supported container is immutable. */
internal class IdentitySnapshot(
    override val userId: String?,
    override val deviceId: String?,
    userProperties: Map<String, Any?>,
) : AnalyticsIdentity {
    override val userProperties: Map<String, Any?> = immutableProperties(userProperties)
}

internal fun immutableProperties(properties: Map<String, Any?>): Map<String, Any?> =
    Collections.unmodifiableMap(properties.mapValues { (_, value) -> immutableValue(value) })

private fun immutableValue(value: Any?): Any? =
    when {
        value is Map<*, *> -> Collections.unmodifiableMap(value.mapValues { (_, item) -> immutableValue(item) })
        value is Collection<*> -> Collections.unmodifiableList(value.map { immutableValue(it) })
        value != null && value.javaClass.isArray ->
            Collections.unmodifiableList((0 until Array.getLength(value)).map { immutableValue(Array.get(value, it)) })
        else -> value
    }
