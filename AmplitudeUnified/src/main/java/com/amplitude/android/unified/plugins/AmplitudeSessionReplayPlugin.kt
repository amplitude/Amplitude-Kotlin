package com.amplitude.android.unified.plugins

import android.content.Context
import com.amplitude.android.sessionreplay.SessionReplay
import com.amplitude.core.context.AmplitudeContext
import com.amplitude.core.platform.plugins.AnalyticsClient
import com.amplitude.core.platform.plugins.UniversalPlugin

class AmplitudeSessionReplayPlugin(
    private val context: Context,
) : UniversalPlugin {
    companion object {
        const val PLUGIN_NAME = "com.amplitude.android.sessionreplay"
    }

    override val name: String = PLUGIN_NAME
    private var sessionReplay: SessionReplay? = null

    override fun setup(
        analyticsClient: AnalyticsClient,
        amplitudeContext: AmplitudeContext,
    ) {
        super.setup(analyticsClient, amplitudeContext)
        sessionReplay?.stop()
        sessionReplay =
            SessionReplay(
                apiKey = amplitudeContext.apiKey,
                context = context,
                deviceId = analyticsClient.identity.deviceId.orEmpty(),
                sessionId = analyticsClient.sessionId,
                logger = amplitudeContext.logger,
                serverZone = amplitudeContext.serverZone,
            )
    }
}
