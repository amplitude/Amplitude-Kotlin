package com.amplitude.core.utilities

import com.amplitude.common.jvm.ConsoleLogger
import com.amplitude.core.Configuration
import com.amplitude.id.utilities.PropertiesFile
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.json.JSONArray
import org.json.JSONObject
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.io.File
import kotlin.concurrent.thread

private const val STORAGE_KEY = Configuration.DEFAULT_INSTANCE

/** Enough flush cycles to show growth is unbounded rather than a one-off. */
private const val READ_CYCLES = 10

@OptIn(ExperimentalCoroutinesApi::class)
class EventsFileManagerTest {
    @TempDir lateinit var tempDir: File
    private val propertiesDispatcher = StandardTestDispatcher()
    private val propertiesFile by lazy {
        createPropertiesFile()
    }
    private val logger = ConsoleLogger()
    private val testDiagnostics = Diagnostics()
    private val eventsFileManager: EventsFileManager by lazy {
        EventsFileManager(
            directory = tempDir,
            storageKey = STORAGE_KEY,
            kvs = propertiesFile,
            logger = logger,
            diagnostics = testDiagnostics,
        )
    }

    @AfterEach
    fun finishPropertiesSaves() {
        propertiesDispatcher.scheduler.advanceUntilIdle()
    }

    @Test
    fun `failed deletion is included in diagnostic error logs`() {
        val batch = File(tempDir, "nonempty-batch").apply { mkdir() }
        File(batch, "child").writeText("data")
        assertFalse(eventsFileManager.remove(batch.path))
        val errors = org.json.JSONObject(testDiagnostics.extractDiagnostics()).getJSONArray("error_logs")
        assertEquals("Failed to delete event file", errors.getString(0))
    }

    @Test
    fun `successful deletion does not report an error`() {
        val batch = File(tempDir, "batch").apply { writeText("data") }
        assertTrue(eventsFileManager.remove(batch.path))
        assertFalse(testDiagnostics.hasDiagnostics())
    }

