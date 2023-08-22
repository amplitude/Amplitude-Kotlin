package com.amplitude.core.utilities

import com.amplitude.common.jvm.ConsoleLogger
import com.amplitude.core.events.BaseEvent
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.concurrent.thread

class EventsFileManagerTest {
    @TempDir
    lateinit var root: File

    @Test
    fun `events should be stored and read`() {
        val storageKey = "\$default"
        var timestamp: Long = 0
        val manager = EventsFileManager(root, storageKey, ConsoleLogger()) { timestamp }
        runBlocking {
            timestamp = 100
            manager.storeEvent(createEvent(1))
            manager.rollover()
            timestamp = 200
            manager.storeEvent(createEvent(2))
            manager.storeEvent(createEvent(3))
            manager.rollover()
            timestamp = 300
            manager.storeEvent(createEvent(4))
        }
        val filePaths = manager.read()
        Assertions.assertEquals(2, filePaths.size)
        filePaths.withIndex().forEach {
            val file = File(it.value)
            Assertions.assertEquals(root.resolve(storageKey).absolutePath, file.parentFile.absolutePath)
            Assertions.assertEquals("0000000000${(it.index + 1) * 100}-${manager.id}", file.name)
        }

        val eventsString1 = manager.getEventString(filePaths[0])
        val events1 = JSONArray(eventsString1)
        Assertions.assertEquals(1, events1.length())
        Assertions.assertEquals("event-1", events1.getJSONObject(0).getString("event_type"))

        val eventsString2 = manager.getEventString(filePaths[1])
        val events2 = JSONArray(eventsString2)
        Assertions.assertEquals(2, events2.length())
        Assertions.assertEquals("event-2", events2.getJSONObject(0).getString("event_type"))
        Assertions.assertEquals("event-3", events2.getJSONObject(1).getString("event_type"))
    }

    @Test
    fun `rollover should finish current non-empty temp file`() {
        val storageKey = "\$default"
        val manager = EventsFileManager(root, storageKey, ConsoleLogger())
        runBlocking {
            manager.storeEvent(createEvent(1))
        }
        var filePaths = manager.read()
        Assertions.assertEquals(0, filePaths.size)

        runBlocking {
            manager.rollover()
        }

        filePaths = manager.read()
        Assertions.assertEquals(1, filePaths.size)
    }

    @Test
    fun `rollover should ignore current empty temp file`() {
        val storageKey = "\$default"
        val manager = EventsFileManager(root, storageKey, ConsoleLogger())
        var filePaths = manager.read()
        Assertions.assertEquals(0, filePaths.size)

        runBlocking {
            manager.rollover()
        }

        filePaths = manager.read()
        Assertions.assertEquals(0, filePaths.size)
    }

    @Test
    fun `remove should delete a file`() {
        val storageKey = "\$default"
        val manager = EventsFileManager(root, storageKey, ConsoleLogger())
        runBlocking {
            manager.storeEvent(createEvent(1))
            manager.rollover()
        }

        var filePaths = manager.read()
        Assertions.assertEquals(1, filePaths.size)

        manager.remove(filePaths[0])

        filePaths = manager.read()
        Assertions.assertEquals(0, filePaths.size)
    }

    @Test
    fun `previous event files (version 2) should be attached to current manager`() {
        val storageKey = "\$default"
        val storageDir = root.resolve(storageKey)
        storageDir.mkdir()
        val previousFile1 = File(storageDir, "1000000000001-abcd_xyz")
        previousFile1.appendText("{\"event_type\": \"event-1\"}\n")
        previousFile1.appendText("{\"event_type\": \"event-2\"}\n")
        val previousFile2 = File(storageDir, "1000000000002-abcd_xyz.tmp")
        previousFile2.appendText("{\"event_type\": \"event-3\"}\n")

        val manager = EventsFileManager(root, storageKey, ConsoleLogger())

        val filePaths = manager.read()
        Assertions.assertEquals(2, filePaths.size)
        filePaths.forEach {
            val file = File(it)
            Assertions.assertEquals(root.resolve(storageKey).absolutePath, file.parentFile.absolutePath)
        }
        Assertions.assertEquals("1000000000001-${manager.id}", File(filePaths[0]).name)
        Assertions.assertEquals("1000000000002-${manager.id}", File(filePaths[1]).name)

        val eventsString1 = manager.getEventString(filePaths[0])
        val events1 = JSONArray(eventsString1)
        Assertions.assertEquals(2, events1.length())
        Assertions.assertEquals("event-1", events1.getJSONObject(0).getString("event_type"))
        Assertions.assertEquals("event-2", events1.getJSONObject(1).getString("event_type"))

        val eventsString2 = manager.getEventString(filePaths[1])
        val events2 = JSONArray(eventsString2)
        Assertions.assertEquals(1, events2.length())
        Assertions.assertEquals("event-3", events2.getJSONObject(0).getString("event_type"))
    }

