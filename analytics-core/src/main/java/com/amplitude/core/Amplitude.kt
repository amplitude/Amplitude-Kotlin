package com.amplitude.core

import com.amplitude.common.Logger
import com.amplitude.core.diagnostics.DiagnosticsClient
import com.amplitude.core.diagnostics.DiagnosticsClientImpl
import com.amplitude.core.diagnostics.DiagnosticsContextProvider
import com.amplitude.core.events.BaseEvent
import com.amplitude.core.events.EventOptions
import com.amplitude.core.events.GroupIdentifyEvent
import com.amplitude.core.events.Identify
import com.amplitude.core.events.IdentifyEvent
import com.amplitude.core.events.Revenue
import com.amplitude.core.events.RevenueEvent
import com.amplitude.core.platform.EventPlugin
import com.amplitude.core.platform.Plugin
import com.amplitude.core.platform.PluginHost
import com.amplitude.core.platform.Signal
import com.amplitude.core.platform.Timeline
import com.amplitude.core.platform.UniversalPlugin
import com.amplitude.core.platform.plugins.AmplitudeDestination
import com.amplitude.core.platform.plugins.ContextPlugin
import com.amplitude.core.platform.plugins.GetAmpliExtrasPlugin
import com.amplitude.core.remoteconfig.RemoteConfigClient
import com.amplitude.core.remoteconfig.RemoteConfigClientImpl
import com.amplitude.core.utilities.AnalyticsEventReceiver
import com.amplitude.core.utilities.Diagnostics
import com.amplitude.core.utilities.deepCopy
import com.amplitude.core.utilities.http.HttpClient
import com.amplitude.eventbridge.EventBridgeContainer
import com.amplitude.eventbridge.EventChannel
import com.amplitude.id.IdentityConfiguration
import com.amplitude.id.IdentityContainer
import com.amplitude.id.IdentityStorage
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * <h1>Amplitude</h1>
 * This is the SDK instance class that contains all of the SDK functionality.<br><br>
 * Many of the SDK functions return the SDK instance back, allowing you to chain multiple methods calls together.
 */
