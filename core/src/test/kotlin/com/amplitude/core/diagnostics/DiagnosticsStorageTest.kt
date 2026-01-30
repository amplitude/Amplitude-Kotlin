package com.amplitude.core.diagnostics

import com.amplitude.common.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File

class DiagnosticsStorageTest {
    private lateinit var storageDir: File
    private val testLogger =
        object : Logger {
            override var logMode: Logger.LogMode = Logger.LogMode.OFF

            override fun debug(message: String) {}

            override fun error(message: String) {}

            override fun info(message: String) {}

            override fun warn(message: String) {}
        }

    @BeforeEach
    fun setup() {
        storageDir = createTempDir(prefix = "diag-storage")
    }

    @AfterEach
    fun tearDown() {
        storageDir.deleteRecursively()
    }

    @Test
    fun `saveSnapshot persists tags to disk`() =
        runBlocking {
            val storage = createStorage(sessionStartAt = "session-1")

            // Tags need at least one counter/histogram/event to be included in loaded sessions
            val snapshot =
                DiagnosticsSnapshot(
                    tags = mapOf("tag1" to "value1", "tag2" to "value2"),
                    counters = mapOf("counter" to 1L),
                    histograms = null,
                    events = null,
                )
            storage.saveSnapshot(snapshot)
            delay(100) // Allow actor to process

            val newStorage = createStorage(sessionStartAt = "session-2")
            val loadedSnapshots = newStorage.loadAndClearPreviousSessions()

            assertEquals(1, loadedSnapshots.size)
            assertEquals("value1", loadedSnapshots.first().tags?.get("tag1"))
            assertEquals("value2", loadedSnapshots.first().tags?.get("tag2"))
        }

    @Test
    fun `saveSnapshot persists counters to disk`() =
        runBlocking {
            val storage = createStorage(sessionStartAt = "session-1")

            val snapshot =
                DiagnosticsSnapshot(
                    tags = null,
                    counters = mapOf("counter1" to 10L, "counter2" to 20L),
                    histograms = null,
                    events = null,
                )
            storage.saveSnapshot(snapshot)
            delay(100)

            val newStorage = createStorage(sessionStartAt = "session-2")
            val loadedSnapshots = newStorage.loadAndClearPreviousSessions()

            assertEquals(1, loadedSnapshots.size)
            assertEquals(10L, loadedSnapshots.first().counters?.get("counter1"))
            assertEquals(20L, loadedSnapshots.first().counters?.get("counter2"))
        }

    @Test
    fun `saveSnapshot persists histograms to disk`() =
        runBlocking {
            val storage = createStorage(sessionStartAt = "session-1")

            val snapshot =
                DiagnosticsSnapshot(
                    tags = null,
                    counters = null,
                    histograms =
                        mapOf(
                            "metric" to HistogramSnapshot(count = 3, min = 5.0, max = 20.0, sum = 35.0),
                        ),
                    events = null,
                )
            storage.saveSnapshot(snapshot)
            delay(100)

            val newStorage = createStorage(sessionStartAt = "session-2")
            val loadedSnapshots = newStorage.loadAndClearPreviousSessions()

            assertEquals(1, loadedSnapshots.size)
            val loadedHistogram = loadedSnapshots.first().histograms?.get("metric")
            assertNotNull(loadedHistogram)
            assertEquals(3L, loadedHistogram?.count)
            assertEquals(5.0, loadedHistogram?.min)
            assertEquals(20.0, loadedHistogram?.max)
            assertEquals(35.0, loadedHistogram?.sum)
        }

    @Test
    fun `saveSnapshot persists events to disk`() =
        runBlocking {
            val storage = createStorage(sessionStartAt = "session-1")

            val events =
                listOf(
                    DiagnosticsEvent("event1", 100.0, mapOf("k" to "v")),
                    DiagnosticsEvent("event2", 200.0, null),
                )
            val snapshot =
                DiagnosticsSnapshot(
                    tags = null,
                    counters = null,
                    histograms = null,
                    events = events,
                )
            storage.saveSnapshot(snapshot)
            delay(100)

            val newStorage = createStorage(sessionStartAt = "session-2")
            val loadedSnapshots = newStorage.loadAndClearPreviousSessions()

            assertEquals(1, loadedSnapshots.size)
            val loadedEvents = loadedSnapshots.first().events
            assertNotNull(loadedEvents)
            assertEquals(2, loadedEvents?.size)
            assertTrue(loadedEvents?.any { it.eventName == "event1" } ?: false)
            assertTrue(loadedEvents?.any { it.eventName == "event2" } ?: false)
            val event1 = loadedEvents?.first { it.eventName == "event1" }
            assertEquals("v", event1?.eventProperties?.get("k"))
        }

    @Test
    fun `saveSnapshot persists complete snapshot to disk`() =
        runBlocking {
            val storage = createStorage(sessionStartAt = "session-1")

            val snapshot =
                DiagnosticsSnapshot(
                    tags = mapOf("tag" to "value"),
                    counters = mapOf("counter" to 42L),
                    histograms =
                        mapOf(
                            "metric" to HistogramSnapshot(count = 1, min = 10.0, max = 10.0, sum = 10.0),
                        ),
                    events = listOf(DiagnosticsEvent("event", 123.0, mapOf("k" to "v"))),
                )
            storage.saveSnapshot(snapshot)
            delay(100)

            val newStorage = createStorage(sessionStartAt = "session-2")
            val loadedSnapshots = newStorage.loadAndClearPreviousSessions()

            assertEquals(1, loadedSnapshots.size)
            val loaded = loadedSnapshots.first()
            assertEquals("value", loaded.tags?.get("tag"))
            assertEquals(42L, loaded.counters?.get("counter"))
            assertNotNull(loaded.histograms?.get("metric"))
            assertTrue(loaded.events?.any { it.eventName == "event" } ?: false)
        }

