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

    @Test
    fun `remove only drops the container when it is still the expected one`() {
        val name = "ownership-instance"
        val first = EventBridgeContainer.getInstance(name)

        // First instance shuts down and frees its slot; a same-named rebuild gets a fresh one.
        EventBridgeContainer.remove(name, first)
        val second = EventBridgeContainer.getInstance(name)
        Assertions.assertNotSame(first, second)

        // A late teardown from the superseded first instance must NOT delete the new container.
        EventBridgeContainer.remove(name, first)
        Assertions.assertSame(second, EventBridgeContainer.getInstance(name))
    }
}
