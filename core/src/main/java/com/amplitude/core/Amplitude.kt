package com.amplitude.core

import com.amplitude.common.Logger
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
import com.amplitude.core.platform.Timeline
import com.amplitude.core.platform.plugins.AmplitudeDestination
import com.amplitude.core.platform.plugins.ContextPlugin
import com.amplitude.core.platform.plugins.GetAmpliExtrasPlugin
import com.amplitude.core.utilities.AnalyticsEventReceiver
import com.amplitude.core.utilities.AnalyticsIdentityListener
import com.amplitude.core.utilities.Diagnostics
import com.amplitude.eventbridge.EventBridgeContainer
import com.amplitude.eventbridge.EventChannel
import com.amplitude.id.IdentityConfiguration
import com.amplitude.id.IdentityContainer
import com.amplitude.id.IdentityStorage
import com.amplitude.id.IdentityUpdateType
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import java.util.UUID
import java.util.concurrent.Executors

/**
 * <h1>Amplitude</h1>
 * This is the SDK instance class that contains all of the SDK functionality.<br><br>
 * Many of the SDK functions return the SDK instance back, allowing you to chain multiple methods calls together.
 */
open class Amplitude internal constructor(
    val configuration: Configuration,
    val store: State,
    val amplitudeScope: CoroutineScope = CoroutineScope(SupervisorJob()),
    val amplitudeDispatcher: CoroutineDispatcher = Executors.newCachedThreadPool().asCoroutineDispatcher(),
    val networkIODispatcher: CoroutineDispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher(),
    val storageIODispatcher: CoroutineDispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher(),
    val retryDispatcher: CoroutineDispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher(),
) {
    val timeline: Timeline
    lateinit var storage: Storage
        private set
    lateinit var identifyInterceptStorage: Storage
        private set
    lateinit var identityStorage: IdentityStorage
        private set
    val logger: Logger
    lateinit var idContainer: IdentityContainer
        private set
    val isBuilt: Deferred<Boolean>
    val diagnostics = Diagnostics()

    init {
        require(configuration.isValid()) { "invalid configuration" }
        timeline = this.createTimeline()
        logger = configuration.loggerProvider.getLogger(this)
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
        )
    }

    protected fun createIdentityContainer(identityConfiguration: IdentityConfiguration) {
        idContainer = IdentityContainer.getInstance(identityConfiguration)
        val listener = AnalyticsIdentityListener(store)
        idContainer.identityManager.addIdentityListener(listener)
        if (idContainer.identityManager.isInitialized()) {
            listener.onIdentityChanged(idContainer.identityManager.getIdentity(), IdentityUpdateType.Initialized)
        }
    }

    protected open fun build(): Deferred<Boolean> {
        val amplitude = this

        val built =
            amplitudeScope.async(amplitudeDispatcher, CoroutineStart.LAZY) {
                storage = configuration.storageProvider.getStorage(amplitude)
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
        add(
            object : ContextPlugin() {
                override fun setDeviceId(deviceId: String) {
                    // set device id immediately, don't wait for isBuilt
                    setDeviceIdInternal(deviceId)
                }
            },
        )
        add(GetAmpliExtrasPlugin())
        add(AmplitudeDestination())
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
        options ?. let {
            event.mergeEventOptions(it)
        }
        callback ?. let {
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
        amplitudeScope.launch(amplitudeDispatcher) {
            if (isBuilt.await()) {
                idContainer.identityManager.editIdentity().setUserId(userId).commit()
            }
        }
        return this
    }

    /**
     * Get the user id.
     *
     * @return User id.
     */
    fun getUserId(): String? {
        return if (this::idContainer.isInitialized) idContainer.identityManager.getIdentity().userId else null
    }

    /**
     * <b>INTERNAL METHOD!</b>
     *
     * Sets device id immediately without waiting for build() to complete.
     *
     * <b>Note: only do this if you know what you are doing!</b>
     */
    protected fun setDeviceIdInternal(deviceId: String) {
        idContainer.identityManager.editIdentity().setDeviceId(deviceId).commit()
    }

    /**
     * Sets a custom device id. <b>Note: only do this if you know what you are doing!</b>
     *
     * @param deviceId custom device id
     * @return the Amplitude instance
     */
    fun setDeviceId(deviceId: String): Amplitude {
        amplitudeScope.launch(amplitudeDispatcher) {
            isBuilt.await()
            setDeviceIdInternal(deviceId)
        }
        return this
    }

    /**
     * Get the device id.
     *
     * @return Device id.
     */
    fun getDeviceId(): String? {
        return if (this::idContainer.isInitialized) idContainer.identityManager.getIdentity().deviceId else null
    }

    /**
     * Reset identity:
     *  - reset userId to "null"
     *  - reset deviceId to random UUID
     * @return the Amplitude instance
     */
    open fun reset(): Amplitude {
        this.setUserId(null)
        this.setDeviceId(UUID.randomUUID().toString() + "R")
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
        if (configuration.optOut) {
            logger.info("Skip event for opt out config.")
            return
        }

        if (event.timestamp == null) {
            event.timestamp = System.currentTimeMillis()
        }

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
        this.timeline.applyClosure {
            (it as? EventPlugin)?.flush()
        }
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
 * constructor function to build amplitude in dsl format with config options
 * Usage: Amplitude("123") {
 *            this.flushQueueSize = 10
 *        }
 *
 * NOTE: this method should only be used for JVM application.
 *
 * @param apiKey
 * @param configs
 * @return
 */
fun Amplitude(
    apiKey: String,
    configs: Configuration.() -> Unit,
): Amplitude {
    val config = Configuration(apiKey)
    configs.invoke(config)
    return Amplitude(config)
}
