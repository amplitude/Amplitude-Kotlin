package com.amplitude.core.utilities

import com.amplitude.common.jvm.ConsoleLogger
import com.amplitude.id.utilities.PropertiesFile
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
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
}
