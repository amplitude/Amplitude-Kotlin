package com.amplitude.eventbridge

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class EventBridgeTest {
    @Test
    fun `test addEventListener, sendEvent, listener called`() {
        val testEvent = Event("test")
        val identifyEvent = Event("identify")
        val eventBridge = EventBridgeImpl()
        var wasEventReceiverCalled = false
        eventBridge.setEventReceiver(
            EventChannel.EVENT,
            object : EventReceiver {
                override fun receive(
                    channel: EventChannel,
                    event: Event,
                ) {
                    assertEquals(event, testEvent)
                    wasEventReceiverCalled = true
                }
            },
        )
        eventBridge.setEventReceiver(
            EventChannel.IDENTIFY,
            object : EventReceiver {
                override fun receive(
                    channel: EventChannel,
                    event: Event,
                ) {
                    assertEquals(event, identifyEvent)
                    wasEventReceiverCalled = true
                }
            },
        )
        eventBridge.sendEvent(EventChannel.EVENT, testEvent)
        eventBridge.sendEvent(EventChannel.IDENTIFY, identifyEvent)
        assertTrue(wasEventReceiverCalled)
    }

    @Test
    fun `test sendEvent first, add listener later and listener receive event`() {
        val testEvent1 = Event("test1")
        val testEvent2 = Event("test2")
        val testEvent3 = Event("test3")
        val eventBridge = EventBridgeImpl()
        eventBridge.sendEvent(EventChannel.EVENT, testEvent1)
        eventBridge.sendEvent(EventChannel.EVENT, testEvent2)
        eventBridge.sendEvent(EventChannel.EVENT, testEvent3)
        var eventCount = 0
        eventBridge.setEventReceiver(
            EventChannel.EVENT,
            object : EventReceiver {
                override fun receive(
                    channel: EventChannel,
                    event: Event,
                ) {
                    eventCount++
                }
            },
        )
        assertEquals(3, eventCount)
    }
}
