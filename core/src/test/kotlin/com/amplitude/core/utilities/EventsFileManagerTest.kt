package com.amplitude.core.utilities

import com.amplitude.common.jvm.ConsoleLogger
import com.amplitude.id.utilities.PropertiesFile
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class EventsFileManagerTest {
    @TempDir
    lateinit var tempDir: File

    @Test
    fun `test store event and read`() {
        val logger = ConsoleLogger()
        val storageKey = "storageKey"
        val propertiesFile = PropertiesFile(tempDir, storageKey, "test-prefix", logger)
        val eventsFileManager =
            EventsFileManager(tempDir, storageKey, propertiesFile, logger)
        runBlocking {
            eventsFileManager.storeEvent(createEvent("test1"))
            eventsFileManager.storeEvent(createEvent("test2"))
            eventsFileManager.rollover()
            eventsFileManager.storeEvent(createEvent("test3"))
            eventsFileManager.storeEvent(createEvent("test4"))
            eventsFileManager.rollover()
            eventsFileManager.storeEvent(createEvent("test5"))
        }
        val filePaths = eventsFileManager.read()
        assertEquals(2, filePaths.size)
        filePaths.withIndex().forEach { (index, filePath) ->
            // verify file name and raw content
            val file = File(filePath)
            assertEquals("$storageKey-$index", file.name)
            val content = file.readText()
            val lines = content.split("\n")
            assertEquals(3, lines.size)
            assertEquals(createEvent("test${index * 2 + 1}"), lines[0])
            assertEquals(createEvent("test${index * 2 + 2}"), lines[1])
            assertEquals("", lines[2])
        }

        runBlocking {
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
    }

    @Test
    fun `rollover should finish current non-empty temp file`() {
        val logger = ConsoleLogger()
        val storageKey = "storageKey"
        val propertiesFile = PropertiesFile(tempDir, storageKey, "test-prefix", logger)
        val eventsFileManager =
            EventsFileManager(tempDir, storageKey, propertiesFile, logger)
        runBlocking {
            eventsFileManager.storeEvent(createEvent("test1"))
        }
        val filePaths = eventsFileManager.read()
        assertEquals(0, filePaths.size)
        runBlocking {
            eventsFileManager.rollover()
        }
        val filePathsAfterRollover = eventsFileManager.read()
        assertEquals(1, filePathsAfterRollover.size)
        val file = File(filePathsAfterRollover[0])
        val content = file.readText()
        val lines = content.split("\n")
        assertEquals(2, lines.size)
        assertEquals(createEvent("test1"), lines[0])
        assertEquals("", lines[1])
        runBlocking {
            val eventsString = eventsFileManager.getEventString(filePathsAfterRollover[0])
            val events = JSONArray(eventsString)
            assertEquals(1, events.length())
            assertEquals("test1", events.getJSONObject(0).getString("eventType"))
        }
    }

    @Test
    fun `rollover should ignore current empty temp file`() {
        val logger = ConsoleLogger()
        val storageKey = "storageKey"
        val propertiesFile = PropertiesFile(tempDir, storageKey, "test-prefix", logger)
        val eventsFileManager =
            EventsFileManager(tempDir, storageKey, propertiesFile, logger)
        runBlocking {
            eventsFileManager.rollover()
        }
        val filePathsAfterRollover = eventsFileManager.read()
        assertEquals(0, filePathsAfterRollover.size)
    }

    @Test
    fun `remove should delete a file`() {
        val logger = ConsoleLogger()
        val storageKey = "storageKey"
        val propertiesFile = PropertiesFile(tempDir, storageKey, "test-prefix", logger)
        val eventsFileManager =
            EventsFileManager(tempDir, storageKey, propertiesFile, logger)
        runBlocking {
            eventsFileManager.storeEvent(createEvent("test1"))
            eventsFileManager.rollover()
        }
        val filePaths = eventsFileManager.read()
        assertEquals(1, filePaths.size)
        eventsFileManager.remove(filePaths[0])
        val filePathsAfterRemove = eventsFileManager.read()
        assertEquals(0, filePathsAfterRemove.size)
    }

    @Test
    fun `test split`() {
        val logger = ConsoleLogger()
        val storageKey = "storageKey"
        val propertiesFile = PropertiesFile(tempDir, storageKey, "test-prefix", logger)
        val eventsFileManager =
            EventsFileManager(tempDir, storageKey, propertiesFile, logger)
        runBlocking {
            eventsFileManager.storeEvent(createEvent("test1"))
            eventsFileManager.storeEvent(createEvent("test2"))
            eventsFileManager.rollover()
        }
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
        val lines0 = content0.split("\n")
        assertEquals(2, lines0.size)
        assertEquals(createEvent("test1"), lines0[0])
        assertEquals("", lines0[1])
        val file1 = File(filePathsAfterSplit[1])
        val content1 = file1.readText()
        val lines1 = content1.split("\n")
        assertEquals(2, lines1.size)
        assertEquals(createEvent("test2"), lines1[0])
        assertEquals("", lines1[1])
    }

    @Test
    fun `could handle earlier version of events file`() {
        createEarlierVersionEventFiles()
        val logger = ConsoleLogger()
        val storageKey = "storageKey"
        val propertiesFile = PropertiesFile(tempDir, storageKey, "test-prefix", logger)
        val eventsFileManager =
            EventsFileManager(tempDir, storageKey, propertiesFile, logger)
        val filePaths = eventsFileManager.read()
        assertEquals(7, filePaths.size)
        runBlocking {
           filePaths.withIndex().forEach { (index, filePath) ->
               val eventsString = eventsFileManager.getEventString(filePath)
               if (index == 5) {
                   assertEquals("[{\"eventType\":\"test11\"}]", eventsString)
               } else {
                   val events = JSONArray(eventsString)
                   assertEquals(2, events.length())
                   assertEquals(
                       "test${index * 2 + 1}",
                       events.getJSONObject(0).getString("eventType")
                   )
                   assertEquals(
                       "test${index * 2 + 2}",
                       events.getJSONObject(1).getString("eventType")
                   )
               }
           }
        }
    }

    @Test
    fun `concurrent writes to the same event file manager instance`() {
        val logger = ConsoleLogger()
        val storageKey = "storageKey"
        val propertiesFile = PropertiesFile(tempDir, storageKey, "test-prefix", logger)
        val eventsFileManager =
            EventsFileManager(tempDir, storageKey, propertiesFile, logger)
        runBlocking {
            val job1 = kotlinx.coroutines.GlobalScope.launch {
                eventsFileManager.storeEvent(createEvent("test1"))
                eventsFileManager.storeEvent(createEvent("test2"))
                eventsFileManager.rollover()
            }
            val job2 = kotlinx.coroutines.GlobalScope.launch {
                eventsFileManager.rollover()
                eventsFileManager.storeEvent(createEvent("test3"))
                eventsFileManager.storeEvent(createEvent("test4"))
                eventsFileManager.rollover()
            }
            val job3 = kotlinx.coroutines.GlobalScope.launch {
                eventsFileManager.rollover()
                eventsFileManager.storeEvent(createEvent("test5"))
                eventsFileManager.storeEvent(createEvent("test6"))
                eventsFileManager.rollover()
            }
            kotlinx.coroutines.joinAll(job1, job2, job3)
        }
        val filePaths = eventsFileManager.read()
        var eventsCount = 0
        runBlocking {
            filePaths.forEach { filePath ->
                val eventsString = eventsFileManager.getEventString(filePath)
                val events = JSONArray(eventsString)
                eventsCount += events.length()
            }
        }
        assertEquals(6, eventsCount)
    }

    @Test
    fun `concurrent write to two instances with same configuration`() {
        val logger = ConsoleLogger()
        val storageKey = "storageKey"
        val propertiesFile1 = PropertiesFile(tempDir, storageKey, "test-prefix", logger)
        val propertiesFile2 = PropertiesFile(tempDir, storageKey, "test-prefix", logger)
        val eventsFileManager1 =
            EventsFileManager(tempDir, storageKey, propertiesFile1, logger)
        val eventsFileManager2 =
            EventsFileManager(tempDir, storageKey, propertiesFile2, logger)
        runBlocking {
            val job1 = kotlinx.coroutines.GlobalScope.launch {
                eventsFileManager1.storeEvent(createEvent("test1"))
                eventsFileManager1.storeEvent(createEvent("test2"))
                eventsFileManager1.rollover()
            }
            val job2 = kotlinx.coroutines.GlobalScope.launch {
                eventsFileManager2.rollover()
                eventsFileManager2.storeEvent(createEvent("test3"))
                eventsFileManager2.storeEvent(createEvent("test4"))
                eventsFileManager2.rollover()
            }
            val job3 = kotlinx.coroutines.GlobalScope.launch {
                eventsFileManager1.rollover()
                eventsFileManager1.storeEvent(createEvent("test5"))
                eventsFileManager1.storeEvent(createEvent("test6"))
                eventsFileManager1.rollover()
            }
            val job4 = kotlinx.coroutines.GlobalScope.launch {
                eventsFileManager2.rollover()
                eventsFileManager2.storeEvent(createEvent("test7"))
                eventsFileManager2.storeEvent(createEvent("test8"))
                eventsFileManager2.rollover()
            }
            kotlinx.coroutines.joinAll(job1, job2, job3, job4)
        }
        val filePaths = eventsFileManager1.read()
        var eventsCount = 0
        runBlocking {
            filePaths.forEach { filePath ->
                val eventsString = eventsFileManager1.getEventString(filePath)
                val events = JSONArray(eventsString)
                eventsCount += events.length()
            }
        }
        assertEquals(8, eventsCount)
    }

    private fun createEarlierVersionEventFiles() {
        val file0 = File(tempDir, "storageKey-0")
        file0.writeText("[{\"eventType\":\"test1\"},{\"eventType\":\"test2\"}]")
        val file1 = File(tempDir, "storageKey-1")
        file1.writeText(",{\"eventType\":\"test3\"},{\"eventType\":\"test4\"}]")
        val file2 = File(tempDir, "storageKey-2")
        file2.writeText("[[{\"eventType\":\"test5\"},{\"eventType\":\"test6\"}]]")
        val file3 = File(tempDir, "storageKey-3")
        file3.writeText("[{\"eventType\":\"test7\"},{\"eventType\":\"test8\"}]]")
        val file4 = File(tempDir, "storageKey-4")
        file4.writeText("{\"eventType\":\"test9\"},{\"eventType\":\"test10\"}]")
        val file5 = File(tempDir, "storageKey-5")
        file5.writeText("[{\"eventType\":\"test11\"}],{\"eventType\":\"test12\"}")
        val file6 = File(tempDir, "storageKey-6.tmp")
        file6.writeText("[{\"eventType\":\"test13\"},{\"eventType\":\"test14\"}")
    }

    private fun createEvent(eventType: String) : String {
        return "{\"eventType\":\"$eventType\"}"
    }
}