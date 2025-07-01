package com.amplitude.core.platform.plugins

import com.amplitude.core.context.AmplitudeContext

interface UniversalPlugin {
    val name: String?

    fun setup(
        analyticsClient: AnalyticsClient,
        amplitudeContext: AmplitudeContext,
    ) {}

    fun execute(event: AnalyticsEvent) {}

    fun teardown() {
        // Clean up any resources from setup if necessary
    }

    fun onIdentityChanged(identity: AnalyticsIdentity) {}

    fun onSessionIdChanged(sessionId: Long) {}

    fun onOptOutChanged(optOut: Boolean) {}
}
