package com.amplitude.kotlin

import com.amplitude.kotlin.events.BaseEvent
import com.amplitude.kotlin.events.Identify
import com.amplitude.kotlin.events.Revenue
import com.amplitude.kotlin.platform.Plugin
import com.amplitude.kotlin.platform.Timeline
import com.amplitude.kotlin.platform.plugins.AmplitudeDestination
import com.amplitude.kotlin.platform.plugins.ContextPlugin
import kotlinx.coroutines.*
import java.util.concurrent.Executors

class Amplitude internal constructor(
    val configuration: Configuration,
    val store: State,
    val amplitudeScope: CoroutineScope = CoroutineScope(SupervisorJob()),
    val amplitudeDispatcher: CoroutineDispatcher = Executors.newCachedThreadPool().asCoroutineDispatcher(),
    val networkIODispatcher: CoroutineDispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher(),
    val storageIODispatcher: CoroutineDispatcher = Executors.newFixedThreadPool(2).asCoroutineDispatcher()
){

    internal val timeline: Timeline
    val storage: Storage
    val logger: Logger

    init {
        require(configuration.isValid()) { "invalid configuration" }
        timeline = Timeline().also { it.amplitude = this }
        storage = configuration.storageProvider.getStorage(this)
        logger = configuration.loggerProvider.getLogger(this)
        build()
    }


    constructor(configuration: Configuration) : this(configuration, State())

    internal fun build() {
        add(ContextPlugin())
        add(AmplitudeDestination())

        amplitudeScope.launch (amplitudeDispatcher) {

        }
    }

    fun logEvent(event: BaseEvent) {
        track(event)
    }

    fun track(event: BaseEvent) {
        process(event)
    }

    fun identify(identify: Identify) {

    }

    fun identify(userId: String) {

    }

    fun groupIdentify(identify: Identify) {

    }

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

    fun add(plugin: Plugin) : Amplitude {
        this.timeline.add(plugin)
        return this
    }

    fun remove(plugin: Plugin): Amplitude {
        this.timeline.remove(plugin)
        return this
    }

    fun flush() {

    }
}