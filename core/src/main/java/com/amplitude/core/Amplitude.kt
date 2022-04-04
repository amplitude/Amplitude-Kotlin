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
import com.amplitude.core.utilities.AnalyticsEventReceiver
import com.amplitude.core.utilities.AnalyticsIdentityListener
import com.amplitude.eventbridge.EventBridgeContainer
import com.amplitude.eventbridge.EventChannel
import com.amplitude.id.IMIdentityStorageProvider
import com.amplitude.id.IdentityConfiguration
import com.amplitude.id.IdentityContainer
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.concurrent.Executors

/**
 * <h1>Amplitude</h1>
 * This is the SDK instance class that contains all of the SDK functionality.<br><br>
 * Many of the SDK functions return the SDK instance back, allowing you to chain multiple method calls together
 */
open class Amplitude internal constructor(
    val configuration: Configuration,
    val store: State,
    val amplitudeScope: CoroutineScope = CoroutineScope(SupervisorJob()),
    val amplitudeDispatcher: CoroutineDispatcher = Executors.newCachedThreadPool().asCoroutineDispatcher(),
    val networkIODispatcher: CoroutineDispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher(),
    val storageIODispatcher: CoroutineDispatcher = Executors.newFixedThreadPool(2).asCoroutineDispatcher(),
    val retryDispatcher: CoroutineDispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
) {
    internal val timeline: Timeline
    val storage: Storage
    val logger: Logger
    protected lateinit var idContainer: IdentityContainer

    init {
        require(configuration.isValid()) { "invalid configuration" }
        timeline = Timeline().also { it.amplitude = this }
        storage = configuration.storageProvider.getStorage(this)
        logger = configuration.loggerProvider.getLogger(this)
        build()
    }

    /**
     * Public Constructor
     */
    constructor(configuration: Configuration) : this(configuration, State())

    open fun build() {
        idContainer = IdentityContainer.getInstance(IdentityConfiguration(instanceName = configuration.instanceName, apiKey = configuration.apiKey, identityStorageProvider = IMIdentityStorageProvider()))
        idContainer.identityManager.addIdentityListener(AnalyticsIdentityListener(store))
        EventBridgeContainer.getInstance(configuration.instanceName).eventBridge.setEventReceiver(EventChannel.EVENT, AnalyticsEventReceiver(this))
        add(ContextPlugin())
        add(AmplitudeDestination())

        amplitudeScope.launch(amplitudeDispatcher) {
        }
    }

    @Deprecated("Please use 'track' instead.", ReplaceWith("track"))
    fun logEvent(event: BaseEvent): Amplitude {
        return track(event)
    }

    /**
     * Track an event
     */
    fun track(event: BaseEvent, callback: EventCallBack? = null): Amplitude {
        callback ?. let {
            event.callback = it
        }
        process(event)
        return this
    }

    /**
     * Log event with the specified event type, event properties, and optional event options
     */
    fun track(eventType: String, eventProperties: JSONObject? = null, options: EventOptions? = null): Amplitude {
        val event = BaseEvent()
        event.eventType = eventType
        event.eventProperties = eventProperties
        options ?. let {
            event.mergeEventOptions(it)
        }
        process(event)
        return this
    }

    /**
     * Identify. Use this to send an Identify object containing user property operations to Amplitude server.
     */
    fun identify(identify: Identify, options: EventOptions? = null): Amplitude {
        val event = IdentifyEvent()
        event.userProperties = identify.properties
        options ?. let {
            event.mergeEventOptions(it)
        }
        process(event)
        return this
    }

    /**
     * Sets the user id (can be null).
     */
    fun setUserId(userId: String?): Amplitude {
        this.idContainer.identityManager.editIdentity().setUserId(userId).commit()
        return this
    }

    /**
     * Sets a custom device id. <b>Note: only do this if you know what you are doing!</b>
     */
    fun setDeviceId(deviceId: String): Amplitude {
        this.idContainer.identityManager.editIdentity().setDeviceId(deviceId).commit()
        return this
    }

    /**
     * Identify a group
     */
    fun groupIdentify(groupType: String, groupName: String, identify: Identify, options: EventOptions? = null): Amplitude {
        val event = GroupIdentifyEvent()
        var group: JSONObject? = null
        try {
            group = JSONObject().put(groupType, groupName)
        } catch (e: Exception) {
            logger.error("Error in groupIdentif: ${e.toString()}")
        }
        event.groups = group
        event.groupProperties = identify.properties
        options ?. let {
            event.mergeEventOptions(it)
        }
        process(event)
        return this
    }

    /**
     * Sets the user's group.
     */
    fun setGroup(groupType: String, groupName: String, options: EventOptions? = null): Amplitude {
        val identify = Identify().set(groupType, groupName)
        identify(identify, options)
        return this
    }

    /**
     * ets the user's groups.
     */
    fun setGroup(groupType: String, groupName: Array<String>, options: EventOptions? = null): Amplitude {
        val identify = Identify().set(groupType, groupName)
        identify(identify, options)
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
     */
    fun revenue(revenue: Revenue, options: EventOptions? = null): Amplitude {
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
     * Log a Revenue Event
     */
    fun revenue(event: RevenueEvent): Amplitude {
        process(event)
        return this
    }

    private fun process(event: BaseEvent) {
        if (configuration.optOut) {
            logger.info("Skip event for opt out config.")
        }
        amplitudeScope.launch(amplitudeDispatcher) {
            timeline.process(event)
        }
    }

    fun add(plugin: Plugin): Amplitude {
        when (plugin) {
            is ObservePlugin -> {
                this.store.add(plugin)
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
}
