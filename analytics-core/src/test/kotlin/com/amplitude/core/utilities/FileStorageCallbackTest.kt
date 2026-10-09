package com.amplitude.core.utilities

import com.amplitude.common.jvm.ConsoleLogger
import com.amplitude.core.EventCallBack
import com.amplitude.core.events.BaseEvent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.io.File
import java.util.UUID

class FileStorageCallbackTest {
    @Test
    fun `should retrieve registered callbacks and return null after cleanup`() =
        runTest {
            val prefix = "ooms-${UUID.randomUUID()}"
            val storage = FileStorage("test", ConsoleLogger(), prefix, Diagnostics())
            val receivedEvents = mutableListOf<String>()
            val callback: EventCallBack = { event, _, _ -> receivedEvents.add(event.eventType) }
            val event =
                BaseEvent().apply {
                    eventType = "purchase"
                    insertId = UUID.randomUUID().toString()
                    this.callback = callback
                }
            try {
                assertNull(storage.getEventCallback(event.insertId!!))
                storage.writeEvent(event)
                storage.getEventCallback(event.insertId!!)!!(event, 200, "Event sent success.")
                assertEquals(listOf("purchase"), receivedEvents)
                storage.removeEventCallback(event.insertId!!)
                assertNull(storage.getEventCallback(event.insertId!!))
            } finally {
                File("/tmp/$prefix").deleteRecursively()
            }
        }
}
