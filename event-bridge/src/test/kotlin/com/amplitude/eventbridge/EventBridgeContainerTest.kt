package com.amplitude.eventbridge

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class EventBridgeContainerTest {

    @Test
    fun `test getInstance return same instance for certain name`() {
        val eventBridge1 = EventBridgeContainer.getInstance("testInstance")
        val eventBridge2 = EventBridgeContainer.getInstance("testInstance")
        Assertions.assertEquals(eventBridge1, eventBridge2)
    }
}
