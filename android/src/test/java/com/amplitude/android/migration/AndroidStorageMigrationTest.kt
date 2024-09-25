package com.amplitude.android.migration

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.amplitude.android.utilities.AndroidStorage
import com.amplitude.common.jvm.ConsoleLogger
import com.amplitude.core.Storage
import com.amplitude.core.events.BaseEvent
import com.amplitude.core.utilities.Diagnostics
import com.amplitude.core.utilities.toEvents
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.junit.Assert
import org.junit.Test
import org.junit.jupiter.api.Assertions
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
class AndroidStorageMigrationTest {
    private val testDiagnostics = Diagnostics()

    @Test
    fun `simple values should be migrated`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val logger = ConsoleLogger()

        val source = AndroidStorage(context, UUID.randomUUID().toString(), logger, null, testDiagnostics)
        val destination = AndroidStorage(context, UUID.randomUUID().toString(), logger, null, testDiagnostics)
        val sourceFileIndexKey = "amplitude.events.file.index.${source.storageKey}"
        val destinationFileIndexKey = "amplitude.events.file.index.${destination.storageKey}"

        runBlocking {
            source.write(Storage.Constants.PREVIOUS_SESSION_ID, "123")
            source.write(Storage.Constants.LAST_EVENT_TIME, "456")
            source.write(Storage.Constants.LAST_EVENT_ID, "789")
        }
        source.sharedPreferences.edit().putLong(sourceFileIndexKey, 1234567).commit()

        var destinationPreviousSessionId = destination.read(Storage.Constants.PREVIOUS_SESSION_ID)
        var destinationLastEventTime = destination.read(Storage.Constants.LAST_EVENT_TIME)
        var destinationLastEventId = destination.read(Storage.Constants.LAST_EVENT_ID)
        var destinationFileIndex = destination.sharedPreferences.getLong(destinationFileIndexKey, -1)

        Assertions.assertNull(destinationPreviousSessionId)
        Assertions.assertNull(destinationLastEventTime)
        Assertions.assertNull(destinationLastEventId)
        Assertions.assertEquals(-1, destinationFileIndex)

        val migration = AndroidStorageMigration(source.storageV2, destination.storageV2, logger)
        runBlocking {
            migration.execute()
        }

        val sourcePreviousSessionId = source.read(Storage.Constants.PREVIOUS_SESSION_ID)
        val sourceLastEventTime = source.read(Storage.Constants.LAST_EVENT_TIME)
        val sourceLastEventId = source.read(Storage.Constants.LAST_EVENT_ID)
        val sourceFileIndex = source.sharedPreferences.getLong(sourceFileIndexKey, -1)

        Assertions.assertNull(sourcePreviousSessionId)
        Assertions.assertNull(sourceLastEventTime)
        Assertions.assertNull(sourceLastEventId)
        Assertions.assertEquals(-1, sourceFileIndex)

        destinationPreviousSessionId = destination.read(Storage.Constants.PREVIOUS_SESSION_ID)
        destinationLastEventTime = destination.read(Storage.Constants.LAST_EVENT_TIME)
        destinationLastEventId = destination.read(Storage.Constants.LAST_EVENT_ID)