    @Test
    fun `store event and read`() =
        runBlocking {
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
    fun `read should respect file name order`() {
        val tempFiles = listOf("3.properties")
        val splitFiles1 = listOf("1-1", "1-2")
        val unsentFiles = listOf("6", "7", "8", "9")
        val splitFiles2 = listOf("32-1", "32-2")
        val latestUnsentFiles = (33..41).toList().map { it.toString() }

        // explicitly scramble the order of the files
        (splitFiles2 + unsentFiles + latestUnsentFiles + splitFiles1 + tempFiles)
            // append storage key prefix
            .map { STORAGE_KEY + '-' + it }
            .forEach { fileName ->
                File(tempDir, fileName).createNewFile()
            }

        val eventFiles = eventsFileManager.read()

        val expectedFiles =
            // .properties files should be ignored
            (splitFiles1 + unsentFiles + splitFiles2 + latestUnsentFiles)
                // append full directory path and storage key prefix
                .map { tempDir.toString() + "/" + STORAGE_KEY + '-' + it }

        assertEquals(eventFiles, expectedFiles)
    }

    @Test
    fun `should preserve ordering for nested splits large indices and collision suffixes`() {
        eventsFileManager
        val names = listOf("21", "2-2", "2-1-2", "2-1-1", "1-1234567890-123", "10000000000", "9")
        names.forEach { File(tempDir, "$STORAGE_KEY-$it").createNewFile() }
        File(tempDir, "$STORAGE_KEY-999.tmp").createNewFile()
        File(tempDir, "other-1").createNewFile()
        assertEquals(
            listOf("1-1234567890-123", "2-1-1", "2-1-2", "2-2", "9", "21", "10000000000"),
            eventsFileManager.read().map { File(it).name.removePrefix("$STORAGE_KEY-") },
        )
    }

    @Test
    fun `should preserve escaped unicode events across read chunks`() =
        runTest {
            val properties = org.json.JSONObject().put("event_type", "x".repeat(8191) + "世界😀\n\"escaped\"")
            val original = properties.toString()
            eventsFileManager.storeEvent(original)
            eventsFileManager.storeEvent("not json")
            eventsFileManager.storeEvent(original)
            eventsFileManager.rollover()
            val payload = eventsFileManager.getEventString(eventsFileManager.read().single())
            assertEquals("[$original,$original]", payload)
        }

    @Test
    fun `rollover should finish current non-empty temp file`() =
        runBlocking {
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
    fun `rollover should ignore current empty temp file`() =
        runBlocking {
            eventsFileManager.rollover()
            val filePathsAfterRollover = eventsFileManager.read()
            assertEquals(0, filePathsAfterRollover.size)
        }

    @Test
    fun `remove should delete a file`() =
        runBlocking {
            eventsFileManager.storeEvent(createEvent("test1"))
            eventsFileManager.rollover()
            val filePaths = eventsFileManager.read()
            assertEquals(1, filePaths.size)
            eventsFileManager.remove(filePaths[0])
            val filePathsAfterRemove = eventsFileManager.read()
            assertEquals(0, filePathsAfterRemove.size)
        }

    @Test
    fun `split event files`() =
        runBlocking {
            eventsFileManager.storeEvent(createEvent("test1"))
            eventsFileManager.storeEvent(createEvent("test2"))
            eventsFileManager.rollover()

            val filePaths = eventsFileManager.read()
            assertEquals(1, filePaths.size)
            val eventsString = eventsFileManager.getEventString(filePaths[0])
            val events = JSONArray(eventsString)

            assertEquals(2, events.length())
            eventsFileManager.splitFile(filePaths[0], events)
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
    fun `getEventString should handle empty file`() =
        runBlocking {
            val file = File(tempDir, "$STORAGE_KEY-0")
            file.createNewFile()
            val filePaths = eventsFileManager.read()
            assertEquals(1, filePaths.size)
            val eventsString = eventsFileManager.getEventString(filePaths[0])
            assertEquals("", eventsString)
        }

    @Test
    fun `getEventString should return empty for non-existent file`() =
        runBlocking {
            val deletedFilePath = File(tempDir, "$STORAGE_KEY-0").absolutePath
            // Simulate file deleted between read() and getEventString()
            val eventsString = eventsFileManager.getEventString(deletedFilePath)
            assertEquals("", eventsString)
        }

    @Test
    fun `getEventString should handle trailing content after last delimiter`() =
        runBlocking {
            // Trigger lazy init first so handleV1Files() runs on empty directory
            eventsFileManager.read()
            // File where the last event is NOT followed by a delimiter
            val file = File(tempDir, "$STORAGE_KEY-0")
            file.writeText("{\"eventType\":\"test1\"}\u0000{\"eventType\":\"test2\"}")
            val filePaths = eventsFileManager.read()
            assertEquals(1, filePaths.size)
            val eventsString = eventsFileManager.getEventString(filePaths[0])
            val events = JSONArray(eventsString)
            assertEquals(2, events.length())
            assertEquals("test1", events.getJSONObject(0).getString("eventType"))
            assertEquals("test2", events.getJSONObject(1).getString("eventType"))
        }

    @Test
    fun `verify delimiter handled gracefully`() =
        runBlocking {
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
    fun `verify malformed event shows up in diagnostics`() =
        runBlocking {
            val file0 = File(tempDir, "$STORAGE_KEY-0")
            file0.writeText(
                "{\"eventType\":\"test1\"}\u0000{\"eventType\":\"test2\"}\u0000{\"eventType\":\"test3\"\u0000",
            )
            val logger = ConsoleLogger()
            val propertiesFile = createPropertiesFile()
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
                diagnostics.extractDiagnostics(),
            )
        }

    @Test
    fun `verify delimiter in event names`() =
        runBlocking {
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
    fun `could handle earlier version of events file`() =
        runBlocking {
            createEarlierVersionEventFiles()
            val filePaths = eventsFileManager.read()
            assertEquals(7, filePaths.size)
            filePaths.withIndex().forEach { (index, filePath) ->
                val file = File(filePath)
                assertTrue(
                    file.extension.isEmpty(),
                    "file extension should be empty for v1 event files",
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
    fun `could handle earlier versions with name conflict and new events`() =
        runBlocking {
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
    fun `could handle earlier versions with line break in event name`() =
        runBlocking {
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
    fun `concurrent writes to the same event file manager instance`() =
        runBlocking {
            val job1 =
                GlobalScope.launch {
                    eventsFileManager.storeEvent(createEvent("test1"))
                    eventsFileManager.storeEvent(createEvent("test2"))
                    eventsFileManager.rollover()
                }
            val job2 =
                GlobalScope.launch {
                    eventsFileManager.rollover()
                    eventsFileManager.storeEvent(createEvent("test3"))
                    eventsFileManager.storeEvent(createEvent("test4"))
                    eventsFileManager.rollover()
                }
            val job3 =
                GlobalScope.launch {
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
    fun `concurrent write from multiple threads`() =
        runBlocking {
            for (i in 0..100) {
                val thread =
                    thread {
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
    fun `concurrent write to two instances with same configuration`() =
        runBlocking {
            val logger = ConsoleLogger()
            val propertiesFile1 = createPropertiesFile()
            val propertiesFile2 = createPropertiesFile()
            val eventsFileManager1 =
                EventsFileManager(tempDir, STORAGE_KEY, propertiesFile1, logger, testDiagnostics)
            val eventsFileManager2 =
                EventsFileManager(tempDir, STORAGE_KEY, propertiesFile2, logger, testDiagnostics)
            val job1 =
                GlobalScope.launch {
                    eventsFileManager1.storeEvent(createEvent("test1"))
                    eventsFileManager1.storeEvent(createEvent("test2"))
                    eventsFileManager1.rollover()
                }
            val job2 =
                GlobalScope.launch {
                    eventsFileManager2.rollover()
                    eventsFileManager2.storeEvent(createEvent("test3"))
                    eventsFileManager2.storeEvent(createEvent("test4"))
                    eventsFileManager2.rollover()
                }
            val job3 =
                GlobalScope.launch {
                    eventsFileManager1.rollover()
                    eventsFileManager1.storeEvent(createEvent("test5"))
                    eventsFileManager1.storeEvent(createEvent("test6"))
                    eventsFileManager1.rollover()
                }
            val job4 =
                GlobalScope.launch {
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
    fun `concurrent write from multiple threads on multiple instances`() =
        runBlocking {
            val logger = ConsoleLogger()
            val propertiesFile = createPropertiesFile()
            for (i in 0..100) {
                val eventsFileManager =
                    EventsFileManager(tempDir, STORAGE_KEY, propertiesFile, logger, testDiagnostics)
                val thread =
                    thread {
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

    @Nested
    inner class PropertiesFileFiltering {
        @ParameterizedTest
        @ValueSource(strings = ["client.properties", "client.properties-archive", ".properties", "properties", "client.[test]+(archive)"])
        fun `should preserve events when the storage key contains properties`(storageKey: String) = runTest {
            val manager = EventsFileManager(tempDir, storageKey, createPropertiesFile(storageKey), logger, testDiagnostics)
            val metadataFiles = createMetadataFiles(storageKey)
            val event = createEvent("test")

            manager.storeEvent(event)
            manager.rollover()

            val batch = File(tempDir, "$storageKey-0")
            assertEquals(listOf(batch.absolutePath), manager.read())
            assertEquals(event + EventsFileManager.DELIMITER, batch.readText())
            metadataFiles.forEach { assertEquals("counter=7\n", it.readText()) }
        }

        @ParameterizedTest
        @ValueSource(strings = ["client.properties", "client.properties-archive", ".properties", "properties", "client.[test]+(archive)"])
        fun `should migrate and recover events when the storage key contains properties`(storageKey: String) = runTest {
            val metadataFiles = createMetadataFiles(storageKey)
            val legacy = createEvent("legacy")
            File(tempDir, "$storageKey-0").writeText("[$legacy]")
            File(tempDir, "$storageKey-1.tmp").writeText("[$legacy]")
            val manager = EventsFileManager(tempDir, storageKey, createPropertiesFile(storageKey), logger, testDiagnostics)
            val legacyBatch = File(tempDir, "$storageKey-0")
            val migratedBatch = File(tempDir, "$storageKey-1")
            assertEquals(legacy + EventsFileManager.DELIMITER, legacyBatch.readText())
            assertEquals(legacy + EventsFileManager.DELIMITER, migratedBatch.readText())

            val first = createEvent("first")
            val second = createEvent("second")
            val unfinished = File(tempDir, "$storageKey-7.tmp")
            unfinished.writeText(first + EventsFileManager.DELIMITER)

            manager.storeEvent(second)
            manager.rollover()

            val recoveredBatch = File(tempDir, "$storageKey-7")
            assertEquals(listOf(legacyBatch.absolutePath, migratedBatch.absolutePath, recoveredBatch.absolutePath), manager.read())
            assertEquals(first + EventsFileManager.DELIMITER + second + EventsFileManager.DELIMITER, recoveredBatch.readText())
            assertFalse(unfinished.exists())
            metadataFiles.forEach { assertEquals("counter=7\n", it.readText()) }
        }

        @ParameterizedTest
        @ValueSource(strings = [".properties", ".properties.bak", ".properties-123.tmp"])
        fun `should preserve properties files while storing and reading events`(suffix: String) = runTest {
            val manager = eventsFileManager
            val metadata = File(tempDir, "amplitude-metadata-$STORAGE_KEY$suffix")
            val contents = "counter=7\n"
            metadata.writeText(contents)
            val event = createEvent("test")

            manager.storeEvent(event)
            manager.rollover()

            val batch = File(tempDir, "$STORAGE_KEY-0")
            assertEquals(listOf(batch.absolutePath), manager.read())
            assertEquals(event + EventsFileManager.DELIMITER, batch.readText())
            assertEquals(contents, metadata.readText())
        }

        @ParameterizedTest
        @ValueSource(strings = [".properties", ".properties.bak", ".properties-123.tmp"])
        fun `should preserve properties files while migrating legacy events`(suffix: String) = runTest {
            val metadata = File(tempDir, "amplitude-metadata-$STORAGE_KEY$suffix")
            val contents = "counter=7\n"
            metadata.writeText(contents)
            val event = createEvent("legacy")
            val batch = File(tempDir, "$STORAGE_KEY-0").apply { writeText("[$event]") }

            val manager = eventsFileManager

            assertEquals(contents, metadata.readText())
            assertEquals(event + EventsFileManager.DELIMITER, batch.readText())
            assertEquals(listOf(batch.absolutePath), manager.read())
        }

        @Test
        fun `should recover unfinished event files alongside properties sidecars`() = runTest {
            val manager = eventsFileManager
            val first = createEvent("first")
            val second = createEvent("second")
            val unfinished = File(tempDir, "$STORAGE_KEY-0.tmp")
            unfinished.writeText(first + EventsFileManager.DELIMITER)
            val metadata = File(tempDir, "amplitude-metadata-$STORAGE_KEY.properties-123.tmp")
            val contents = "counter=7\n"
            metadata.writeText(contents)

            manager.storeEvent(second)
            manager.rollover()

            val batch = File(tempDir, "$STORAGE_KEY-0")
            assertEquals(listOf(batch.absolutePath), manager.read())
            assertEquals(first + EventsFileManager.DELIMITER + second + EventsFileManager.DELIMITER, batch.readText())
            assertFalse(unfinished.exists())
            assertEquals(contents, metadata.readText())
        }

        private fun createMetadataFiles(storageKey: String): List<File> =
            listOf(".properties", ".properties.bak", ".properties-123.tmp").map { suffix ->
                File(tempDir, "amplitude-metadata-$storageKey$suffix").apply { writeText("counter=7\n") }
            }
    }

    @Nested
    inner class UnrelatedFiles {
        @Test
        fun `should leave unrelated files untouched while storing and reading events`() = runTest {
            val manager = eventsFileManager
            val unrelatedFiles = createUnrelatedFiles()
            val event = createEvent("test")

            manager.storeEvent(event)
            manager.rollover()

            val batch = File(tempDir, "$STORAGE_KEY-0")
            assertEquals(listOf(batch.absolutePath), manager.read())
            assertEquals(event + EventsFileManager.DELIMITER, batch.readText())
            unrelatedFiles.forEach { assertEquals("unrelated contents", it.readText()) }
        }

        @Test
        fun `should leave unrelated files untouched while migrating legacy events`() = runTest {
            val unrelatedFiles = createUnrelatedFiles()
            val event = createEvent("legacy")
            val batch = File(tempDir, "$STORAGE_KEY-0").apply { writeText("[$event]") }

            val manager = eventsFileManager

            assertEquals(listOf(batch.absolutePath), manager.read())
            assertEquals(event + EventsFileManager.DELIMITER, batch.readText())
            unrelatedFiles.forEach { assertEquals("unrelated contents", it.readText()) }
        }

        private fun createUnrelatedFiles(): List<File> =
            listOf(
                "prefix-$STORAGE_KEY-9.tmp",
                "$STORAGE_KEY-notes",
                "$STORAGE_KEY-notes.tmp",
                "$STORAGE_KEY-9.bak",
                "$STORAGE_KEY-9.tmp.bak",
                "$STORAGE_KEY-9.tmp.tmp",
                "$STORAGE_KEY-9-custom.tmp",
            ).map { name -> File(tempDir, name).apply { writeText("unrelated contents") } }
    }

    @Nested
    inner class MatchesStorageFile {
        @Test
        fun `should not throw when platform passes a null file name`() {
            // SDK-176: File.listFiles can hand the FilenameFilter callback a null name
            assertFalse(eventsFileManager.matchesStorageFile(null) { true })
        }

        @ParameterizedTest
        @ValueSource(strings = ["0", "0.tmp", "10000000000", "2-1", "2-1-2.tmp", "1-1700000000000-999", "1-1700000000000-999-1.tmp"])
        fun `should recognize current and legacy event file names`(suffix: String) {
            assertTrue(eventsFileManager.matchesStorageFile("$STORAGE_KEY-$suffix") { true })
        }

        @ParameterizedTest
        @ValueSource(strings = ["", "-1", "1-", "1--2", "1-custom", "1.bak", "1.tmp.bak", "1.tmp.tmp", "1.properties", "1\n"])
        fun `should reject names outside the event file format`(suffix: String) {
            assertFalse(eventsFileManager.matchesStorageFile("$STORAGE_KEY-$suffix") { true })
        }

        @Test
        fun `should reject names that only contain the storage key`() {
            assertFalse(eventsFileManager.matchesStorageFile("prefix-$STORAGE_KEY-0") { true })
            assertFalse(eventsFileManager.matchesStorageFile("${STORAGE_KEY}other-0") { true })
            assertFalse(eventsFileManager.matchesStorageFile(STORAGE_KEY) { true })
        }

        @Test
        fun `should reject files for a different storage key`() {
            assertFalse(eventsFileManager.matchesStorageFile("other-key-0") { true })
        }

        @Test
        fun `should reject files that fail the predicate`() {
            assertFalse(
                eventsFileManager.matchesStorageFile("$STORAGE_KEY-0.tmp") { !it.endsWith(".tmp") },
            )
        }
    }

    @Nested
    inner class GetEventStringSerialization {
        @Test
        fun `should preserve event values when normalizing JSON`() =
            runTest {
                val event = """{"zzz":1,"aaa":2,"nested":{"b":[1,2]},"big":10000000000000000000000}"""
                writeEventFile(event)

                val eventsString = eventsFileManager.getEventString(eventsFileManager.read().single())

                val parsed = JSONArray(eventsString)
                assertEquals(1, parsed.length())
                val actual = parsed.getJSONObject(0)
                assertEquals(1, actual.getInt("zzz"))
                assertEquals(2, actual.getInt("aaa"))
                assertEquals(listOf(1, 2), actual.getJSONObject("nested").getJSONArray("b").toList())
                assertEquals("10000000000000000000000", actual.get("big").toString())
            }

        @ParameterizedTest
        @ValueSource(strings = ["junk", "{}", "{\"eventType\":"])
        fun `should normalize accepted prefixes without losing later valid events`(suffix: String) =
            runTest {
                writeEventFile(createEvent("first") + suffix, createEvent("later-valid"))

                val eventsString = eventsFileManager.getEventString(eventsFileManager.read().single())

                val parsed = JSONArray(eventsString)
                assertEquals(
                    listOf("first", "later-valid"),
                    (0 until parsed.length()).map { parsed.getJSONObject(it).getString("eventType") },
                )
            }

        @Test
        fun `should normalize lenient object syntax to valid JSON`() =
            runTest {
                writeEventFile("{eventType:'世界 😀'}")

                val eventsString = eventsFileManager.getEventString(eventsFileManager.read().single())

                assertEquals("""[{"eventType":"世界 😀"}]""", eventsString)
            }

        @Test
        fun `should not emit a stray comma when the first event is malformed`() =
            runBlocking {
                writeEventFile("not-json", createEvent("test1"), createEvent("test2"))

                val eventsString = eventsFileManager.getEventString(eventsFileManager.read().single())

                assertEquals("[${createEvent("test1")},${createEvent("test2")}]", eventsString)
                assertEquals(2, JSONArray(eventsString).length())
            }

        @Test
        fun `should not emit a stray comma when a middle or trailing event is malformed`() =
            runBlocking {
                writeEventFile(createEvent("test1"), "not-json", createEvent("test2"), "also-not-json")

                val eventsString = eventsFileManager.getEventString(eventsFileManager.read().single())

                assertEquals("[${createEvent("test1")},${createEvent("test2")}]", eventsString)
                assertEquals(2, JSONArray(eventsString).length())
            }

        private fun writeEventFile(vararg events: String) {
            File(tempDir, "$STORAGE_KEY-0")
                .writeText(events.joinToString(separator = "", postfix = "") { it + EventsFileManager.DELIMITER })
        }
    }

    @Nested
    inner class CorruptEventFile {
        /**
         * A file whose every event fails to parse yields an empty string, so
         * [EventPipeline.upload] skips it before it can flush diagnostics or delete the file.
         * Each re-read then appends the same raw event strings to [Diagnostics] again.
         */
        @Test
        fun `should not re-add malformed events for a file read repeatedly`() =
            runBlocking {
                val corruptFile = File(tempDir, "$STORAGE_KEY-0")
                corruptFile.writeText("not-json-1${EventsFileManager.DELIMITER}not-json-2${EventsFileManager.DELIMITER}")

                val filePath = eventsFileManager.read().single()
                repeat(READ_CYCLES) {
                    assertEquals("", eventsFileManager.getEventString(filePath))
                }

                // The file yields nothing uploadable, so nothing ever clears the diagnostics.
                val malformedEvents = malformedEventsIn(testDiagnostics.extractDiagnostics())
                assertEquals(2, malformedEvents.length())
            }

        @Test
        fun `should not retain a file that yields no parseable events`() =
            runBlocking {
                val corruptFile = File(tempDir, "$STORAGE_KEY-0")
                corruptFile.writeText("not-json${EventsFileManager.DELIMITER}")

                val filePath = eventsFileManager.read().single()
                repeat(READ_CYCLES) {
                    eventsFileManager.getEventString(filePath)
                }

                assertFalse(corruptFile.exists(), "corrupt file should not be re-read forever")
            }

        private fun malformedEventsIn(diagnostics: String?): JSONArray =
            JSONObject(diagnostics ?: "{}").optJSONArray("malformed_events") ?: JSONArray()
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

    private fun createPropertiesFile(storageKey: String = STORAGE_KEY): PropertiesFile = PropertiesFile.getInstance(
        tempDir.path,
        "test-prefix-$storageKey.properties",
        logger,
        propertiesDispatcher,
    )
}
