package com.amplitude.android

import android.app.Application
import android.content.Context
import com.amplitude.analytics.connector.AnalyticsConnector
import com.amplitude.android.diagnostics.AndroidDiagnosticsContextProvider
import com.amplitude.android.migration.MigrationManager
import com.amplitude.android.plugins.AnalyticsConnectorIdentityPlugin
import com.amplitude.android.plugins.AnalyticsConnectorPlugin
import com.amplitude.android.plugins.AndroidContextPlugin
import com.amplitude.android.plugins.AndroidLifecyclePlugin
import com.amplitude.android.plugins.AndroidNetworkConnectivityCheckerPlugin
import com.amplitude.android.storage.AndroidStorageContextV3
import com.amplitude.android.utilities.ActivityLifecycleObserver
import com.amplitude.core.RestrictedAmplitudeFeature
import com.amplitude.core.State
import com.amplitude.core.diagnostics.DiagnosticsContextProvider
import com.amplitude.core.platform.UniversalPlugin
import com.amplitude.core.platform.plugins.AmplitudeDestination
import com.amplitude.core.platform.plugins.ContextPlugin
import com.amplitude.core.platform.plugins.GetAmpliExtrasPlugin
import com.amplitude.id.IdentityConfiguration
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import com.amplitude.core.Amplitude as CoreAmplitude

