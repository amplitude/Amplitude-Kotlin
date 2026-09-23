package com.amplitude.core

import com.amplitude.common.Logger
import com.amplitude.core.diagnostics.DiagnosticsClient
import com.amplitude.core.diagnostics.DiagnosticsClientImpl
import com.amplitude.core.remoteconfig.RemoteConfigClient
import com.amplitude.core.remoteconfig.RemoteConfigClientImpl
import com.amplitude.core.utilities.InMemoryStorage
import com.amplitude.core.utilities.http.HttpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import java.io.File

/**
 * Shared configuration handed to a [com.amplitude.core.platform.UniversalPlugin] at setup.
 * Lets a plugin read host-level settings such as the API key, instance name, server zone, and
 * logger instead of duplicating them in its own configuration.
 *
 * @property apiKey the host's API key.
 * @property instanceName the host's instance name.
 * @property serverZone the server zone events are sent to.
 * @property logger the host's logger.
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
    ) {
        /**
         * Creates a context with host-provided [remoteConfigClient] and [diagnosticsClient].
         */
        @RestrictedAmplitudeFeature
        public constructor(
            apiKey: String,
            instanceName: String,
            serverZone: ServerZone,
            logger: Logger,
            remoteConfigClient: RemoteConfigClient,
            diagnosticsClient: DiagnosticsClient,
        ) : this(
            apiKey,
            instanceName,
            serverZone,
            logger,
            { remoteConfigClient },
            { diagnosticsClient },
        )

        /**
         * Creates a context for use outside an [Amplitude] instance.
         *
         * [remoteConfigClient] and [diagnosticsClient] are created on first access, using
         * [storageDirectory] for diagnostics persistence. The diagnostics client owns a
         * standalone coroutine scope that is cancelled by [DiagnosticsClient.close].
         *
         * ```
         * val context = AmplitudeContext(
         *     apiKey = apiKey,
         *     instanceName = "standalone",
         *     serverZone = ServerZone.US,
         *     logger = logger,
         *     storageDirectory = filesDir,
         * )
         * ```
         */
        @RestrictedAmplitudeFeature
        public constructor(
            apiKey: String,
            instanceName: String,
            serverZone: ServerZone,
            logger: Logger,
            storageDirectory: File,
        ) : this(
            apiKey,
            instanceName,
            serverZone,
            logger,
            ClientResolver(
                apiKey = apiKey,
                instanceName = instanceName,
                serverZone = serverZone,
                logger = logger,
                storageDirectory = storageDirectory,
            ),
        )

        @OptIn(RestrictedAmplitudeFeature::class)
        private constructor(
            apiKey: String,
            instanceName: String,
            serverZone: ServerZone,
            logger: Logger,
            resolver: ClientResolver,
        ) : this(
            apiKey,
            instanceName,
            serverZone,
            logger,
            { resolver.remoteConfig() },
            { resolver.diagnostics() },
        ) {
            resolver.context = this
        }

        @RestrictedAmplitudeFeature
        public val remoteConfigClient: RemoteConfigClient by lazy(remoteConfigClientProvider)

        @RestrictedAmplitudeFeature
        public val diagnosticsClient: DiagnosticsClient by lazy(diagnosticsClientProvider)

        @OptIn(RestrictedAmplitudeFeature::class)
        private class ClientResolver(
            private val apiKey: String,
            private val instanceName: String,
            private val serverZone: ServerZone,
            private val logger: Logger,
            private val storageDirectory: File,
        ) {
            lateinit var context: AmplitudeContext

            private val coroutineScope by lazy {
                CoroutineScope(SupervisorJob() + Dispatchers.IO)
            }

            private val httpClient by lazy {
                HttpClient(
                    Configuration(
                        apiKey = apiKey,
                        instanceName = instanceName,
                        serverZone = serverZone,
                    ),
                    logger,
                )
            }

            fun remoteConfig(): RemoteConfigClient =
                RemoteConfigClientImpl(
                    apiKey = apiKey,
                    serverZone = serverZone,
                    coroutineScope = coroutineScope,
                    networkIODispatcher = Dispatchers.IO,
                    storageIODispatcher = Dispatchers.IO,
                    storage = InMemoryStorage(),
                    httpClient = httpClient,
                    logger = logger,
                )

            fun diagnostics(): DiagnosticsClient {
                val ownedScope = coroutineScope
                val impl =
                    DiagnosticsClientImpl(
                        apiKey = apiKey,
                        serverZone = serverZone,
                        instanceName = instanceName,
                        storageDirectory = storageDirectory,
                        logger = logger,
                        coroutineScope = ownedScope,
                        networkIODispatcher = Dispatchers.IO,
                        storageIODispatcher = Dispatchers.IO,
                        remoteConfigClient = context.remoteConfigClient,
                        httpClient = httpClient,
                    )
                return object : DiagnosticsClient by impl {
                    override fun close() {
                        impl.close()
                        ownedScope.cancel()
                    }
                }
            }
        }
    }
