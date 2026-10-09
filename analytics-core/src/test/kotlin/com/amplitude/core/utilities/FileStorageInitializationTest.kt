package com.amplitude.core.utilities

import com.amplitude.common.jvm.ConsoleLogger
import com.amplitude.core.Storage
import com.amplitude.core.events.BaseEvent
import com.amplitude.id.utilities.PropertiesFile
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File
import java.util.UUID

class FileStorageInitializationTest {
    @Test
    fun `should load persisted version and index before constructing the file manager`() =
        runTest {
            val prefix = "ooms-${UUID.randomUUID()}"
            val storageKey = "test"
            val directory = File("/tmp/$prefix/$storageKey")
            val events = File(directory, "events").apply { mkdirs() }
            try {
                PropertiesFile(directory, "$prefix-$storageKey", null).apply {
                    load()
                    putLong("amplitude.events.file.version.$storageKey", 2)
                    putLong("amplitude.events.file.index.$storageKey", 23)
                    putString(Storage.Constants.APP_VERSION.rawVal, "existing")
                }
                // Invalid contents would be dropped by a migration scan. V2 must skip that scan.
                val existing = File(events, "$storageKey-22").apply { writeText("unparseable") }
                val storage = FileStorage(storageKey, ConsoleLogger(), prefix, Diagnostics())
                assertEquals("existing", storage.read(Storage.Constants.APP_VERSION))
                assertTrue(existing.exists())
                assertEquals("unparseable", existing.readText())
                storage.writeEvent(BaseEvent().apply { eventType = "first" })
                storage.rollover()
                assertTrue(File(events, "$storageKey-23").exists())
                val reconstructed = FileStorage(storageKey, ConsoleLogger(), prefix, Diagnostics())
                assertEquals("existing", reconstructed.read(Storage.Constants.APP_VERSION))
                assertTrue(existing.exists())
                reconstructed.writeEvent(BaseEvent().apply { eventType = "second" })
                reconstructed.rollover()
                assertTrue(File(events, "$storageKey-24").exists())
            } finally {
                directory.parentFile.deleteRecursively()
            }
        }
}