    @Test
    fun `previous event files (version 1) should be attached to current manager`() {
        val storageKey = "\$default"
        val previousFile1 = File(root, "$storageKey-1")
        previousFile1.writeText("[{\"event_type\": \"event-1\"},{\"event_type\": \"event-2\"}]")
        val previousFile2 = File(root, "$storageKey-3.tmp")
        previousFile2.writeText("[{\"event_type\": \"event-3\"},")

        val manager = EventsFileManager(root, storageKey, ConsoleLogger())

        val filePaths = manager.read()
        Assertions.assertEquals(2, filePaths.size)
        filePaths.forEach {
            val file = File(it)
            Assertions.assertEquals(root.resolve(storageKey).absolutePath, file.parentFile.absolutePath)
        }
        Assertions.assertEquals("0000000000001-${manager.id}", File(filePaths[0]).name)
        Assertions.assertEquals("0000000000003-${manager.id}", File(filePaths[1]).name)

        val eventsString1 = manager.getEventString(filePaths[0])
        val events1 = JSONArray(eventsString1)
        Assertions.assertEquals(2, events1.length())
        Assertions.assertEquals("event-1", events1.getJSONObject(0).getString("event_type"))
        Assertions.assertEquals("event-2", events1.getJSONObject(1).getString("event_type"))

        val eventsString2 = manager.getEventString(filePaths[1])
        val events2 = JSONArray(eventsString2)
        Assertions.assertEquals(1, events2.length())
        Assertions.assertEquals("event-3", events2.getJSONObject(0).getString("event_type"))
    }

    @Test
    fun `event files should not exceed max size`() {
        val storageKey = "\$default"
        var timestamp: Long = 0
        val manager = EventsFileManager(root, storageKey, ConsoleLogger()) { timestamp }
        val eventCount = EventsFileManager.MAX_FILE_SIZE / 1000
        runBlocking {
            for (i in 1..eventCount) {
                timestamp = (100 * i).toLong()
                manager.storeEvent(createEvent(i, 1000))
            }
            manager.rollover()
        }
        val filePaths = manager.read()
        Assertions.assertEquals(2, filePaths.size)
        filePaths.withIndex().forEach {
            val file = File(it.value)
            Assertions.assertEquals(root.resolve(storageKey).absolutePath, file.parentFile.absolutePath)
        }
        Assertions.assertEquals("0000000000100-${manager.id}", File(filePaths[0]).name)
        Assertions.assertEquals("0000000087300-${manager.id}", File(filePaths[1]).name)

        val eventsString1 = manager.getEventString(filePaths[0])
        val events1 = JSONArray(eventsString1)
        val eventsString2 = manager.getEventString(filePaths[1])
        val events2 = JSONArray(eventsString2)
        Assertions.assertEquals(eventCount, events1.length() + events2.length())
    }

    @Test
    fun `event files should be split`() {
        val storageKey = "\$default"
        val timestamp: Long = 100
        val manager = EventsFileManager(root, storageKey, ConsoleLogger()) { timestamp }
        val eventCount = 11
        val events = (1..eventCount).map { createEvent(it) }
        runBlocking {
            events.forEach { manager.storeEvent(it) }
            manager.rollover()
        }
        var filePaths = manager.read()
        Assertions.assertEquals(1, filePaths.size)
        val originalFile = File(filePaths[0])
        Assertions.assertEquals("0000000000100-${manager.id}", originalFile.name)
        Assertions.assertTrue(originalFile.exists())

        val jsonEvents = JSONArray(events.map { JSONUtil.eventToJsonObject(it) })
        manager.splitFile(filePaths[0], jsonEvents)

        filePaths = manager.read()
        Assertions.assertEquals(2, filePaths.size)
        Assertions.assertEquals("0000000000100-${manager.id}-1", File(filePaths[0]).name)
        Assertions.assertEquals("0000000000100-${manager.id}-2", File(filePaths[1]).name)

        val eventsString1 = manager.getEventString(filePaths[0])
        val events1 = JSONArray(eventsString1)
        Assertions.assertEquals(eventCount / 2, events1.length())
        val eventsString2 = manager.getEventString(filePaths[1])
        val events2 = JSONArray(eventsString2)
        Assertions.assertEquals(eventCount - eventCount / 2, events2.length())
    }

