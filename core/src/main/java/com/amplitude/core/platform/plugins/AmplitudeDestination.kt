package com.amplitude.core.platform.plugins

import com.amplitude.core.Amplitude
import com.amplitude.core.events.BaseEvent
import com.amplitude.core.events.GroupIdentifyEvent
import com.amplitude.core.events.IdentifyEvent
import com.amplitude.core.events.RevenueEvent
import com.amplitude.core.platform.DestinationPlugin
import com.amplitude.core.platform.EventPipeline
import com.amplitude.core.platform.NetworkConnectivityChecker
import com.amplitude.core.platform.intercept.IdentifyInterceptor
import kotlinx.coroutines.launch

class AmplitudeDestination(private val networkConnectivityChecker: NetworkConnectivityChecker? = null) : DestinationPlugin() {
    private lateinit var pipeline: EventPipeline
    private lateinit var identifyInterceptor: IdentifyInterceptor

    override fun track(payload: BaseEvent): BaseEvent? {
        enqueue(payload)
        return payload
    }

    override fun identify(payload: IdentifyEvent): IdentifyEvent? {
        enqueue(payload)
        return payload
    }

    override fun groupIdentify(payload: GroupIdentifyEvent): GroupIdentifyEvent? {
        enqueue(payload)
        return payload
    }

    override fun revenue(payload: RevenueEvent): RevenueEvent? {
        enqueue(payload)
        return payload
    }

    override fun flush() {
        amplitude.amplitudeScope.launch(amplitude.storageIODispatcher) {
            identifyInterceptor.transferInterceptedIdentify()
            pipeline.flush()
        }
    }

    private fun enqueue(payload: BaseEvent?) {
        payload?.let { event ->
            if (!event.isValid()) {
                amplitude.logger.warn("Event is invalid for missing information like userId and deviceId. Dropping event: ${event.eventType}")
                return
            }
            amplitude.amplitudeScope.launch(amplitude.storageIODispatcher) {
                val interceptedEvent = identifyInterceptor.intercept(event)
                interceptedEvent?.let {
                    enqueuePipeline(it)
                }
            }
        }
    }

    fun enqueuePipeline(event: BaseEvent) {
        pipeline.put(event)
    }

    override fun setup(amplitude: Amplitude) {
        super.setup(amplitude)
        val plugin = this

        with(amplitude) {
            pipeline = EventPipeline(
                amplitude,
                networkConnectivityChecker
            )
            pipeline.start()
            identifyInterceptor = IdentifyInterceptor(
                amplitude.identifyInterceptStorage,
                amplitude,
                logger,
                configuration,
                plugin
            )
        }
        add(IdentityEventSender())
    }
}