open class Amplitude(
    val configuration: Configuration,
    val store: State,
    val amplitudeScope: CoroutineScope = CoroutineScope(SupervisorJob()),
    val amplitudeDispatcher: CoroutineDispatcher = Executors.newCachedThreadPool().asCoroutineDispatcher(),
    val networkIODispatcher: CoroutineDispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher(),
    val storageIODispatcher: CoroutineDispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher(),
) : PluginHost {
    open val identity: AnalyticsIdentity
        get() =
            object : AnalyticsIdentity {
                override val userId: String? = store.userId
                override val deviceId: String? = store.deviceId
            }

    /**
     * Session id is Android-only. Core returns -1 by default; the Android
     * subclass overrides this with the real value.
     */
    open val sessionId: Long
        get() = -1L
    val timeline: Timeline
    val storage: Storage by lazy {
        configuration.storageProvider.getStorage(this)
    }
    lateinit var identifyInterceptStorage: Storage
        private set
    lateinit var identityStorage: IdentityStorage
        private set
    val logger: Logger by lazy {
        configuration.loggerProvider.getLogger(this)
    }
    lateinit var idContainer: IdentityContainer
        private set
    val isBuilt: Deferred<Boolean>
    val diagnostics = Diagnostics()
    internal val identityCoordinator = IdentityCoordinator(store)

    private val shutdownState = AtomicBoolean(false)

    /** Whether [shutdown] has been invoked. Readable by platform subclasses. */
    protected fun isShutdown(): Boolean = shutdownState.get()

    // The EventBridge container this instance registered on; freed (ownership-checked) on shutdown.
    @Volatile private var eventBridgeContainer: EventBridgeContainer? = null

    @RestrictedAmplitudeFeature
    val diagnosticsClient: DiagnosticsClient by lazy {
        DiagnosticsClientImpl(
            apiKey = configuration.apiKey,
            serverZone = configuration.serverZone,
            instanceName = configuration.instanceName,
            storageDirectory = diagnosticsStorageDirectory(),
            logger = logger,
            coroutineScope = amplitudeScope,
            networkIODispatcher = networkIODispatcher,
            storageIODispatcher = storageIODispatcher,
            remoteConfigClient = remoteConfigClient,
            httpClient = HttpClient(configuration, logger),
            contextProvider = diagnosticsContextProvider(),
            enabled = configuration.enableDiagnostics,
        )
    }

    @RestrictedAmplitudeFeature
    val remoteConfigClient: RemoteConfigClient by lazy {
        RemoteConfigClientImpl(
            apiKey = configuration.apiKey,
            serverZone = configuration.serverZone,
            coroutineScope = amplitudeScope,
            networkIODispatcher = networkIODispatcher,
            storageIODispatcher = storageIODispatcher,
            storage = storage,
            httpClient = HttpClient(configuration, logger),
            logger = logger,
        )
    }

    val amplitudeContext: AmplitudeContext by lazy { buildAmplitudeContext() }

    @OptIn(RestrictedAmplitudeFeature::class)
    private fun buildAmplitudeContext(): AmplitudeContext =
        AmplitudeContext(
            apiKey = configuration.apiKey,
            instanceName = configuration.instanceName,
            serverZone = configuration.serverZone,
            logger = logger,
            remoteConfigClientProvider = { remoteConfigClient },
            diagnosticsClientProvider = { diagnosticsClient },
        )

    // Signal broadcasting - single shared flow for all plugins
    private val _signalFlow =
        MutableSharedFlow<Signal>(
            replay = 0,
            // Generous buffer to handle signal bursts without dropping events
            extraBufferCapacity = 1_000,
            onBufferOverflow = DROP_OLDEST,
        )
    val signalFlow: SharedFlow<Signal> = _signalFlow.asSharedFlow()

    /**
     * Emit a signal to the shared signal flow.
     * This method is called by SignalProvider plugins to emit signals.
     */
    internal fun emitSignal(signal: Signal) {
        _signalFlow.tryEmit(signal)
    }

    /**
     * Whether events should be suppressed. Set this at runtime to opt the user in or out.
     * Reads through to [Configuration.optOut]. Set via this property (not
     * `configuration.optOut` directly) — only this setter notifies plugins via
     * [Plugin.onOptOutChanged].
     */
    open var optOut: Boolean
        get() = configuration.optOut
        set(value) {
            configuration.optOut = value
            notifyPlugins { it.onOptOutChanged(value) }
        }

    internal val analyticsClient: AnalyticsClient by lazy { AmplitudeAnalyticsClient(this) }

    init {
        require(configuration.isValid()) { "invalid configuration" }
        timeline = this.createTimeline()
        isBuilt = this.build()
        isBuilt.start()
    }

    /**
     * Public Constructor.
     */
    constructor(configuration: Configuration) : this(configuration, State())

    open fun createTimeline(): Timeline {
        return Timeline().also { it.amplitude = this }
    }

    protected open fun createIdentityConfiguration(): IdentityConfiguration {
        return IdentityConfiguration(
            instanceName = configuration.instanceName,
            apiKey = configuration.apiKey,
            identityStorageProvider = configuration.identityStorageProvider,
            logger = logger,
            storageDirectory = File("/tmp/amplitude-identity/${configuration.instanceName}"),
            fileName = "amplitude-identity-${configuration.instanceName}",
        )
    }

    protected fun createIdentityContainer(identityConfiguration: IdentityConfiguration) {
        idContainer = IdentityContainer.getInstance(identityConfiguration)
        identityCoordinator.bootstrap(idContainer.identityManager)
    }

    protected open fun build(): Deferred<Boolean> {
        val amplitude = this

        val built =
            amplitudeScope.async(amplitudeDispatcher, CoroutineStart.LAZY) {
                identifyInterceptStorage =
                    configuration.identifyInterceptStorageProvider.getStorage(
                        amplitude,
                        "amplitude-identify-intercept",
                    )
                val identityConfiguration = createIdentityConfiguration()
                identityStorage = configuration.identityStorageProvider.getIdentityStorage(identityConfiguration)

                amplitude.buildInternal(identityConfiguration)
                true
            }
        return built
    }

    protected open suspend fun buildInternal(identityConfiguration: IdentityConfiguration) {
        createIdentityContainer(identityConfiguration)
        val eventBridgeContainer = EventBridgeContainer.getInstance(configuration.instanceName)
        this.eventBridgeContainer = eventBridgeContainer
        eventBridgeContainer.eventBridge.setEventReceiver(EventChannel.EVENT, AnalyticsEventReceiver(this))
        add(ContextPlugin())
        add(GetAmpliExtrasPlugin())
        add(AmplitudeDestination())

        // Tear down if shutdown() raced this async build. Keep no suspension point between the
        // last add() above and this check, or a racing scope cancel could leak those plugins.
        if (shutdownState.get()) {
            performShutdown()
        }
    }

    protected open fun diagnosticsContextProvider(): DiagnosticsContextProvider? {
        return null
    }

    protected open fun diagnosticsStorageDirectory(): File {
        return File("/tmp/amplitude-diagnostics/${configuration.instanceName}")
    }

    @Deprecated("Please use 'track' instead.", ReplaceWith("track"))
    fun logEvent(event: BaseEvent): Amplitude {
        return track(event)
    }

    /**
     * Track an event.
     *
     * @param event the event
     * @param callback the optional event callback
     * @param options optional event options
     * @return the Amplitude instance
     */
    @JvmOverloads
    fun track(
        event: BaseEvent,
        options: EventOptions? = null,
        callback: EventCallBack? = null,
    ): Amplitude {
        options?.let {
            event.mergeEventOptions(it)
        }
        callback?.let {
            event.callback = it
        }
        process(event)
        return this
    }

    /**
     * Log event with the specified event type, event properties, and optional event options.
     *
     * @param eventType the event type
     * @param eventProperties the event properties
     * @param options optional event options
     * @return the Amplitude instance
     */
    @JvmOverloads
    fun track(
        eventType: String,
        eventProperties: Map<String, Any?>? = null,
        options: EventOptions? = null,
    ): Amplitude {
        val event = BaseEvent()
        event.eventType = eventType
        event.eventProperties = eventProperties?.toMutableMap()
        options ?. let {
            event.mergeEventOptions(it)
        }
        process(event)
        return this
    }

    /**
     * Identify lets you set the user properties.
     * You can modify user properties by calling this api.
     *
     * @param userProperties user properties
     * @param options optional event options
     * @return the Amplitude instance
     */
    @JvmOverloads
    fun identify(
        userProperties: Map<String, Any?>?,
        options: EventOptions? = null,
    ): Amplitude {
        return identify(convertPropertiesToIdentify(userProperties), options)
    }

    /**
     * Identify lets you to send an Identify object containing user property operations to Amplitude server.
     * You can modify user properties by calling this api.
     *
     * @param identify identify object
     * @param options optional event options
     * @return the Amplitude instance
     */
    @JvmOverloads
    fun identify(
        identify: Identify,
        options: EventOptions? = null,
    ): Amplitude {
        val event = IdentifyEvent()
        event.userProperties = identify.properties

        options ?. let { eventOptions ->
            event.mergeEventOptions(eventOptions)
            eventOptions.userId ?.let { this.setUserId(it) }
            eventOptions.deviceId ?.let { this.setDeviceId(it) }
        }

        process(event)
        return this
    }

    /**
     * Set the user id (can be null).
     *
     * @param userId custom user id
     * @return the Amplitude instance
     */
    fun setUserId(userId: String?): Amplitude {
        identityCoordinator.setUserId(userId)
        val plugins = pluginsSnapshot()
        plugins.filterIsInstance<Plugin>().forEach { safelyNotify(it) { p -> p.onUserIdChanged(store.userId) } }
        val snapshot = identity
        plugins.forEach { safelyNotify(it) { p -> p.onIdentityChanged(snapshot) } }
        return this
    }

    /**
     * Get the user id.
     *
     * @return User id.
     */
    fun getUserId(): String? {
        return store.userId
    }

    /**
     * Sets a custom device id. <b>Note: only do this if you know what you are doing!</b>
     *
     * @param deviceId custom device id
     * @return the Amplitude instance
     */
    fun setDeviceId(deviceId: String): Amplitude {
        identityCoordinator.setDeviceId(deviceId)
        val plugins = pluginsSnapshot()
        plugins.filterIsInstance<Plugin>().forEach { safelyNotify(it) { p -> p.onDeviceIdChanged(store.deviceId) } }
        val snapshot = identity
        plugins.forEach { safelyNotify(it) { p -> p.onIdentityChanged(snapshot) } }
        return this
    }

    /**
     * Get the device id.
     *
     * @return Device id.
     */
    fun getDeviceId(): String? {
        return store.deviceId
    }

    /**
     * Reset identity:
     *  - reset userId to "null"
     *  - reset deviceId to random UUID
     * @return the Amplitude instance
     */
    open fun reset(): Amplitude {
        doResetWithDeviceId(ContextPlugin.generateRandomDeviceId())
        return this
    }

    /**
     * Reset identity and fan the bundled change out to every plugin from a **single**
     * snapshot: onUserIdChanged(null), then onDeviceIdChanged(newDeviceId), then onReset().
     * The mutation commits under the identity lock before any callback runs (reentrancy-safe).
     */
    protected fun doResetWithDeviceId(newDeviceId: String) {
        identityCoordinator.reset(newDeviceId)
        val plugins = pluginsSnapshot()
        plugins.filterIsInstance<Plugin>().forEach { safelyNotify(it) { p -> p.onUserIdChanged(store.userId) } }
        plugins.filterIsInstance<Plugin>().forEach { safelyNotify(it) { p -> p.onDeviceIdChanged(store.deviceId) } }
        val snapshot = identity
        plugins.forEach { safelyNotify(it) { p -> p.onIdentityChanged(snapshot) } }
        plugins.forEach { safelyNotify(it) { p -> p.onReset() } }
    }

    /**
     * Fan an arbitrary state-change callback out to every plugin (timeline + observe store).
     * For platform SDKs (e.g. Android session-id changes); each invocation is isolated.
     */
    protected fun notifyAllPlugins(block: (UniversalPlugin) -> Unit) {
        notifyPlugins(block)
    }

    /**
     * Identify a group. You can modify group properties by calling this api.
     *
     * @param groupType the group type
     * @param groupName the group name
     * @param groupProperties the group properties
     * @param options optional event options
     * @return the Amplitude instance
     */
    @JvmOverloads
    fun groupIdentify(
        groupType: String,
        groupName: String,
        groupProperties: Map<String, Any?>?,
        options: EventOptions? = null,
    ): Amplitude {
        return groupIdentify(groupType, groupName, convertPropertiesToIdentify(groupProperties), options)
    }

    /**
     * Identify a group. You can modify group properties by calling this api.
     *
     * @param groupType the group type
     * @param groupName the group name
     * @param identify identify object
     * @param options optional event options
     * @return the Amplitude instance
     */
    @JvmOverloads
    fun groupIdentify(
        groupType: String,
        groupName: String,
        identify: Identify,
        options: EventOptions? = null,
    ): Amplitude {
        val event = GroupIdentifyEvent()
        val group = mutableMapOf<String, Any?>()
        group.put(groupType, groupName)
        event.groups = group
        event.groupProperties = identify.properties
        options ?. let {
            event.mergeEventOptions(it)
        }
        process(event)
        return this
    }

    /**
     * Set the user's group.
     *
     * @param groupType the group type
     * @param groupName the group name
     * @param options optional event options
     * @return the Amplitude instance
     */
    @JvmOverloads
    fun setGroup(
        groupType: String,
        groupName: String,
        options: EventOptions? = null,
    ): Amplitude {
        val identify = Identify().set(groupType, groupName)
        val event =
            IdentifyEvent().apply {
                groups = mutableMapOf(groupType to groupName)
                userProperties = identify.properties
            }
        track(event, options)
        return this
    }

    /**
     * Sets the user's groups.
     *
     * @param groupType the group type
     * @param groupName the group name
     * @param options optional event options
     * @return the Amplitude instance
     */
    @JvmOverloads
    fun setGroup(
        groupType: String,
        groupName: Array<String>,
        options: EventOptions? = null,
    ): Amplitude {
        val identify = Identify().set(groupType, groupName)
        val event =
            IdentifyEvent().apply {
                groups = mutableMapOf(groupType to groupName)
                userProperties = identify.properties
            }
        track(event, options)
        return this
    }

    @Deprecated("Please use 'revenue' instead.", ReplaceWith("revenue"))
    fun logRevenue(revenue: Revenue): Amplitude {
        revenue(revenue)
        return this
    }

    /**
     * Create a Revenue object to hold your revenue data and properties,
     * and log it as a revenue event using this method.
     *
     * @param revenue revenue object
     * @param options optional event options
     * @return the Amplitude instance
     */
    @JvmOverloads
    fun revenue(
        revenue: Revenue,
        options: EventOptions? = null,
    ): Amplitude {
        if (!revenue.isValid()) {
            logger.warn("Invalid revenue object, missing required fields")
            return this
        }
        val event = revenue.toRevenueEvent()
        options ?. let {
            event.mergeEventOptions(it)
        }
        revenue(event)
        return this
    }

    /**
     * Log a Revenue Event.
     *
     * @param event the revenue event
     * @return the Amplitude instance
     */
    fun revenue(event: RevenueEvent): Amplitude {
        process(event)
        return this
    }

    private fun process(event: BaseEvent) {
        if (shutdownState.get()) {
            logger.info("SDK is shut down; dropping event.")
            return
        }

        if (optOut) {
            logger.info("Skip event for opt out config.")
            return
        }

        if (event.timestamp == null) {
            event.timestamp = System.currentTimeMillis()
        }

        // Deep-copy mutable maps to sever all references (including nested maps)
        // from the caller. Prevents ConcurrentModificationException when the pipeline
        // processes the event on a background thread while the caller mutates the original.
        event.eventProperties = event.eventProperties?.deepCopy()
        event.userProperties = event.userProperties?.deepCopy()
        event.groups = event.groups?.deepCopy()
        event.groupProperties = event.groupProperties?.deepCopy()

        logger.debug("Logged event with type: ${event.eventType}")
        timeline.process(event)
    }

    /**
     * Add a plugin.
     *
     * @param plugin the plugin
     * @return the Amplitude instance
     */
    fun add(plugin: Plugin): Amplitude {
        if (isShutdown()) {
            logger.debug("SDK is shut down; ignoring add().")
            safelyTeardown(plugin)
            return this
        }
        timeline.add(plugin)
        return this
    }

    /**
     * Add a [UniversalPlugin]. If the plugin is also a [Plugin], it is added directly.
     * Otherwise it is hosted in the enrichment stage.
     */
    fun add(plugin: UniversalPlugin): Amplitude {
        if (isShutdown()) {
            logger.debug("SDK is shut down; ignoring add().")
            safelyTeardown(plugin)
            return this
        }
        timeline.add(plugin)
        return this
    }

    /**
     * Find the first registered plugin assignable to [T] in the timeline mediators.
     */
    inline fun <reified T : Plugin> findPlugin(): T? = timeline.findPlugin<T>()

    override fun plugin(name: String): UniversalPlugin? = timeline.plugin(name)

    override fun <T : UniversalPlugin> plugins(clazz: Class<T>): List<T> {
        val matches = mutableListOf<T>()
        pluginsSnapshot().forEach { plugin ->
            if (clazz.isInstance(plugin)) {
                @Suppress("UNCHECKED_CAST")
                matches.add(plugin as T)
            }
        }
        return matches
    }

    fun remove(plugin: Plugin): Amplitude {
        timeline.remove(plugin)
        return this
    }

    /**
     * Remove a [UniversalPlugin], including a bare plugin hosted in the enrichment stage.
     */
    fun remove(plugin: UniversalPlugin): Amplitude {
        timeline.remove(plugin)
        return this
    }

    fun flush() {
        if (shutdownState.get()) {
            logger.info("SDK is shut down; dropping event.")
            return
        }

        amplitudeScope.launch(amplitudeDispatcher) {
            isBuilt.await()

            timeline.applyClosure {
                (it as? EventPlugin)?.flush()
            }
        }
    }

    /**
     * Permanently shuts down this instance: stops autocapture, tears down all plugins, and stops
     * background work. [track] and [flush] no-op afterwards. Idempotent. Discard your reference
     * to the instance after calling this.
     */
    open fun shutdown() {
        if (shutdownState.compareAndSet(false, true)) {
            performShutdown()
        }
    }

    /** Teardown steps; platform SDKs may override to add their own (call `super`). Idempotent. */
    @Synchronized
    protected open fun performShutdown() {
        // Serialized: shutdown() and the buildInternal tail-check can both call this off different threads.
        try {
            timeline.stop()
        } catch (e: Exception) {
            logger.warn("timeline stop failed: $e")
        } finally {
            // finally, so an Error from a plugin's teardown() still cancels the scope.
            amplitudeScope.cancel()
        }

        // Free the instanceName slot (ownership-checked) so a same-named rebuild gets its own receiver.
        eventBridgeContainer?.let { EventBridgeContainer.remove(configuration.instanceName, it) }

        // Graceful close (no join — shutdown() may run on the main thread; a join risks an ANR).
        (amplitudeDispatcher as? ExecutorCoroutineDispatcher)?.close()
        (networkIODispatcher as? ExecutorCoroutineDispatcher)?.close()
        (storageIODispatcher as? ExecutorCoroutineDispatcher)?.close()
    }

    /**
     * The single plugin fan-out path. Snapshots timeline + store once and isolates every
     * invocation, so a plugin that throws an exception can't break the loop or the other
     * plugins. Errors (e.g. OutOfMemoryError) are not caught and propagate to the caller.
     * Never call while holding the identity lock (notify happens after it's released).
     */
    private fun notifyPlugins(block: (UniversalPlugin) -> Unit) {
        pluginsSnapshot().forEach { safelyNotify(it, block) }
    }

    private fun pluginsSnapshot(): List<UniversalPlugin> = timeline.pluginsSnapshot()

    private fun <T : Any> safelyNotify(
        plugin: T,
        block: (T) -> Unit,
    ) {
        try {
            block(plugin)
        } catch (e: Exception) {
            logger.warn("Plugin '${plugin.identifier()}' threw during state callback: $e")
        }
    }

    /**
     * Tears down a plugin that was rejected by [add] because the SDK is already shut down.
     * The plugin was never registered with the timeline, so this is the only teardown it'll get.
     */
    private fun safelyTeardown(plugin: UniversalPlugin) {
        try {
            plugin.teardown()
        } catch (e: Exception) {
            logger.warn("Plugin '${plugin.identifier()}' threw during teardown: $e")
        }
    }

    private fun Any.identifier(): String =
        try {
            (this as? UniversalPlugin)?.name ?: this::class.java.name
        } catch (_: Exception) {
            this::class.java.name
        }

    private fun convertPropertiesToIdentify(userProperties: Map<String, Any?>?): Identify {
        val identify = Identify()
        userProperties?.forEach { property ->
            property.value?.let { identify.set(property.key, it) }
        }
        return identify
    }
}

private class AmplitudeAnalyticsClient(
    private val amplitude: Amplitude,
) : AnalyticsClient {
    override val identity: AnalyticsIdentity
        get() = amplitude.identity

    override val sessionId: Long
        get() = amplitude.sessionId

    override val optOut: Boolean
        get() = amplitude.optOut

    override fun track(
        eventType: String,
        eventProperties: Map<String, Any?>?,
    ) {
        amplitude.track(eventType, eventProperties, null)
    }
}

/**
 * DSL for creating an Amplitude instance with a [ConfigurationBuilder] block.
 *
 * Usage:
 * ```
 * Amplitude("api-key") {
 *     flushQueueSize = 10
 *     serverZone = ServerZone.EU
 * }
 * ```
 *
 * NOTE: this method should only be used for JVM application.
 */
fun Amplitude(
    apiKey: String,
    configs: ConfigurationBuilder.() -> Unit,
): Amplitude {
    return Amplitude(ConfigurationBuilder(apiKey).apply(configs).build())
}
