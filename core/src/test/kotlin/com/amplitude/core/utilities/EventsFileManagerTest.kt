package com.amplitude.core.utilities

import com.amplitude.common.jvm.ConsoleLogger
import com.amplitude.id.utilities.PropertiesFile
import kotlin.concurrent.thread
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

private const val STORAGE_KEY = "storageKey"

class EventsFileManagerTest {
    @TempDir lateinit var tempDir: File
    private val propertiesFile by lazy {
        PropertiesFile(
            tempDir, "test-prefix-$STORAGE_KEY", logger
        )
    }
    private val logger = ConsoleLogger()
    private val testDiagnostics = Diagnostics()
    private val eventsFileManager: EventsFileManager by lazy {
        EventsFileManager(
            directory = tempDir,
            storageKey = STORAGE_KEY,
            kvs = propertiesFile,
            logger = logger,
            diagnostics = testDiagnostics
        )
    }

    @Test
    fun `store event and read`() = runBlocking {
        eventsFileManager.storeEvent(createEvent("test1"))
        eventsFileManager.storeEvent(createEvent("test2"))
        eventsFileManager.rollover()
        eventsFileManager.storeEvent(createEvent("test3"))
        eventsFileManager.storeEvent(createEvent("test4"))
        eventsFileManager.rollover()
        eventsFileManager.storeEvent(createEvent("test5"))
        val filePaths = eventsFileManager.read()
        assertEquals(2, filePaths.size)
        filePaths.withIndex().forEach { (index, filePath) ->
            // verify file name and raw content
            val file = File(filePath)
            assertEquals("$STORAGE_KEY-$index", file.name)
            val content = file.readText()
            val lines = content.split(EventsFileManager.DELIMITER)
            assertEquals(3, lines.size)
            assertEquals(createEvent("test${index * 2 + 1}"), lines[0])
            assertEquals(createEvent("test${index * 2 + 2}"), lines[1])
            assertEquals("", lines[2])
        }

        // verify the content read from the file
        val eventsString0 = eventsFileManager.getEventString(filePaths[0])
        val eventsString1 = eventsFileManager.getEventString(filePaths[1])
        val events0 = JSONArray(eventsString0)
        val events1 = JSONArray(eventsString1)
        assertEquals(2, events0.length())
        assertEquals(2, events1.length())
        assertEquals("test1", events0.getJSONObject(0).getString("eventType"))
        assertEquals("test2", events0.getJSONObject(1).getString("eventType"))
        assertEquals("test3", events1.getJSONObject(0).getString("eventType"))
        assertEquals("test4", events1.getJSONObject(1).getString("eventType"))
    }

    @Test
    fun `rollover should finish current non-empty temp file`() = runBlocking {
        eventsFileManager.storeEvent(createEvent("test1"))
        val filePaths = eventsFileManager.read()
        assertEquals(0, filePaths.size)
        eventsFileManager.rollover()
        val filePathsAfterRollover = eventsFileManager.read()
        assertEquals(1, filePathsAfterRollover.size)
        val file = File(filePathsAfterRollover[0])
        val content = file.readText()
        val lines = content.split(EventsFileManager.DELIMITER)
        assertEquals(2, lines.size)
        assertEquals(createEvent("test1"), lines[0])
        assertEquals("", lines[1])
        val eventsString = eventsFileManager.getEventString(filePathsAfterRollover[0])
        val events = JSONArray(eventsString)
        assertEquals(1, events.length())
        assertEquals("test1", events.getJSONObject(0).getString("eventType"))
    }

    @Test
    fun `rollover should ignore current empty temp file`() = runBlocking {
        eventsFileManager.rollover()
        val filePathsAfterRollover = eventsFileManager.read()
        assertEquals(0, filePathsAfterRollover.size)
    }

    @Test
    fun `remove should delete a file`() = runBlocking {
        eventsFileManager.storeEvent(createEvent("test1"))
        eventsFileManager.rollover()
        val filePaths = eventsFileManager.read()
        assertEquals(1, filePaths.size)
        eventsFileManager.remove(filePaths[0])
        val filePathsAfterRemove = eventsFileManager.read()
        assertEquals(0, filePathsAfterRemove.size)
    }