@OptIn(RestrictedAmplitudeFeature::class)
open class Amplitude internal constructor(
    configuration: Configuration,
    state: State,
    amplitudeScope: CoroutineScope = CoroutineScope(SupervisorJob()),
    amplitudeDispatcher: CoroutineDispatcher = Executors.newCachedThreadPool().asCoroutineDispatcher(),
    networkIODispatcher: CoroutineDispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher(),
    storageIODispatcher: CoroutineDispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher(),
) : CoreAmplitude(
        configuration = configuration,
        store = state,
        amplitudeScope = amplitudeScope,
        amplitudeDispatcher = amplitudeDispatcher,
        networkIODispatcher = networkIODispatcher,
        storageIODispatcher = storageIODispatcher,
    ) {
    constructor(configuration: Configuration) : this(configuration, State())

    override val sessionId: Long
        get() {
            return (timeline as Timeline).sessionId
        }

    private lateinit var activityLifecycleCallbacks: ActivityLifecycleObserver

    private lateinit var androidContextPlugin: AndroidContextPlugin

    internal lateinit var autocaptureManager: AutocaptureManager
        private set

    internal lateinit var replacementInstanceName: String
        private set

    internal lateinit var replacementApplication: Application
        private set
    private lateinit var replacementBuildGate: CompletableDeferred<Unit>
    private val replacementLifecycleLock = Any()
    private val active = AtomicBoolean(true)
    private val cleanupStarted = AtomicBoolean(false)
    private var lifecycleCallbacksRegistered = false
    private var shutdownHook: Thread? = null
    private var androidLifecyclePlugin: AndroidLifecyclePlugin? = null

    /**
     * Init-order trap: [CoreAmplitude]'s `init` calls [build] before this class's field
     * initializers run, and [buildInternal] may already be on a background thread.
     * Assign anything [buildInternal] or its plugins read here — never `by lazy`, field
     * initializers, or `init {}`.
     */
    override fun build(): Deferred<Boolean> {
        activityLifecycleCallbacks = ActivityLifecycleObserver()
        androidContextPlugin = AndroidContextPlugin()
        val androidConfig = configuration as Configuration
        replacementApplication = androidConfig.context as Application
        replacementInstanceName = androidConfig.instanceName
        replacementBuildGate = CompletableDeferred()
        autocaptureManager =
            AutocaptureManager(
                initialAutocapture = androidConfig.autocapture,
                initialInteractionsOptions = androidConfig.interactionsOptions,
                remoteConfigClient = if (androidConfig.enableAutocaptureRemoteConfig) remoteConfigClient else null,
                logger = logger,
                diagnosticsClient = diagnosticsClient,
            )
        val build = super.build()
        return amplitudeScope.async(amplitudeDispatcher, CoroutineStart.LAZY) {
            replacementBuildGate.await()
            build.await()
        }
    }

    init {
        try {
            ActiveAmplitudeInstances.install(this)
        } finally {
            if (active.get()) {
                replacementBuildGate.complete(Unit)
            } else {
                replacementBuildGate.cancel()
            }
        }
    }

    override fun createTimeline(): Timeline {
        return Timeline(configuration.sessionId).also { it.amplitude = this }
    }

    override fun createIdentityConfiguration(): IdentityConfiguration {
        val configuration = configuration as Configuration

        return IdentityConfiguration(
            instanceName = configuration.instanceName,
            apiKey = configuration.apiKey,
            identityStorageProvider = configuration.identityStorageProvider,
            storageDirectory = AndroidStorageContextV3.getIdentityStorageDirectory(configuration),
            logger = configuration.loggerProvider.getLogger(this),
            fileName = AndroidStorageContextV3.getIdentityStorageFileName(),
        )
    }

    override suspend fun buildInternal(identityConfiguration: IdentityConfiguration) {
        val migrationManager = MigrationManager(this)
        migrationManager.migrateOldStorage()

        synchronized(replacementLifecycleLock) {
            if (!active.get()) {
                return
            }

            this.createIdentityContainer(identityConfiguration)

            if (this.configuration.offline != AndroidNetworkConnectivityCheckerPlugin.Disabled) {
                add(AndroidNetworkConnectivityCheckerPlugin())
            }
            add(androidContextPlugin)
            add(GetAmpliExtrasPlugin())
            val lifecyclePlugin = AndroidLifecyclePlugin(activityLifecycleCallbacks)
            add(lifecyclePlugin)
            androidLifecyclePlugin = lifecyclePlugin
            add(AnalyticsConnectorIdentityPlugin())
            add(AnalyticsConnectorPlugin())
            add(AmplitudeDestination())

            (timeline as Timeline).start()
        }
    }

    override fun diagnosticsContextProvider(): DiagnosticsContextProvider? {
        val configuration = configuration as Configuration
        return AndroidDiagnosticsContextProvider(configuration.context)
    }

    override fun diagnosticsStorageDirectory(): File {
        val configuration = configuration as Configuration
        return configuration.getStorageDirectory()
    }

    override fun reset(): Amplitude {
        val newDeviceId =
            if (isBuilt.isCompleted) {
                androidContextPlugin.resolveDeviceId(configuration as Configuration, forceRegenerate = true)
                    ?: ContextPlugin.generateRandomDeviceId()
            } else {
                ContextPlugin.generateRandomDeviceId()
            }
        doResetWithDeviceId(newDeviceId)
        return this
    }

    internal fun notifySessionIdChanged(sessionId: Long) {
        notifyAllPlugins { it.onSessionIdChanged(sessionId) }
    }

    @GuardedAmplitudeFeature
    fun onEnterForeground(timestamp: Long) {
        (timeline as Timeline).onEnterForeground(timestamp)
    }

    @GuardedAmplitudeFeature
    fun onExitForeground(timestamp: Long) {
        (timeline as Timeline).onExitForeground(timestamp)
    }

    internal fun activateForReplacement() {
        synchronized(replacementLifecycleLock) {
            check(active.get()) { "Cannot activate a retired Amplitude instance." }
            try {
                replacementApplication.registerActivityLifecycleCallbacks(activityLifecycleCallbacks)
                lifecycleCallbacksRegistered = true
                registerShutdownHook()
            } catch (e: Exception) {
                active.set(false)
                cleanupSafely("roll back lifecycle callback registration") {
                    unregisterLifecycleCallbacks()
                }
                cleanupSafely("roll back the runtime shutdown hook") {
                    removeShutdownHook()
                }
                cleanupSafely("stop lifecycle observation") {
                    activityLifecycleCallbacks.stop()
                }
                cleanupSafely("stop event processing") {
                    (timeline as Timeline).stop()
                }
                amplitudeScope.cancel()
                throw e
            }
        }
    }

    internal fun deactivateForReplacement() {
        synchronized(replacementLifecycleLock) {
            if (!active.compareAndSet(true, false)) {
                return
            }

            cleanupSafely("unregister lifecycle callbacks") {
                unregisterLifecycleCallbacks()
            }
            cleanupSafely("stop lifecycle observation") {
                activityLifecycleCallbacks.stop()
            }
            cleanupSafely("stop Android autocapture") {
                androidLifecyclePlugin?.stopAutocapture()
            }
            cleanupSafely("stop event processing") {
                (timeline as Timeline).stop()
            }
            cleanupSafely("detach Analytics Connector") {
                AnalyticsConnector.getInstance(replacementInstanceName).eventBridge.setEventReceiver(null)
            }
            cleanupSafely("remove the runtime shutdown hook") {
                removeShutdownHook()
            }
            amplitudeScope.cancel()
        }
    }

    internal fun finishReplacementCleanup() {
        if (!cleanupStarted.compareAndSet(false, true)) {
            return
        }

        plugins(UniversalPlugin::class.java).forEach { plugin ->
            cleanupSafely("tear down plugin '${plugin.name ?: plugin::class.java.name}'") {
                plugin.teardown()
            }
        }

        setOf(amplitudeDispatcher, networkIODispatcher, storageIODispatcher).forEach { dispatcher ->
            cleanupSafely("close an SDK dispatcher") {
                (dispatcher as? ExecutorCoroutineDispatcher)?.close()
            }
        }
    }

    internal fun isActiveForReplacement(): Boolean = active.get()

    private fun registerShutdownHook() {
        val hook =
            object : Thread() {
                override fun run() {
                    (this@Amplitude.timeline as Timeline).stop()
                }
            }
        try {
            Runtime.getRuntime().addShutdownHook(hook)
            shutdownHook = hook
        } catch (e: IllegalStateException) {
            // Once the shutdown sequence has begun it is impossible to register a shutdown hook,
            // so we just ignore the IllegalStateException that's thrown.
            // https://developer.android.com/reference/java/lang/Runtime#addShutdownHook(java.lang.Thread)
        }
    }

    private fun removeShutdownHook() {
        val hook = shutdownHook ?: return
        shutdownHook = null
        try {
            Runtime.getRuntime().removeShutdownHook(hook)
        } catch (_: IllegalStateException) {
            // The runtime is already shutting down.
        } catch (_: SecurityException) {
            // The runtime does not allow shutdown-hook removal.
        }
    }

    private fun unregisterLifecycleCallbacks() {
        if (!lifecycleCallbacksRegistered) {
            return
        }
        replacementApplication.unregisterActivityLifecycleCallbacks(activityLifecycleCallbacks)
        lifecycleCallbacksRegistered = false
    }

    private inline fun cleanupSafely(
        operation: String,
        cleanup: () -> Unit,
    ) {
        try {
            cleanup()
        } catch (e: Exception) {
            logger.warn("Failed to $operation while replacing Amplitude instance '$replacementInstanceName': $e")
        }
    }

    companion object {
        /**
         * The event type for start session events.
         */
        const val START_SESSION_EVENT = "session_start"

        /**
         * The event type for end session events.
         */
        const val END_SESSION_EVENT = "session_end"
    }
}

/**
 * DSL for creating an Android Amplitude instance with a [ConfigurationBuilder] block.
 *
 * Usage:
 * ```
 * Amplitude("api-key", applicationContext) {
 *     autocapture = setOf(AutocaptureOption.SESSIONS)
 *     flushQueueSize = 10
 * }
 * ```
 */
fun Amplitude(
    apiKey: String,
    context: Context,
    configs: ConfigurationBuilder.() -> Unit,
): Amplitude {
    return Amplitude(ConfigurationBuilder(apiKey, context).apply(configs).build())
}
