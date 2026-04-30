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
import com.amplitude.core.platform.ObservePlugin
import com.amplitude.core.platform.Plugin
import com.amplitude.core.platform.Signal
import com.amplitude.core.platform.Timeline
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
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.Executors

/**
 * <h1>Amplitude</h1>
 * This is the SDK instance class that contains all of the SDK functionality.<br><br>
 * Many of the SDK functions return the SDK instance back, allowing you to chain multiple methods calls together.
 */
@OptIn(RestrictedAmplitudeFeature::class)
open class Amplitude(
    val configuration: Configuration,
    val store: State,
    val amplitudeScope: CoroutineScope = CoroutineScope(SupervisorJob()),
    val amplitudeDispatcher: CoroutineDispatcher = Executors.newCachedThreadPool().asCoroutineDispatcher(),
    val networkIODispatcher: CoroutineDispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher(),
    val storageIODispatcher: CoroutineDispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher(),
) {
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
     * Delegates to [Configuration.optOut] — mutating either is equivalent.
     */
    open var optOut: Boolean
        get() = configuration.optOut
        set(value) {
            configuration.optOut = value
            notifyAllPlugins { it.onOptOutChanged(value) }
        }

    init {
        require(configuration.isValid()) { "invalid configuration" }
        timeline = this.createTimeline()

        // Wire identity-change notifications from State to all plugins.
        // The EnumSet tells us which fields changed in this update so we can
        // collapse a setIdentity() call into one logical notification.
        store.onIdentityChanged = { state, changes ->
            if (changes.contains(State.IdentityChangeType.USER_ID)) {
                notifyTimelinePlugins { it.onUserIdChanged(state.userId) }
            }
            if (changes.contains(State.IdentityChangeType.DEVICE_ID)) {
                notifyTimelinePlugins { it.onDeviceIdChanged(state.deviceId) }
            }
        }

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
        EventBridgeContainer.getInstance(
            configuration.instanceName,
        ).eventBridge.setEventReceiver(EventChannel.EVENT, AnalyticsEventReceiver(this))
        add(ContextPlugin())
        add(GetAmpliExtrasPlugin())
        add(AmplitudeDestination())
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
     *
     * Plugins receive [Plugin.onReset] together with one bundled identity-change
     * notification covering both userId and deviceId — never two interleaved.
     *
     * @return the Amplitude instance
     */
    open fun reset(): Amplitude {
        resetIdentity(ContextPlugin.generateRandomDeviceId())
        notifyAllPlugins { it.onReset() }
        return this
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
     * If [plugin] declares a non-null [Plugin.name], any existing plugin with
     * the same name is removed (and its [Plugin.teardown] invoked) before the
     * new plugin is wired in. Plugins with a `null` name are never deduplicated.
     *
     * @param plugin the plugin
     * @return the Amplitude instance
     */
    fun add(plugin: Plugin): Amplitude {
        // Dedup: if the incoming plugin has a non-null name, evict any existing
        // plugin sharing that name from both the timeline and the observe store.
        plugin.name?.let { pluginName ->
            timeline.removeByName(pluginName)
            store.removeByName(pluginName)
        }

        when (plugin) {
            is ObservePlugin -> {
                this.store.add(plugin, this)
            }
            else -> {
                this.timeline.add(plugin)
            }
        }

        return this
    }

    /**
     * Find the first plugin of type [T] registered with this Amplitude
     * instance, traversing all timeline layers (Before, Enrichment,
     * Destination, Utility, Observe). Returns null if no plugin matches.
     */
    inline fun <reified T : Plugin> findPlugin(): T? = timeline.findPlugin()

    /**
     * Find the first plugin whose [Plugin.name] matches [name] across all
     * timeline layers and the observe store. Returns null if no plugin matches.
     */
    fun findPluginByName(name: String): Plugin? = timeline.findPluginByName(name) ?: store.plugins.firstOrNull { it.name == name }

    fun remove(plugin: Plugin): Amplitude {
        when (plugin) {
            is ObservePlugin -> {
                this.store.remove(plugin)
            }
            else -> {
                this.timeline.remove(plugin)
            }
        }

        return this
    }

    fun flush() {
        amplitudeScope.launch(amplitudeDispatcher) {
            isBuilt.await()

            timeline.applyClosure {
                (it as? EventPlugin)?.flush()
            }
        }
    }

    /**
     * Fan a closure out to every plugin in the timeline (Before, Enrichment,
     * Destination, Utility, Observe).
     *
     * Each per-plugin invocation is isolated: if a plugin throws, the failure
     * is logged and the remaining plugins still get notified. Callbacks may
     * fire from any coroutine — they must never propagate plugin failures back
     * to the caller (e.g. the Android event-processing loop).
     *
     * Restricted to platform-SDK use (e.g. the Android session-id wiring).
     * Public to allow cross-module access; not part of the customer surface.
     */
    @RestrictedAmplitudeFeature
    fun notifyTimelinePlugins(block: (Plugin) -> Unit) {
        timeline.applyClosure { plugin -> safelyNotify(plugin, block) }
    }

    /**
     * Fan a closure out to every plugin registered with this Amplitude
     * instance — both timeline plugins and [ObservePlugin]s in the store.
     * Used for state-change callbacks where ObservePlugin authors reasonably
     * expect to be notified ([Plugin.onOptOutChanged], [Plugin.onReset],
     * [Plugin.onSessionIdChanged]).
     *
     * Each per-plugin invocation is isolated: if a plugin throws, the failure
     * is logged and the remaining plugins still get notified.
     *
     * Restricted to platform-SDK use; not part of the customer surface.
     */
    @RestrictedAmplitudeFeature
    fun notifyAllPlugins(block: (Plugin) -> Unit) {
        timeline.applyClosure { plugin -> safelyNotify(plugin, block) }
        store.plugins.forEach { plugin -> safelyNotify(plugin, block) }
    }

    /**
     * Invoke [block] on [plugin], catching any [Throwable] so one misbehaving
     * plugin can't break the notification fan-out (or terminate the coroutine
     * draining the Android event-message channel).
     */
    private fun safelyNotify(
        plugin: Plugin,
        block: (Plugin) -> Unit,
    ) {
        try {
            block(plugin)
        } catch (throwable: Throwable) {
            val identifier = plugin.name ?: plugin::class.java.name
            logger.warn("Plugin '$identifier' threw during notify callback: $throwable")
        }
    }

    /**
     * Reset identity atomically: clear userId and rotate to [newDeviceId].
     * Plugins observe one bundled identity-change notification rather than
     * two interleaved ones. Restricted to platform-SDK use.
     */
    @RestrictedAmplitudeFeature
    fun resetIdentity(newDeviceId: String) {
        identityCoordinator.resetIdentity(newDeviceId)
    }

    private fun convertPropertiesToIdentify(userProperties: Map<String, Any?>?): Identify {
        val identify = Identify()
        userProperties?.forEach { property ->
            property.value?.let { identify.set(property.key, it) }
        }
        return identify
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
