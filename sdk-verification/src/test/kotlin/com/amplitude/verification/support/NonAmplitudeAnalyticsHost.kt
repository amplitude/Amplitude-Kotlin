@file:OptIn(com.amplitude.core.RestrictedAmplitudeFeature::class)

package com.amplitude.verification.support

import com.amplitude.common.Logger
import com.amplitude.core.AmplitudeContext
import com.amplitude.core.AnalyticsClient
import com.amplitude.core.AnalyticsIdentity
import com.amplitude.core.ServerZone
import com.amplitude.core.diagnostics.DiagnosticsClient
import com.amplitude.core.events.BaseEvent
import com.amplitude.core.platform.PluginHost
import com.amplitude.core.platform.UniversalPlugin
import com.amplitude.core.remoteconfig.RemoteConfigClient
import io.mockk.mockk
import java.util.Collections

/** Minimal third-party analytics host that drives only the public UniversalPlugin contract. */
internal class NonAmplitudeAnalyticsHost(
    apiKey: String = "non-amplitude-host-api-key",
    instanceName: String = "non-amplitude-host-${System.nanoTime()}",
    initialUserId: String? = "third-party-user",
    initialDeviceId: String? = "third-party-device",
    initialUserProperties: Map<String, Any?> = emptyMap(),
    initialSessionId: Long = 1_000L,
    initialOptOut: Boolean = false,
    serverZone: ServerZone = ServerZone.US,
) : PluginHost,
    AnalyticsClient {
    private val registeredPlugins = mutableListOf<UniversalPlugin>()
    private val trackedEvents = Collections.synchronizedList(mutableListOf<BaseEvent>())
    private var currentIdentity =
        HostIdentity(
            userId = initialUserId,
            deviceId = initialDeviceId,
            userProperties = initialUserProperties,
        )

    val context =
        AmplitudeContext(
            apiKey = apiKey,
            instanceName = instanceName,
            serverZone = serverZone,
            logger = mockk<Logger>(relaxed = true),
            remoteConfigClient = mockk<RemoteConfigClient>(relaxed = true),
            diagnosticsClient = mockk<DiagnosticsClient>(relaxed = true),
        )

    override val identity: AnalyticsIdentity
        get() = currentIdentity

    override var sessionId: Long = initialSessionId
        private set

    override var optOut: Boolean = initialOptOut
        private set

    fun add(plugin: UniversalPlugin): Boolean {
        val name = plugin.name
        if (name != null && registeredPlugins.any { it.name == name }) return false

        registeredPlugins += plugin
        plugin.setup(this, context)
        return true
    }

    fun remove(plugin: UniversalPlugin): Boolean {
        if (!registeredPlugins.remove(plugin)) return false

        plugin.teardown()
        return true
    }

    override fun plugin(name: String): UniversalPlugin? = registeredPlugins.firstOrNull { it.name == name }

    override fun <T : UniversalPlugin> plugins(clazz: Class<T>): List<T> =
        registeredPlugins.mapNotNull { plugin ->
            if (clazz.isInstance(plugin)) clazz.cast(plugin) else null
        }

    fun updateIdentity(
        userId: String? = currentIdentity.userId,
        deviceId: String? = currentIdentity.deviceId,
        userProperties: Map<String, Any?> = currentIdentity.userProperties,
    ) {
        currentIdentity = HostIdentity(userId, deviceId, userProperties)
        registeredPlugins.toList().forEach { it.onIdentityChanged(currentIdentity) }
    }

    fun updateSessionId(sessionId: Long) {
        this.sessionId = sessionId
        registeredPlugins.toList().forEach { it.onSessionIdChanged(sessionId) }
    }

    fun updateOptOut(optOut: Boolean) {
        this.optOut = optOut
        registeredPlugins.toList().forEach { it.onOptOutChanged(optOut) }
    }

    fun reset(deviceId: String) {
        currentIdentity = HostIdentity(userId = null, deviceId = deviceId)
        registeredPlugins.toList().forEach { plugin ->
            plugin.onIdentityChanged(currentIdentity)
            plugin.onReset()
        }
    }

    fun process(event: BaseEvent): BaseEvent? {
        var currentEvent: BaseEvent? = event
        registeredPlugins.toList().forEach { plugin ->
            currentEvent = currentEvent?.let { plugin.execute(it) }
        }
        currentEvent?.let { trackedEvents += it }
        return currentEvent
    }

    override fun track(
        eventType: String,
        eventProperties: Map<String, Any?>?,
    ) {
        if (optOut) return

        process(
            BaseEvent().apply {
                this.eventType = eventType
                this.eventProperties = eventProperties?.toMutableMap()
                userId = identity.userId
                deviceId = identity.deviceId
                sessionId = this@NonAmplitudeAnalyticsHost.sessionId
            },
        )
    }

    fun events(): List<BaseEvent> = synchronized(trackedEvents) { trackedEvents.toList() }

    fun close() {
        registeredPlugins.toList().forEach(::remove)
    }

    private data class HostIdentity(
        override val userId: String?,
        override val deviceId: String?,
        override val userProperties: Map<String, Any?> = emptyMap(),
    ) : AnalyticsIdentity
}
