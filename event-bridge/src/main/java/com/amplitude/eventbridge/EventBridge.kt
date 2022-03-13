package com.amplitude.eventbridge

import java.util.concurrent.ArrayBlockingQueue

data class Event (
    val eventType: String,
    val eventProperties: Map<String, Any>? = null,
    val userProperties: Map<String, Any>? = null,
    val groups:Map<String, Any>? = null,
    var groupProperties: Map<String, Any>? = null
)

enum class EventChannel {
    EVENT, IDENTITY
}

interface EventReceiver {
    fun receive(channel: EventChannel, event: Event)
}

interface EventBridge {
    fun sendEvent(channel: EventChannel, event: Event)
    fun setEventReceiver(channel: EventChannel, receiver: EventReceiver)
}

internal class EventBridgeImpl : EventBridge {
    private val lock = Any()
    private val channels = mutableMapOf<EventChannel, EventBridgeChannel>()

    override fun sendEvent(channel: EventChannel, event: Event) {
        synchronized(lock) {
            channels.getOrPut(channel) {
                EventBridgeChannel(channel)
            }
        }.sendEvent(event)
    }

    override fun setEventReceiver(channel: EventChannel, receiver: EventReceiver) {
        synchronized(lock) {
            channels.getOrPut(channel) {
                EventBridgeChannel(channel)
            }
        }.setEventReceiver(receiver)
    }
}

internal class EventBridgeChannel(private val channel: EventChannel) {
    companion object {
        const val QUEUE_CAPACITY = 512
    }

    private val lock = Any()
    private var receiver: EventReceiver? = null
    private val queue = ArrayBlockingQueue<Event>(QUEUE_CAPACITY)

    fun sendEvent(event: Event) {
        synchronized(lock) {
            if (this.receiver == null) {
                queue.offer(event)
            }
            this.receiver
        }?.receive(channel, event)
    }

    fun setEventReceiver(receiver: EventReceiver?) {
        synchronized(lock) {
            if (this.receiver != null) {
                // Only allow one receiver per channel now
                return
            }
            this.receiver = receiver
            mutableListOf<Event>().apply {
                queue.drainTo(this)
            }
        }.forEach { event ->
            receiver?.receive(channel, event)
        }
    }
}
