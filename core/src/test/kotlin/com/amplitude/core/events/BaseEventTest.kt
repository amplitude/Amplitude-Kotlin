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
        event.eventType = "test"
        event.country = "US"
        val eventOptions = EventOptions()
        eventOptions.userId = "user_id"
        eventOptions.country = "DE"
        event.mergeEventOptions(eventOptions)
        assertEquals(event.userId, "user_id")
        assertEquals(event.country, "US")
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
