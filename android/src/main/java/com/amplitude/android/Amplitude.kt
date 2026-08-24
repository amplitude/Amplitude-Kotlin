package com.amplitude.android

import android.app.Application
import android.content.Context
import com.amplitude.android.anr.AnrCatcher
import com.amplitude.android.anr.createAnrCatcher
import com.amplitude.android.anr.recordAnr
import com.amplitude.android.crash.CrashCatcher
import com.amplitude.android.crash.CrashTrackingRemoteConfig
import com.amplitude.android.crash.recordCrash
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
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File
import java.lang.ref.WeakReference
import java.util.concurrent.atomic.AtomicBoolean
import com.amplitude.core.Amplitude as CoreAmplitude

@OptIn(ExperimentalCoroutinesApi::class, RestrictedAmplitudeFeature::class)
public open class Amplitude internal constructor(
    configuration: Configuration,
    state: State,
    amplitudeScope: CoroutineScope = CoroutineScope(SupervisorJob()),
    amplitudeDispatcher: CoroutineDispatcher = Dispatchers.IO,
    networkIODispatcher: CoroutineDispatcher = Dispatchers.IO.limitedParallelism(1),
    storageIODispatcher: CoroutineDispatcher = Dispatchers.IO.limitedParallelism(1),
) : CoreAmplitude(
        configuration = configuration,
        store = state,
        amplitudeScope = amplitudeScope,
        amplitudeDispatcher = amplitudeDispatcher,
        networkIODispatcher = networkIODispatcher,
        storageIODispatcher = storageIODispatcher,
    ) {
    public constructor(configuration: Configuration) : this(configuration, State())

    // Assigned in [initWatchers], after configuration validation and before any other
    // initialization, so the handler covers SDK init without leaking on invalid config.
    private lateinit var crashCatcher: CrashCatcher
    private lateinit var anrCatcher: AnrCatcher

    override fun initWatchers() {
        val androidConfig = configuration as Configuration
        crashCatcher =
            CrashCatcher(
                context = androidConfig.context,
                ioDispatcher = storageIODispatcher,
                diagnosticsClientLazy = lazy { diagnosticsClient },
                crashTrackingRemoteConfigLazy = lazy { crashTrackingRemoteConfig },
            )
        anrCatcher =
            createAnrCatcher(
                context = androidConfig.context,
                ioDispatcher = storageIODispatcher,
            )
    }

    override val sessionId: Long
        get() {
            return (timeline as Timeline).sessionId
        }

    private lateinit var activityLifecycleCallbacks: ActivityLifecycleObserver

    private lateinit var androidContextPlugin: AndroidContextPlugin

    internal lateinit var autocaptureManager: AutocaptureManager
        private set

    private lateinit var crashTrackingRemoteConfig: CrashTrackingRemoteConfig

    // Assigned in [build], not by a field initializer: initializers run after the core constructor,
    // where they would clobber a retirement from a same-name instance racing this one.
    private lateinit var retired: AtomicBoolean

    /**
     * Whether this instance still owns its instance name. Gates event ingress, session work,
     * plugin installation, and claims on state shared by name.
     */
    internal val isActive: Boolean
        get() = !retired.get()

    internal val application: Application
        get() = (configuration as Configuration).context as Application

    /**
     * Init-order trap: [CoreAmplitude]'s `init` calls [build] before this class's field
     * initializers run, and [buildInternal] may already be on a background thread. Assign anything
     * [buildInternal] or its plugins read here — never `by lazy` or a field initializer. This is
     * also where the instance claims its name, so ownership settles before the build starts.
     */
    override fun build(): Deferred<Boolean> {
        retired = AtomicBoolean(false)
        activityLifecycleCallbacks = ActivityLifecycleObserver()
        androidContextPlugin = AndroidContextPlugin()
        try {
            val androidConfig = configuration as Configuration
            crashTrackingRemoteConfig =
                CrashTrackingRemoteConfig(
                    remoteConfigClient = remoteConfigClient,
                    sdkVersion = BuildConfig.AMPLITUDE_VERSION,
                )
            crashTrackingRemoteConfig.initialize()
            autocaptureManager =
                AutocaptureManager(
                    initialAutocapture = androidConfig.autocapture,
                    initialInteractionsOptions = androidConfig.interactionsOptions,
                    remoteConfigClient = if (androidConfig.enableAutocaptureRemoteConfig) remoteConfigClient else null,
                    logger = logger,
                    diagnosticsClient = diagnosticsClient,
                )
            // Claim the instance name before the build exists, so nothing the build installs can run
            // before the claim, and a failed claim leaves no build behind.
            AmplitudeRegistry.activate(this)
        } catch (error: Throwable) {
            // Watchers are installed in the constructor, before this method. A throw here never
            // reaches retire(), so drop them before the half-built instance is abandoned.
            markRetired()
            detachWatchers()
            throw error
        }
        return super.build()
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
        // A build that outlives its instance must not install plugins or claim shared state:
        // the replacement owns both by then.
        if (!isActive) return

        crashCatcher.consumePreviousCrash()?.let { previousCrash ->
            AmplitudeRegistry.runIfActive(this) {
                diagnosticsClient.recordCrash(previousCrash)
            }
        }
        anrCatcher.consumePreviousAnrs().forEach { previousAnr ->
            AmplitudeRegistry.runIfActive(this) {
                diagnosticsClient.recordAnr(previousAnr)
            }
        }

        val migrationManager = MigrationManager(this)
        migrationManager.migrateOldStorage()

        if (!isActive) return

        this.createIdentityContainer(identityConfiguration)

        if (!isActive) return

        if (this.configuration.offline != AndroidNetworkConnectivityCheckerPlugin.Disabled) {
            add(AndroidNetworkConnectivityCheckerPlugin())
        }
        add(androidContextPlugin)
        add(GetAmpliExtrasPlugin())
        add(AndroidLifecyclePlugin(activityLifecycleCallbacks))
        add(AnalyticsConnectorIdentityPlugin())
        add(AnalyticsConnectorPlugin())
        add(AmplitudeDestination())

        (timeline as Timeline).start()
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
        // Identity is shared by instance name, so a replaced instance must not rotate it.
        if (!isActive) return this

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
    public fun onEnterForeground(timestamp: Long) {
        (timeline as Timeline).onEnterForeground(timestamp)
    }

    @GuardedAmplitudeFeature
    public fun onExitForeground(timestamp: Long) {
        (timeline as Timeline).onExitForeground(timestamp)
    }

    /** Claims the hooks only the active instance may own. Throwing here leaves the previous instance in place. */
    internal fun startAsActiveInstance() {
        registerShutdownHook()
        application.registerActivityLifecycleCallbacks(activityLifecycleCallbacks)
    }

    /** Marks this instance inactive, returning false if it already was. */
    internal fun markRetired(): Boolean = retired.compareAndSet(false, true)

    /**
     * Releases process-wide watchers started in [initWatchers]. Safe before [retire], including
     * when construction fails after those watchers are installed.
     */
    internal fun detachWatchers() {
        if (::crashCatcher.isInitialized) {
            runCatching { crashCatcher.detach() }
                .onFailure { logger.warn("Failed to detach the crash handler: $it") }
        }
    }

    /**
     * Releases what an inactive instance must not keep holding. Nothing here waits on this
     * instance, and queued events still drain to the storage the replacement shares by name.
     */
    internal fun retire() {
        runCatching { application.unregisterActivityLifecycleCallbacks(activityLifecycleCallbacks) }
            .onFailure { logger.warn("Failed to unregister activity lifecycle callbacks: $it") }
        runCatching { (timeline as Timeline).close() }
            .onFailure { logger.warn("Failed to close the event queue: $it") }
        // Autocapture holds process-wide resources (window callbacks, a Curtains listener) and
        // takes no part in draining, so it goes now rather than behind the drain below.
        runCatching { findPlugin<AndroidLifecyclePlugin>()?.let { remove(it) } }
            .onFailure { logger.warn("Failed to stop Android autocapture: $it") }
        // The uncaught exception handler cannot be unregistered, so it has to let go of this
        // instance instead. Crash persistence belongs to whichever instance is still active.
        detachWatchers()

        // The rest go last: they must outlive an in-flight build that is still adding them and the
        // queued events still draining through them. Off the constructor's thread either way, so
        // the replacement never waits on this.
        amplitudeScope.launch(amplitudeDispatcher) {
            isBuilt.join()
            (timeline as Timeline).awaitDrained()
            plugins(UniversalPlugin::class.java).forEach { plugin ->
                runCatching { remove(plugin) }
                    .onFailure { logger.warn("Failed to remove ${plugin.name ?: plugin::class.java.simpleName}: $it") }
            }
        }
    }

    private fun registerShutdownHook() {
        // close the stream if the app shuts down
        try {
            val weakRef = WeakReference(this)
            Runtime.getRuntime().addShutdownHook(
                object : Thread() {
                    override fun run() {
                        (weakRef.get()?.timeline as? Timeline)?.stop()
                    }
                },
            )
        } catch (e: IllegalStateException) {
            // Once the shutdown sequence has begun it is impossible to register a shutdown hook,
            // so we just ignore the IllegalStateException that's thrown.
            // https://developer.android.com/reference/java/lang/Runtime#addShutdownHook(java.lang.Thread)
        }
    }

    public companion object {
        /**
         * The event type for start session events.
         */
        public const val START_SESSION_EVENT: String = "session_start"

        /**
         * The event type for end session events.
         */
        public const val END_SESSION_EVENT: String = "session_end"
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
public fun Amplitude(
    apiKey: String,
    context: Context,
    configs: ConfigurationBuilder.() -> Unit,
): Amplitude {
    return Amplitude(ConfigurationBuilder(apiKey, context).apply(configs).build())
}
