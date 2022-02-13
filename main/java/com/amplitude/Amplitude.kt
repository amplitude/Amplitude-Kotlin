package com.amplitude

import com.amplitude.events.BaseEvent
import com.amplitude.events.Identify
import com.amplitude.events.Revenue
import com.amplitude.platform.ObservePlugin
import com.amplitude.platform.Plugin
import com.amplitude.platform.Timeline
import com.amplitude.platform.plugins.AmplitudeDestination
import com.amplitude.platform.plugins.ContextPlugin
import com.amplitude.utilities.AnalyticsIdentityListener
import kotlinx.coroutines.*
import java.util.concurrent.Executors

open class Amplitude internal constructor(
    val configuration: com.amplitude.Configuration,
    val store: com.amplitude.State,
    val amplitudeScope: CoroutineScope = CoroutineScope(SupervisorJob()),
    val amplitudeDispatcher: CoroutineDispatcher = Executors.newCachedThreadPool().asCoroutineDispatcher(),
    val networkIODispatcher: CoroutineDispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher(),
    val storageIODispatcher: CoroutineDispatcher = Executors.newFixedThreadPool(2).asCoroutineDispatcher()
){

    internal val timeline: Timeline
    val storage: com.amplitude.Storage
    val logger: com.amplitude.Logger
    val idContainer: IdContainer

    init {
        require(configuration.isValid()) { "invalid configuration" }
        timeline = Timeline().also { it.amplitude = this }
        storage = configuration.storageProvider.getStorage(this)
        logger = configuration.loggerProvider.getLogger(this)
        idContainer = IdContainer.getInstance(configuration.apiKey, IMIdentityStorageProvider())
        idContainer.identityStore.addIdentityListener(AnalyticsIdentityListener(store))
        build()
    }

    /**
     * Public Constructor
     */
    constructor(configuration: com.amplitude.Configuration) : this(configuration, com.amplitude.State())

    internal fun build() {
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
        this.idContainer.identityStore.editIdentity().setUserId(userId).commit()
    }

    fun groupIdentify(identify: Identify) {

    }

    fun setGroup(groupType: String, groupName: Array<String>) {

    }

    @Deprecated("Please use 'revenue' instead.", ReplaceWith("revenue"))
    fun logRevenue(revenue: Revenue) {
        revenue(revenue)
    }

    fun revenue(revenue: Revenue) {

    }

    fun process(event: BaseEvent) {
        amplitudeScope.launch(amplitudeDispatcher) {
            timeline.process(event)
        }
    }

    fun add(plugin: Plugin) : com.amplitude.Amplitude {
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

    fun remove(plugin: Plugin): com.amplitude.Amplitude {
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