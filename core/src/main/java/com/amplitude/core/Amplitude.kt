package com.amplitude.core

import com.amplitude.core.events.BaseEvent
import com.amplitude.core.events.Identify
import com.amplitude.core.events.Revenue
import com.amplitude.core.events.RevenueEvent
import com.amplitude.core.platform.ObservePlugin
import com.amplitude.core.platform.Plugin
import com.amplitude.core.platform.Timeline
import com.amplitude.core.platform.plugins.AmplitudeDestination
import com.amplitude.core.platform.plugins.ContextPlugin
import com.amplitude.core.utilities.AnalyticsIdentityListener
import com.amplitude.id.IMIdentityStorageProvider
import com.amplitude.id.IdentityConfiguration
import com.amplitude.id.IdentityContainer
import kotlinx.coroutines.*
import java.util.concurrent.Executors

open class Amplitude internal constructor(
    val configuration: Configuration,
    val store: State,
    val amplitudeScope: CoroutineScope = CoroutineScope(SupervisorJob()),
    val amplitudeDispatcher: CoroutineDispatcher = Executors.newCachedThreadPool().asCoroutineDispatcher(),
    val networkIODispatcher: CoroutineDispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher(),
    val storageIODispatcher: CoroutineDispatcher = Executors.newFixedThreadPool(2).asCoroutineDispatcher(),
    val retryDispatcher: CoroutineDispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
){

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
        add(ContextPlugin())
        add(AmplitudeDestination())

        amplitudeScope.launch (amplitudeDispatcher) {

        }
    }

    @Deprecated("Please use 'track' instead.", ReplaceWith("track"))
    fun logEvent(event: BaseEvent) {
        track(event)
    }

    fun track(event: BaseEvent, callback: ((BaseEvent) -> Unit)? = null) {
        process(event)
    }

    fun identify(identify: Identify) {

    }

    fun identify(userId: String) {
        this.idContainer.identityManager.editIdentity().setUserId(userId).commit()
    }

    fun setDeviceId(deviceId: String) {
        this.idContainer.identityManager.editIdentity().setUserId(deviceId).commit()
    }

    fun groupIdentify(identify: Identify) {

    }

    fun setGroup(groupType: String, groupName: Array<String>) {

    }

    @Deprecated("Please use 'revenue' instead.", ReplaceWith("revenue"))
    fun logRevenue(revenue: Revenue) {
        revenue(revenue)
    }

    /**
     * Create a Revenue object to hold your revenue data and properties,
     * and log it as a revenue event using this method.
     */
    fun revenue(revenue: Revenue) {
        if (!revenue.isValid()) {
            return
        }
        revenue(revenue.toRevenueEvent())
    }

    /**
     * Log a Revenue Event
     */
    fun revenue(event: RevenueEvent) {
        process(event)
    }

    fun process(event: BaseEvent) {
        amplitudeScope.launch(amplitudeDispatcher) {
            timeline.process(event)
        }
    }

    fun add(plugin: Plugin) : Amplitude {
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

    }
}