        Assertions.assertEquals("123", destinationPreviousSessionId)
        Assertions.assertEquals("456", destinationLastEventTime)
        Assertions.assertEquals("789", destinationLastEventId)
    }

    @Test
    fun `event files should be migrated`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val logger = ConsoleLogger()

        val source = AndroidStorage(context, UUID.randomUUID().toString(), logger, null, testDiagnostics)
        val destination = AndroidStorage(context, UUID.randomUUID().toString(), logger, null, testDiagnostics)

        runBlocking {
            source.writeEvent(createEvent(1))
            source.writeEvent(createEvent(22))
            source.rollover()
            source.writeEvent(createEvent(333))
            source.rollover()
            source.writeEvent(createEvent(4444))
            source.rollover()
        }

        var sourceEventFiles = source.readEventsContent() as List<String>
        Assertions.assertEquals(3, sourceEventFiles.size)

        var destinationEventFiles = destination.readEventsContent() as List<String>
        Assertions.assertEquals(0, destinationEventFiles.size)

        val migration = AndroidStorageMigration(source.storageV2, destination.storageV2, logger)
        runBlocking {
            migration.execute()
        }

        sourceEventFiles = source.readEventsContent() as List<String>
        Assertions.assertEquals(0, sourceEventFiles.size)

        runBlocking {
            val events = getEventsFromStorage(destination)
            Assertions.assertEquals(4, events.size)
            Assert.assertEquals("event-1", events[0].eventType)
            Assert.assertEquals("event-22", events[1].eventType)
            Assert.assertEquals("event-333", events[2].eventType)
            Assert.assertEquals("event-4444", events[3].eventType)
        }
    }

    @Test
    fun `missing source should not fail`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val logger = ConsoleLogger()

        val source = AndroidStorage(context, UUID.randomUUID().toString(), logger, null, testDiagnostics)
        val destination = AndroidStorage(context, UUID.randomUUID().toString(), logger, null, testDiagnostics)

        var destinationPreviousSessionId = destination.read(Storage.Constants.PREVIOUS_SESSION_ID)
        var destinationLastEventTime = destination.read(Storage.Constants.LAST_EVENT_TIME)
        var destinationLastEventId = destination.read(Storage.Constants.LAST_EVENT_ID)

        Assertions.assertNull(destinationPreviousSessionId)
        Assertions.assertNull(destinationLastEventTime)
        Assertions.assertNull(destinationLastEventId)

        val migration = AndroidStorageMigration(source.storageV2, destination.storageV2, logger)
        runBlocking {
            migration.execute()
        }

        destinationPreviousSessionId = destination.read(Storage.Constants.PREVIOUS_SESSION_ID)
        destinationLastEventTime = destination.read(Storage.Constants.LAST_EVENT_TIME)
        destinationLastEventId = destination.read(Storage.Constants.LAST_EVENT_ID)

        Assertions.assertNull(destinationPreviousSessionId)
        Assertions.assertNull(destinationLastEventTime)
        Assertions.assertNull(destinationLastEventId)

        val destinationEventFiles = destination.readEventsContent() as List<String>
        Assertions.assertEquals(0, destinationEventFiles.size)
    }

    @Test
    fun `event files with duplicated names should be migrated`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val logger = ConsoleLogger()

        val source = AndroidStorage(context, UUID.randomUUID().toString(), logger, null, testDiagnostics)
        val destination = AndroidStorage(context, UUID.randomUUID().toString(), logger, null, testDiagnostics)

        runBlocking {
            source.writeEvent(createEvent(1))
            source.rollover()
            source.writeEvent(createEvent(22))
            source.rollover()
        }

        val sourceEventFiles = source.readEventsContent() as List<String>
        Assertions.assertEquals(2, sourceEventFiles.size)

        runBlocking {
            destination.writeEvent(createEvent(333))
            destination.rollover()
        }

        var destinationEventFiles = destination.readEventsContent() as List<String>
        Assertions.assertEquals(1, destinationEventFiles.size)

        val migration = AndroidStorageMigration(source.storageV2, destination.storageV2, logger)
        runBlocking {
            migration.execute()
        }

        runBlocking {
            val events = getEventsFromStorage(destination)
            Assertions.assertEquals(3, events.size)
            Assert.assertEquals("event-333", events[0].eventType)
            Assert.assertEquals("event-1", events[1].eventType)
            Assert.assertEquals("event-22", events[2].eventType)
        }

    }

    private suspend fun getEventsFromStorage(storage: Storage): List<BaseEvent> {
        val files = storage.readEventsContent() as List<String>
        val events = mutableListOf<BaseEvent>()
        for (file in files) {
            val content = JSONArray(storage.getEventsString(file))
            events.addAll(content.toEvents())
        }
        return events
    }

    private fun createEvent(eventIndex: Int): BaseEvent {
        val event = BaseEvent()
        event.eventType = "event-$eventIndex"
        return event
    }
}
