package com.amplitude.android.migration

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.amplitude.android.utilities.AndroidStorage
import com.amplitude.common.jvm.ConsoleLogger
import com.amplitude.core.Storage
import com.amplitude.core.events.BaseEvent
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.jupiter.api.Assertions
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
class StorageKeyMigrationTest {
    @Test
    fun `simple values should be migrated`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val logger = ConsoleLogger()

        val source = AndroidStorage(context, UUID.randomUUID().toString(), logger, null)
        val destination = AndroidStorage(context, UUID.randomUUID().toString(), logger, null)
        val sourceFileIndexKey = "amplitude.events.file.index.${source.storageKey}"
        val destinationFileIndexKey = "amplitude.events.file.index.${destination.storageKey}"

        source.write(Storage.Constants.PREVIOUS_SESSION_ID, "123")
        source.write(Storage.Constants.LAST_EVENT_TIME, "456")
        source.write(Storage.Constants.LAST_EVENT_ID, "789")
        source.sharedPreferences.edit().putLong(sourceFileIndexKey, 1234567).commit()

        var destinationPreviousSessionId = destination.read(Storage.Constants.PREVIOUS_SESSION_ID)
        var destinationLastEventTime = destination.read(Storage.Constants.LAST_EVENT_TIME)
        var destinationLastEventId = destination.read(Storage.Constants.LAST_EVENT_ID)
        var destinationFileIndex = destination.sharedPreferences.getLong(destinationFileIndexKey, -1)

        Assertions.assertNull(destinationPreviousSessionId)
        Assertions.assertNull(destinationLastEventTime)
        Assertions.assertNull(destinationLastEventId)
        Assertions.assertEquals(-1, destinationFileIndex)

        val migration = StorageKeyMigration(source, destination, logger)
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
        destinationFileIndex = destination.sharedPreferences.getLong(destinationFileIndexKey, -1)

        Assertions.assertEquals("123", destinationPreviousSessionId)
        Assertions.assertEquals("456", destinationLastEventTime)
        Assertions.assertEquals("789", destinationLastEventId)
        Assertions.assertEquals(1234567, destinationFileIndex)
    }

    @Test
    fun `event files should be migrated`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val logger = ConsoleLogger()

        val source = AndroidStorage(context, UUID.randomUUID().toString(), logger, null)
        val destination = AndroidStorage(context, UUID.randomUUID().toString(), logger, null)

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

        val sourceFileSizes = sourceEventFiles.map { File(it).length() }

        var destinationEventFiles = destination.readEventsContent() as List<String>
        Assertions.assertEquals(0, destinationEventFiles.size)

        val migration = StorageKeyMigration(source, destination, logger)
        runBlocking {
            migration.execute()
        }

        sourceEventFiles = source.readEventsContent() as List<String>
        Assertions.assertEquals(0, sourceEventFiles.size)

        destinationEventFiles = destination.readEventsContent() as List<String>
        Assertions.assertEquals(3, destinationEventFiles.size)

        for ((index, destinationEventFile) in destinationEventFiles.withIndex()) {
            val fileSize = File(destinationEventFile).length()
            Assertions.assertEquals(sourceFileSizes[index], fileSize)
        }
    }

    @Test
    fun `missing source should not fail`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val logger = ConsoleLogger()

        val source = AndroidStorage(context, UUID.randomUUID().toString(), logger, null)
        val destination = AndroidStorage(context, UUID.randomUUID().toString(), logger, null)

        var destinationPreviousSessionId = destination.read(Storage.Constants.PREVIOUS_SESSION_ID)
        var destinationLastEventTime = destination.read(Storage.Constants.LAST_EVENT_TIME)
        var destinationLastEventId = destination.read(Storage.Constants.LAST_EVENT_ID)

        Assertions.assertNull(destinationPreviousSessionId)
        Assertions.assertNull(destinationLastEventTime)
        Assertions.assertNull(destinationLastEventId)

        val migration = StorageKeyMigration(source, destination, logger)
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

        val source = AndroidStorage(context, UUID.randomUUID().toString(), logger, null)
        val destination = AndroidStorage(context, UUID.randomUUID().toString(), logger, null)

        runBlocking {
            source.writeEvent(createEvent(1))
            source.rollover()
            source.writeEvent(createEvent(22))
            source.rollover()
        }

        val sourceEventFiles = source.readEventsContent() as List<String>
        Assertions.assertEquals(2, sourceEventFiles.size)

        val sourceFileSizes = sourceEventFiles.map { File(it).length() }

        runBlocking {
            destination.writeEvent(createEvent(333))
            destination.rollover()
        }

        var destinationEventFiles = destination.readEventsContent() as List<String>
        Assertions.assertEquals(1, destinationEventFiles.size)

        val destinationFileSizes = destinationEventFiles.map { File(it).length() }

        val migration = StorageKeyMigration(source, destination, logger)
        runBlocking {
            migration.execute()
        }

        destinationEventFiles = destination.readEventsContent() as List<String>
        Assertions.assertEquals("-0", destinationEventFiles[0].substring(destinationEventFiles[0].length - 2))
        Assertions.assertTrue(destinationEventFiles[1].contains("-0-"))
        Assertions.assertEquals("-1", destinationEventFiles[2].substring(destinationEventFiles[0].length - 2))
        Assertions.assertEquals(destinationFileSizes[0], File(destinationEventFiles[0]).length())
        Assertions.assertEquals(sourceFileSizes[0], File(destinationEventFiles[1]).length())
        Assertions.assertEquals(sourceFileSizes[1], File(destinationEventFiles[2]).length())
    }

    private fun createEvent(eventIndex: Int): BaseEvent {
        val event = BaseEvent()
        event.eventType = "event-$eventIndex"
        return event
    }
}