    @Test
    fun `split`() = runBlocking {
        eventsFileManager.storeEvent(createEvent("test1"))
        eventsFileManager.storeEvent(createEvent("test2"))
        eventsFileManager.rollover()
        val filePaths = eventsFileManager.read()
        assertEquals(1, filePaths.size)
        runBlocking {
            val eventsString = eventsFileManager.getEventString(filePaths[0])
            val events = JSONArray(eventsString)
            assertEquals(2, events.length())
            eventsFileManager.splitFile(filePaths[0], events)
        }
        val filePathsAfterSplit = eventsFileManager.read()
        assertEquals(2, filePathsAfterSplit.size)
        val file0 = File(filePathsAfterSplit[0])
        val content0 = file0.readText()
        val lines0 = content0.split(EventsFileManager.DELIMITER)
        assertEquals(2, lines0.size)
        assertEquals(createEvent("test1"), lines0[0])
        assertEquals("", lines0[1])
        val file1 = File(filePathsAfterSplit[1])
        val content1 = file1.readText()
        val lines1 = content1.split(EventsFileManager.DELIMITER)
        assertEquals(2, lines1.size)
        assertEquals(createEvent("test2"), lines1[0])
        assertEquals("", lines1[1])
    }

    @Test
    fun `verify delimiter handled gracefully`() = runBlocking {
        val file0 = File(tempDir, "$STORAGE_KEY-0")
        file0.writeText("{\"eventType\":\"test1\"}\u0000{\"eventType\":\"test2\"}\u0000")
        val filePaths = eventsFileManager.read()
        assertEquals(1, filePaths.size)
        val eventsString = eventsFileManager.getEventString(filePaths[0])
        val events = JSONArray(eventsString)
        assertEquals(2, events.length())
        assertEquals("test1", events.getJSONObject(0).getString("eventType"))
        assertEquals("test2", events.getJSONObject(1).getString("eventType"))
    }

    @Test
    fun `verify malformed event shows up in diagnostics`() = runBlocking {
        val file0 = File(tempDir, "$STORAGE_KEY-0")
        file0.writeText(
            "{\"eventType\":\"test1\"}\u0000{\"eventType\":\"test2\"}\u0000{\"eventType\":\"test3\"\u0000"
        )
        val logger = ConsoleLogger()
        val propertiesFile = PropertiesFile(tempDir, "test-prefix-$STORAGE_KEY", logger)
        val diagnostics = Diagnostics()
        val eventsFileManager =
            EventsFileManager(tempDir, STORAGE_KEY, propertiesFile, logger, diagnostics)
        val filePaths = eventsFileManager.read()
        assertEquals(1, filePaths.size)
        val eventsString = eventsFileManager.getEventString(filePaths[0])
        val events = JSONArray(eventsString)
        assertEquals(2, events.length())
        assertEquals("test1", events.getJSONObject(0).getString("eventType"))
        assertEquals("test2", events.getJSONObject(1).getString("eventType"))
        assertEquals(
            "{\"malformed_events\":[\"{\\\"eventType\\\":\\\"test3\\\"\"]}",
            diagnostics.extractDiagnostics()
        )
    }

    @Test
    fun `verify delimiter in event names`() = runBlocking {
        eventsFileManager.storeEvent(createEvent("test1"))
        eventsFileManager.storeEvent(createEvent("test2\u0000"))
        eventsFileManager.rollover()
        val filePaths = eventsFileManager.read()
        assertEquals(1, filePaths.size)
        val eventsString = eventsFileManager.getEventString(filePaths[0])
        val events = JSONArray(eventsString)
        assertEquals(2, events.length())
        assertEquals("test1", events.getJSONObject(0).getString("eventType"))
        assertEquals("test2", events.getJSONObject(1).getString("eventType"))
    }