    @Test
    fun `recoverable corrupted event files (version 1) should be read`() {
        val storageKey = "\$default"
        val manager = EventsFileManager(root, storageKey, ConsoleLogger())
        val storageDirectory = root.resolve(storageKey)
        storageDirectory.resolve("0000000000100-${manager.id}").writeText("[[{\"event_type\": \"event-1\"}]")
        storageDirectory.resolve("0000000000200-${manager.id}").writeText("[{\"event_type\": \"event-2\"},{\"event_type\": \"event-3\"}]]")
        storageDirectory.resolve("0000000000300-${manager.id}").writeText("[[{\"event_type\": \"event-4\"},,")

        val filePaths = manager.read()
        Assertions.assertEquals(3, filePaths.size)
        filePaths.withIndex().forEach {
            val file = File(it.value)
            Assertions.assertEquals("0000000000${(it.index + 1) * 100}-${manager.id}", file.name)
        }

        val eventsString1 = manager.getEventString(filePaths[0])
        val events1 = JSONArray(eventsString1)
        Assertions.assertEquals(1, events1.length())
        Assertions.assertEquals("event-1", events1.getJSONObject(0).getString("event_type"))

        val eventsString2 = manager.getEventString(filePaths[1])
        val events2 = JSONArray(eventsString2)
        Assertions.assertEquals(2, events2.length())
        Assertions.assertEquals("event-2", events2.getJSONObject(0).getString("event_type"))
        Assertions.assertEquals("event-3", events2.getJSONObject(1).getString("event_type"))

        val eventsString3 = manager.getEventString(filePaths[2])
        val events3 = JSONArray(eventsString3)
        Assertions.assertEquals(1, events3.length())
        Assertions.assertEquals("event-4", events3.getJSONObject(0).getString("event_type"))
    }

    @Test
    fun `unrecoverable corrupted event files (version 1) should be skipped`() {
        val storageKey = "\$default"
        val manager = EventsFileManager(root, storageKey, ConsoleLogger())
        val storageDirectory = root.resolve(storageKey)
        storageDirectory.resolve("0000000000100-${manager.id}").writeText("[[{\"event_type\": \"event-1\"")
        storageDirectory.resolve("0000000000200-${manager.id}").writeText("[{\"event_type\": \"event-2\"},{\"event_type\": \"event-3\"]]")
        storageDirectory.resolve("0000000000300-${manager.id}").writeText("[[{\"event_type\": \"event-4\",")

        val filePaths = manager.read()
        Assertions.assertEquals(3, filePaths.size)
        filePaths.withIndex().forEach {
            val file = File(it.value)
            Assertions.assertEquals("0000000000${(it.index + 1) * 100}-${manager.id}", file.name)
        }

        val eventsString1 = manager.getEventString(filePaths[0])
        Assertions.assertTrue(eventsString1.isEmpty())

        val eventsString2 = manager.getEventString(filePaths[1])
        Assertions.assertTrue(eventsString2.isEmpty())

        val eventsString3 = manager.getEventString(filePaths[2])
        Assertions.assertTrue(eventsString3.isEmpty())
    }

    @Test
    fun `corrupted events (version 2) should be skipped`() {
        val storageKey = "\$default"
        val manager = EventsFileManager(root, storageKey, ConsoleLogger())
        val storageDirectory = root.resolve(storageKey)
        storageDirectory.resolve("0000000000100-${manager.id}").writeText("{\"event_type\": \"event-1\"")
        storageDirectory.resolve("0000000000200-${manager.id}").writeText("{\"event_type\": \"event-2\"}\n{\"event_type\": \"event-3}")
        storageDirectory.resolve("0000000000300-${manager.id}").writeText("{\"event_type\": \"event-4\"\n{\"event_type\": \"event-5\"}")

        val filePaths = manager.read()
        Assertions.assertEquals(3, filePaths.size)
        filePaths.withIndex().forEach {
            val file = File(it.value)
            Assertions.assertEquals("0000000000${(it.index + 1) * 100}-${manager.id}", file.name)
        }

        val eventsString1 = manager.getEventString(filePaths[0])
        Assertions.assertTrue(eventsString1.isEmpty())

        val eventsString2 = manager.getEventString(filePaths[1])
        val events2 = JSONArray(eventsString2)
        Assertions.assertEquals(1, events2.length())
        Assertions.assertEquals("event-2", events2.getJSONObject(0).getString("event_type"))

        val eventsString3 = manager.getEventString(filePaths[2])
        val events3 = JSONArray(eventsString3)
        Assertions.assertEquals(1, events3.length())
        Assertions.assertEquals("event-5", events3.getJSONObject(0).getString("event_type"))
    }