    @Test
    fun `loadAndClearPreviousSessions clears loaded sessions`() =
        runBlocking {
            val storage = createStorage(sessionStartAt = "session-1")

            val snapshot =
                DiagnosticsSnapshot(
                    tags = mapOf("tag" to "value"),
                    counters = mapOf("counter" to 1L),
                    histograms = null,
                    events = null,
                )
            storage.saveSnapshot(snapshot)
            delay(100)

            val newStorage = createStorage(sessionStartAt = "session-2")
            val firstLoad = newStorage.loadAndClearPreviousSessions()
            assertEquals(1, firstLoad.size)

            val anotherStorage = createStorage(sessionStartAt = "session-3")
            val secondLoad = anotherStorage.loadAndClearPreviousSessions()
            // session-1 was cleared, only session-2 should be empty if nothing was written
            assertTrue(secondLoad.isEmpty() || secondLoad.all { it.isEmpty() })
        }

    @Test
    fun `loadAndClearPreviousSessions ignores current session`() =
        runBlocking {
            val sessionId = "current-session"
            val storage = createStorage(sessionStartAt = sessionId)

            val snapshot =
                DiagnosticsSnapshot(
                    tags = mapOf("tag" to "value"),
                    counters = mapOf("counter" to 1L),
                    histograms = null,
                    events = null,
                )
            storage.saveSnapshot(snapshot)
            delay(100)

            // Load from same session - should not return anything
            val loadedSnapshots = storage.loadAndClearPreviousSessions()
            assertTrue(loadedSnapshots.isEmpty())
        }

    @Test
    fun `deleteActiveFiles removes counters histograms and events`() =
        runBlocking {
            val storage = createStorage(sessionStartAt = "session-1")

            val snapshot =
                DiagnosticsSnapshot(
                    tags = mapOf("tag" to "value"),
                    counters = mapOf("counter" to 1L),
                    histograms =
                        mapOf(
                            "metric" to HistogramSnapshot(count = 1, min = 1.0, max = 1.0, sum = 1.0),
                        ),
                    events = listOf(DiagnosticsEvent("event", 123.0, null)),
                )
            storage.saveSnapshot(snapshot)
            delay(100)

            storage.deleteActiveFiles()
            delay(100)

            val newStorage = createStorage(sessionStartAt = "session-2")
            val loadedSnapshots = newStorage.loadAndClearPreviousSessions()

            // After deletion, the session-1 directory should still have tags but no counters/histograms/events
            // The loadAndClearPreviousSessions will skip sessions that have no counters, histograms, or events
            assertTrue(
                loadedSnapshots.isEmpty() ||
                    loadedSnapshots.all {
                        it.counters.isNullOrEmpty() && it.histograms.isNullOrEmpty() && it.events.isNullOrEmpty()
                    },
            )
        }

    @Test
    fun `multiple saveSnapshot calls accumulate events`() =
        runBlocking {
            val storage = createStorage(sessionStartAt = "session-1")

            storage.saveSnapshot(
                DiagnosticsSnapshot(
                    tags = null,
                    counters = null,
                    histograms = null,
                    events = listOf(DiagnosticsEvent("event1", 100.0, null)),
                ),
            )
            delay(100)

            storage.saveSnapshot(
                DiagnosticsSnapshot(
                    tags = null,
                    counters = null,
                    histograms = null,
                    events = listOf(DiagnosticsEvent("event2", 200.0, null)),
                ),
            )
            delay(100)

            val newStorage = createStorage(sessionStartAt = "session-2")
            val loadedSnapshots = newStorage.loadAndClearPreviousSessions()

            assertEquals(1, loadedSnapshots.size)
            val events = loadedSnapshots.first().events
            assertNotNull(events)
            assertEquals(2, events?.size)
            assertTrue(events?.any { it.eventName == "event1" } ?: false)
            assertTrue(events?.any { it.eventName == "event2" } ?: false)
        }

    @Test
    fun `loadAndClearPreviousSessions returns empty when no previous sessions`() =
        runBlocking {
            val storage = createStorage(sessionStartAt = "session-1")
            val loadedSnapshots = storage.loadAndClearPreviousSessions()
            assertTrue(loadedSnapshots.isEmpty())
        }

    @Test
    fun `loadAndClearPreviousSessions skips empty sessions`() =
        runBlocking {
            val storage = createStorage(sessionStartAt = "session-1")

            // Save a snapshot with only tags (no counters, histograms, or events)
            val snapshot =
                DiagnosticsSnapshot(
                    tags = mapOf("tag" to "value"),
                    counters = null,
                    histograms = null,
                    events = null,
                )
            storage.saveSnapshot(snapshot)
            delay(100)

            val newStorage = createStorage(sessionStartAt = "session-2")
            val loadedSnapshots = newStorage.loadAndClearPreviousSessions()

            // Based on the implementation, loadSnapshot returns null if counters, histograms, and events are all empty
            assertTrue(loadedSnapshots.isEmpty())
        }

    private fun createStorage(sessionStartAt: String): DiagnosticsStorage {
        return DiagnosticsStorage(
            storageDirectory = storageDir,
            instanceName = "test-instance",
            sessionStartAt = sessionStartAt,
            logger = testLogger,
            coroutineScope = CoroutineScope(Dispatchers.IO),
            storageIODispatcher = Dispatchers.IO,
        )
    }
}
