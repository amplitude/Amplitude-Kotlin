package com.amplitude.core

import com.amplitude.common.Logger
import com.amplitude.core.diagnostics.DiagnosticsClient
import com.amplitude.core.diagnostics.DiagnosticsClientImpl
import com.amplitude.core.remoteconfig.FileRemoteConfigStorage
import com.amplitude.core.remoteconfig.RemoteConfigClient
import com.amplitude.core.remoteconfig.RemoteConfigClientImpl
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
        private val onClose: () -> Unit = {},
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
         * [storageDirectory] to persist diagnostics and the remote config cache. Both clients
         * share a standalone coroutine scope, so the owner must call [close] when finished.
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
            { resolver.close() },
        ) {
            resolver.context = this
        }

        /**
         * Releases resources owned by this context. A no-op for host-provided contexts; for a
         * standalone context it closes diagnostics (if created) and cancels the shared scope.
         */
        @RestrictedAmplitudeFeature
        public open fun close() {
            onClose()
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

            // Standalone contexts share one storage directory, so partition by project and region too.
            private val storageNamespace = standaloneStorageNamespace(apiKey, serverZone, instanceName)

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
                    remoteConfigStorage =
                        FileRemoteConfigStorage(
                            storageDirectory = storageDirectory,
                            apiKey = apiKey,
                            namespace = storageNamespace,
                            logger = logger,
                        ),
                    httpClient = httpClient,
                    logger = logger,
                )

            private var diagnosticsClient: DiagnosticsClient? = null

            fun diagnostics(): DiagnosticsClient {
                val impl =
                    DiagnosticsClientImpl(
                        apiKey = apiKey,
                        serverZone = serverZone,
                        instanceName = storageNamespace,
                        storageDirectory = storageDirectory,
                        logger = logger,
                        coroutineScope = coroutineScope,
                        networkIODispatcher = Dispatchers.IO,
                        storageIODispatcher = Dispatchers.IO,
                        remoteConfigClient = context.remoteConfigClient,
                        httpClient = httpClient,
                    )
                return impl.also { diagnosticsClient = it }
            }

            fun close() {
                diagnosticsClient?.close()
                coroutineScope.cancel()
            }
        }
    }

/** Storage namespace for a standalone context. Length-prefixed so distinct triples never collide. */
internal fun standaloneStorageNamespace(
    apiKey: String,
    serverZone: ServerZone,
    instanceName: String,
): String = listOf(apiKey, serverZone.name, instanceName).joinToString("") { "${it.length}:$it" }
