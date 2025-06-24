package com.amplitude.core.platform.plugins

import com.amplitude.core.context.AmplitudeContext

interface UniversalPlugin {
    val name: String?

    fun setup(
        analyticsClient: AnalyticsClient<AnalyticsIdentity>,
        amplitudeContext: AmplitudeContext
    ) {}

    fun <Event : AnalyticsEvent> execute(event: Event) {}
    fun teardown() {}

    fun onIdentityChanged(identity: AnalyticsIdentity) {}
    fun onSessionIdChanged(sessionId: Long) {}
    fun onOptOutChanged(optOut: Boolean) {}
}