    @Test
    fun `could handle earlier version of events file`() = runBlocking {
        createEarlierVersionEventFiles()
        val filePaths = eventsFileManager.read()
        assertEquals(7, filePaths.size)
        filePaths.withIndex().forEach { (index, filePath) ->
            val file = File(filePath)
            assertTrue(
                file.extension.isEmpty(), "file extension should be empty for v1 event files"
            )
            // verify file format updated to v2
            val content = file.readText()
            val lines = content.split(EventsFileManager.DELIMITER)
            if (index == 5) {
                assertEquals(2, lines.size)
                assertEquals("{\"eventType\":\"test11\"}", lines[0])
            } else {
                assertEquals(3, lines.size)
                assertEquals("{\"eventType\":\"test${index * 2 + 1}\"}", lines[0])
                assertEquals("{\"eventType\":\"test${index * 2 + 2}\"}", lines[1])
            }

            val eventsString = eventsFileManager.getEventString(filePath)
            if (index == 5) {
                assertEquals("[{\"eventType\":\"test11\"}]", eventsString)
            } else {
                val events = JSONArray(eventsString)
                assertEquals(2, events.length())
                assertEquals(
                    "test${index * 2 + 1}",
                    events.getJSONObject(0).getString("eventType"),
                )
                assertEquals(
                    "test${index * 2 + 2}",
                    events.getJSONObject(1).getString("eventType"),
                )
            }
        }
    }

    @Test
    fun `could handle earlier versions with name conflict and new events`() = runBlocking {
        createEarlierVersionEventFiles()
        val file = File(tempDir, "$STORAGE_KEY-6")
        file.writeText("{\"eventType\":\"test15\"},{\"eventType\":\"test16\"}]")
        eventsFileManager.storeEvent(createEvent("test17"))
        eventsFileManager.storeEvent(createEvent("test18"))
        eventsFileManager.rollover()
        var eventsCount = 0
        val filePaths = eventsFileManager.read()
        filePaths.forEach { filePath ->
            val eventsString = eventsFileManager.getEventString(filePath)
            val events = JSONArray(eventsString)
            eventsCount += events.length()
        }
        assertEquals(17, eventsCount)
    }

    @Test
    fun `could handle earlier versions with line break in event name`() = runBlocking {
        val file = File(tempDir, "$STORAGE_KEY-6")
        file.writeText("{\"eventType\":\"test15\"},{\"eventType\":\"test16\\nsuffix\"}]")
        val filePaths = eventsFileManager.read()
        assertEquals(1, filePaths.size)
        val eventsString = eventsFileManager.getEventString(filePaths[0])
        val events = JSONArray(eventsString)
        assertEquals(2, events.length())
        assertEquals("test15", events.getJSONObject(0).getString("eventType"))
        assertEquals("test16\nsuffix", events.getJSONObject(1).getString("eventType"))
    }

    @Test
    fun `concurrent writes to the same event file manager instance`() = runBlocking {
        val job1 = GlobalScope.launch {
            eventsFileManager.storeEvent(createEvent("test1"))
            eventsFileManager.storeEvent(createEvent("test2"))
            eventsFileManager.rollover()
        }
        val job2 = GlobalScope.launch {
            eventsFileManager.rollover()
            eventsFileManager.storeEvent(createEvent("test3"))
            eventsFileManager.storeEvent(createEvent("test4"))
            eventsFileManager.rollover()
        }
        val job3 = GlobalScope.launch {
            eventsFileManager.rollover()
            eventsFileManager.storeEvent(createEvent("test5"))
            eventsFileManager.storeEvent(createEvent("test6"))
            eventsFileManager.rollover()
        }
        kotlinx.coroutines.joinAll(job1, job2, job3)
        val filePaths = eventsFileManager.read()
        var eventsCount = 0
        filePaths.forEach { filePath ->
            val eventsString = eventsFileManager.getEventString(filePath)
            val events = JSONArray(eventsString)
            eventsCount += events.length()
        }
        assertEquals(6, eventsCount)
    }

    @Test
    fun `concurrent write from multiple threads`() = runBlocking {
        for (i in 0..100) {
            val thread = thread {
                runBlocking {
                    for (d in 0..10) {
                        eventsFileManager.storeEvent(createEvent("test$i-$d"))
                    }
                    eventsFileManager.rollover()
                }
            }
            thread.join()
        }
        val filePaths = eventsFileManager.read()
        var eventsCount = 0
        filePaths.forEach { filePath ->
            val eventsString = eventsFileManager.getEventString(filePath)
            val events = JSONArray(eventsString)
            eventsCount += events.length()
        }
        assertEquals(101 * 11, eventsCount)
    }

