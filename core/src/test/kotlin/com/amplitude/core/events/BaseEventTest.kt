package com.amplitude.core.events

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BaseEventTest {
    @Test
    fun `test merge options merge the value in options`() {
        val event = BaseEvent()
        event.userId = "userId-event"
        event.deviceId = "deviceId-event"
        event.sessionId = 111
        event.eventType = "test"
        event.country = "US"

        val eventOptions = EventOptions()
        val plan = Plan(branch = "test")
        val ingestionMetadata = IngestionMetadata(sourceName = "ampli")
        eventOptions.userId = "userId-options"
        eventOptions.sessionId = -1
        eventOptions.country = "DE"
        eventOptions.city = "city-options"
        eventOptions.plan = plan
        eventOptions.ingestionMetadata = ingestionMetadata

        event.mergeEventOptions(eventOptions)
        assertEquals("userId-options", event.userId)
        assertEquals("deviceId-event", event.deviceId)
        assertEquals(-1, event.sessionId)
        assertEquals("DE", event.country)
        assertEquals("city-options", event.city)
        assertEquals("test", event.eventType)
        assertEquals(plan, event.plan)
        assertEquals(ingestionMetadata, event.ingestionMetadata)
    }

    @Test
    fun `test isValid method`() {
        val event = BaseEvent()
        event.eventType = "test"
        assertFalse(event.isValid())
        event.userId = "user_id"
        assertTrue(event.isValid())
    }
}