    @Test
    fun `concurrent writes to the same manager should not corrupt events`() {
        val storageKey = "\$default"
        val manager = EventsFileManager(root, storageKey, ConsoleLogger())
        val eventCount = 50_000
        val eventCount1 = eventCount / 2
        val eventCount2 = eventCount - eventCount / 2

        val thread1 = thread {
            for (i in 1..eventCount1) {
                runBlocking {
                    manager.storeEvent(createEvent(i, prefix = "thread1-"))
                }
            }
            runBlocking {
                manager.rollover()
            }
        }

        val thread2 = thread {
            for (i in 1..eventCount2) {
                runBlocking {
                    manager.storeEvent(createEvent(i, prefix = "thread2-"))
                }
            }
            runBlocking {
                manager.rollover()
            }
        }

        thread1.join()
        thread2.join()

        val filePaths = manager.read()
        var readEventCount1 = 0
        var readEventCount2 = 0
        filePaths.forEach {
            val eventString = manager.getEventString(it)
            val events = JSONArray(eventString)
            for (i in 0 until events.length()) {
                val event = events.getJSONObject(i)
                val eventType = event.getString("event_type")
                if (eventType.startsWith("thread1-")) {
                    readEventCount1++
                } else if (eventType.startsWith("thread2-")) {
                    readEventCount2++
                }
            }
        }

        Assertions.assertEquals(eventCount1, readEventCount1)
        Assertions.assertEquals(eventCount2, readEventCount2)
    }

    @Test
    fun `concurrent writes to the same directory should not corrupt events`() {
        val storageKey = "\$default"
        val eventCount1 = 50_000
        val manager1 = EventsFileManager(root, storageKey, ConsoleLogger())
        val eventCount2 = 30_000
        val manager2 = EventsFileManager(root, storageKey, ConsoleLogger())

        val thread1 = thread {
            for (i in 1..eventCount1) {
                runBlocking {
                    manager1.storeEvent(createEvent(i, prefix = "thread1-"))
                }
            }
            runBlocking {
                manager1.rollover()
            }
        }

        val thread2 = thread {
            for (i in 1..eventCount2) {
                runBlocking {
                    manager2.storeEvent(createEvent(i, prefix = "thread2-"))
                }
            }
            runBlocking {
                manager2.rollover()
            }
        }

        thread1.join()
        thread2.join()

        var filePaths = manager1.read()
        var readEventCount1 = 0
        var readEventCount2 = 0
        filePaths.forEach {
            val eventString = manager1.getEventString(it)
            val events = JSONArray(eventString)
            for (i in 0 until events.length()) {
                val event = events.getJSONObject(i)
                val eventType = event.getString("event_type")
                if (eventType.startsWith("thread1-")) {
                    readEventCount1++
                } else if (eventType.startsWith("thread2-")) {
                    readEventCount2++
                }
            }
        }

        Assertions.assertEquals(eventCount1, readEventCount1)
        Assertions.assertEquals(0, readEventCount2)

        filePaths = manager2.read()
        readEventCount1 = 0
        readEventCount2 = 0
        filePaths.forEach {
            val eventString = manager2.getEventString(it)
            val events = JSONArray(eventString)
            for (i in 0 until events.length()) {
                val event = events.getJSONObject(i)
                val eventType = event.getString("event_type")
                if (eventType.startsWith("thread1-")) {
                    readEventCount1++
                } else if (eventType.startsWith("thread2-")) {
                    readEventCount2++
                }
            }
        }

        Assertions.assertEquals(0, readEventCount1)
        Assertions.assertEquals(eventCount2, readEventCount2)
    }

    private fun createEvent(eventIndex: Int, propertySize: Int? = null, prefix: String = ""): BaseEvent {
        val event = BaseEvent()
        event.eventType = "${prefix}event-$eventIndex"
        if (propertySize != null) {
            event.eventProperties = mutableMapOf("property-1" to "a".repeat(propertySize))
        }
        return event
    }
}
