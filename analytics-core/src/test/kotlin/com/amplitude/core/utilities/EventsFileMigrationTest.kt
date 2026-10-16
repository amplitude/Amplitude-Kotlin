package com.amplitude.core.utilities

import com.amplitude.common.jvm.ConsoleLogger
import com.amplitude.id.utilities.PropertiesFile
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.io.File
import java.io.FilenameFilter

class EventsFileMigrationTest {
    @TempDir lateinit var directory: File
    private val storageKey = "migration"
    private val versionKey = "amplitude.events.file.version.$storageKey"
    private val logger = ConsoleLogger()

    @Test
    fun `should skip a file deleted between enumeration and opening and migrate the remaining file`() {
        val missing = File(directory, "$storageKey-0").apply { writeText("[]") }
        val disappearing =
            object : File(missing.path) {
                override fun getPath(): String {
                    missing.delete()
                    return super.getPath()
                }
            }
        val remaining = File(directory, "$storageKey-1").apply { writeText("[{\"event_type\":\"legacy\"}]") }
        val listing =
            object : File(directory.path) {
                override fun listFiles(filter: FilenameFilter?): Array<File> = arrayOf(disappearing, remaining)
            }
        val kvs = PropertiesFile(directory, "metadata", null).apply { load() }
        EventsFileManager(listing, storageKey, kvs, logger, Diagnostics())
        assertFalse(missing.exists())
        assertEquals("{\"event_type\":\"legacy\"}" + EventsFileManager.DELIMITER, remaining.readText())
        assertEquals(2, kvs.getLong(versionKey, 1))
    }

    @Test
    fun `should continue migration and retry unreadable files on the next initialization`() {
        // Opening a directory as text raises FileNotFoundException while exists remains true.
        val unreadable = File(directory, "$storageKey-0").apply { mkdir() }
        val remaining = File(directory, "$storageKey-1").apply { writeText("[{\"event_type\":\"legacy\"}]") }
        val kvs = PropertiesFile(directory, "metadata", null).apply { load() }
        EventsFileManager(directory, storageKey, kvs, logger, Diagnostics())
        assertTrue(remaining.readText().endsWith(EventsFileManager.DELIMITER))
        assertEquals(1, kvs.getLong(versionKey, 1))
        unreadable.delete()
        unreadable.writeText("[{\"event_type\":\"recovered\"}]")
        EventsFileManager(directory, storageKey, kvs, logger, Diagnostics())
        assertEquals("{\"event_type\":\"recovered\"}" + EventsFileManager.DELIMITER, unreadable.readText())
        assertEquals(2, kvs.getLong(versionKey, 1))
    }

    @ParameterizedTest
    @ValueSource(strings = ["complete", "truncated"])
    fun `should preserve V2 files without a trailing delimiter when retrying migration`(tail: String) =
        runTest {
            val unreadable = File(directory, "$storageKey-0").apply { mkdir() }
            val legacy = File(directory, "$storageKey-1").apply { writeText("[{\"event_type\":\"legacy\"}]") }
            val kvs = PropertiesFile(directory, "metadata", null).apply { load() }
            EventsFileManager(directory, storageKey, kvs, logger, Diagnostics())
            assertEquals(1, kvs.getLong(versionKey, 1))

            val completeEvent = """{"event_type":"first"}"""
            val trailingEvent =
                if (tail == "complete") """{"event_type":"second"}""" else """{"event_type":"second""""
            val payload = completeEvent + EventsFileManager.DELIMITER + trailingEvent
            val v2File = File(directory, "$storageKey-2").apply { writeText(payload) }
            unreadable.delete()
            unreadable.writeText("[{\"event_type\":\"recovered\"}]")

            val reconstructed = EventsFileManager(directory, storageKey, kvs, logger, Diagnostics())

            assertTrue(v2File.exists())
            assertEquals(payload, v2File.readText())
            assertEquals("{\"event_type\":\"legacy\"}" + EventsFileManager.DELIMITER, legacy.readText())
            assertEquals("{\"event_type\":\"recovered\"}" + EventsFileManager.DELIMITER, unreadable.readText())
            assertEquals(2, kvs.getLong(versionKey, 1))
            val expectedEvents = if (tail == "complete") "[$completeEvent,$trailingEvent]" else "[$completeEvent]"
            assertEquals(expectedEvents, reconstructed.getEventString(v2File.path))
        }
}
