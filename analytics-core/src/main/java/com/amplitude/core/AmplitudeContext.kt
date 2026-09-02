package com.amplitude.core

import com.amplitude.common.Logger
import com.amplitude.core.diagnostics.DiagnosticsClient
import com.amplitude.core.remoteconfig.RemoteConfigClient

/**
 * Shared configuration handed to a [com.amplitude.core.platform.UniversalPlugin] at setup.
 * Lets a plugin read host-level settings such as the API key, instance name, server zone, and
 * logger instead of duplicating them in its own configuration.
 *
 * @property apiKey the host's API key.
 * @property instanceName the host's instance name.
 * @property serverZone the server zone events are sent to.
 * @property logger the host's logger.
 * @property platformContext host-provided platform handle. On Android this is the
 *   application [android.content.Context]; on JVM hosts it is `null`. Typed as [Any] so
 *   `:analytics-core` stays platform-independent. Android plugins should cast:
 *   `amplitudeContext.platformContext as? android.content.Context`.
 */
public open class AmplitudeContext
    @RestrictedAmplitudeFeature
    internal constructor(
        public val apiKey: String,
        public val instanceName: String,
        public val serverZone: ServerZone,
        public val logger: Logger,
        remoteConfigClientProvider: () -> RemoteConfigClient,
        diagnosticsClientProvider: () -> DiagnosticsClient,
        public val platformContext: Any? = null,
    ) {
        @RestrictedAmplitudeFeature
        @JvmOverloads
        public constructor(
            apiKey: String,
            instanceName: String,
            serverZone: ServerZone,
            logger: Logger,
            remoteConfigClient: RemoteConfigClient,
            diagnosticsClient: DiagnosticsClient,
            platformContext: Any? = null,
        ) : this(
            apiKey,
            instanceName,
            serverZone,
            logger,
            { remoteConfigClient },
            { diagnosticsClient },
            platformContext,
        )

        @RestrictedAmplitudeFeature
        public val remoteConfigClient: RemoteConfigClient by lazy(remoteConfigClientProvider)

        @RestrictedAmplitudeFeature
        public val diagnosticsClient: DiagnosticsClient by lazy(diagnosticsClientProvider)
    }