    @Test
    fun `concurrent write to two instances with same configuration`() = runBlocking {
        val logger = ConsoleLogger()
        val propertiesFile1 = PropertiesFile(tempDir, "test-prefix-$STORAGE_KEY", logger)
        val propertiesFile2 = PropertiesFile(tempDir, "test-prefix-$STORAGE_KEY", logger)
        val eventsFileManager1 =
            EventsFileManager(tempDir, STORAGE_KEY, propertiesFile1, logger, testDiagnostics)
        val eventsFileManager2 =
            EventsFileManager(tempDir, STORAGE_KEY, propertiesFile2, logger, testDiagnostics)
        val job1 = GlobalScope.launch {
            eventsFileManager1.storeEvent(createEvent("test1"))
            eventsFileManager1.storeEvent(createEvent("test2"))
            eventsFileManager1.rollover()
        }
        val job2 = GlobalScope.launch {
            eventsFileManager2.rollover()
            eventsFileManager2.storeEvent(createEvent("test3"))
            eventsFileManager2.storeEvent(createEvent("test4"))
            eventsFileManager2.rollover()
        }
        val job3 = GlobalScope.launch {
            eventsFileManager1.rollover()
            eventsFileManager1.storeEvent(createEvent("test5"))
            eventsFileManager1.storeEvent(createEvent("test6"))
            eventsFileManager1.rollover()
        }
        val job4 = GlobalScope.launch {
            eventsFileManager2.rollover()
            eventsFileManager2.storeEvent(createEvent("test7"))
            eventsFileManager2.storeEvent(createEvent("test8"))
            eventsFileManager2.rollover()
        }
        kotlinx.coroutines.joinAll(job1, job2, job3, job4)
        val filePaths = eventsFileManager1.read()
        var eventsCount = 0
        filePaths.forEach { filePath ->
            val eventsString = eventsFileManager1.getEventString(filePath)
            val events = JSONArray(eventsString)
            eventsCount += events.length()
        }
        assertEquals(8, eventsCount)
    }

    @Test
    fun `concurrent write from multiple threads on multiple instances`() = runBlocking {
        val logger = ConsoleLogger()
        val propertiesFile = PropertiesFile(tempDir, "test-prefix-$STORAGE_KEY", logger)
        for (i in 0..100) {
            val eventsFileManager =
                EventsFileManager(tempDir, STORAGE_KEY, propertiesFile, logger, testDiagnostics)
            val thread = thread {
                runBlocking {
                    for (d in 0..10) {
                        eventsFileManager.storeEvent(createEvent("test$i-$d"))
                    }
                    eventsFileManager.rollover()
                }
            }
            thread.join()
        }

        val eventsFileManagerForRead =
            EventsFileManager(tempDir, STORAGE_KEY, propertiesFile, logger, testDiagnostics)
        val filePaths = eventsFileManagerForRead.read()
        var eventsCount = 0
        filePaths.forEach { filePath ->
            val eventsString = eventsFileManagerForRead.getEventString(filePath)
            val events = JSONArray(eventsString)
            eventsCount += events.length()
        }
        assertEquals(101 * 11, eventsCount)
    }

    private fun createEarlierVersionEventFiles() {
        val file0 = File(tempDir, "$STORAGE_KEY-0")
        file0.writeText("[{\"eventType\":\"test1\"},{\"eventType\":\"test2\"}]")
        val file1 = File(tempDir, "$STORAGE_KEY-1")
        file1.writeText(",{\"eventType\":\"test3\"},{\"eventType\":\"test4\"}]")
        val file2 = File(tempDir, "$STORAGE_KEY-2")
        file2.writeText("[[{\"eventType\":\"test5\"},{\"eventType\":\"test6\"}]]")
        val file3 = File(tempDir, "$STORAGE_KEY-3")
        file3.writeText("[{\"eventType\":\"test7\"},{\"eventType\":\"test8\"}]]")
        val file4 = File(tempDir, "$STORAGE_KEY-4")
        file4.writeText("{\"eventType\":\"test9\"},{\"eventType\":\"test10\"}]")
        val file5 = File(tempDir, "$STORAGE_KEY-5")
        file5.writeText("[{\"eventType\":\"test11\"}],{\"eventType\":\"test12\"}")
        val file6 = File(tempDir, "$STORAGE_KEY-6.tmp")
        file6.writeText("[{\"eventType\":\"test13\"},{\"eventType\":\"test14\"}")
    }

    private fun createEvent(eventType: String): String {
        return "{\"eventType\":\"$eventType\"}"
    }
}
