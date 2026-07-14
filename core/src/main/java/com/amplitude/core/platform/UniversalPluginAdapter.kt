package com.amplitude.core.platform

import com.amplitude.core.Amplitude
import com.amplitude.core.AmplitudeContext
import com.amplitude.core.AnalyticsClient
import com.amplitude.core.AnalyticsIdentity
import com.amplitude.core.events.BaseEvent

/** Adapts a [UniversalPlugin] so it can be hosted by [Amplitude] on the standard [Plugin] timeline. */
internal class UniversalPluginAdapter(val delegate: UniversalPlugin) : Plugin {
    override val type: Plugin.Type = Plugin.Type.Enrichment
    override lateinit var amplitude: Amplitude
    override val name: String? get() = delegate.name

    override fun setup(
        client: AnalyticsClient,
        context: AmplitudeContext,
    ) {
        delegate.setup(client, context)
    }

    override fun execute(event: BaseEvent): BaseEvent? = delegate.execute(event)

    override fun teardown() = delegate.teardown()

    override fun onIdentityChanged(identity: AnalyticsIdentity) = delegate.onIdentityChanged(identity)

    override fun onSessionIdChanged(sessionId: Long) = delegate.onSessionIdChanged(sessionId)

    override fun onOptOutChanged(optOut: Boolean) = delegate.onOptOutChanged(optOut)

    override fun onReset() = delegate.onReset()
}
