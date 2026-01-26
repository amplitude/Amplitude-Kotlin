package com.amplitude.core.diagnostics

import com.amplitude.common.Logger
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File
import java.io.FileOutputStream

class DiagnosticsStorageTest {
    @Test
    fun `merge filters out zero-count histograms`() =
        runTest {
            val storage = createStorage(shouldStore = true, testScope = this)
            val histograms = mapOf("empty" to HistogramStats())
            val snapshot =
                DiagnosticsSnapshot(
                    tags = emptyMap(),
                    counters = emptyMap(),
                    histograms = histograms,
                    events = emptyList(),
                )

            val merged = snapshot.merge(DiagnosticsSnapshot.empty())
            assertTrue(merged.histograms.isEmpty())

            storageRoot(storage).deleteRecursively()
        }

    @Test
    fun `setTag and setTags persist in snapshot`() =
        runTest {
            val storage = createStorage(shouldStore = true, testScope = this)

            storage.setTag("tag1", "value1")
            storage.setTags(mapOf("tag2" to "value2", "tag1" to "value3"))

            val snapshot = storage.dumpAndClearCurrentSession()
            assertEquals("value3", snapshot.tags["tag1"])
            assertEquals("value2", snapshot.tags["tag2"])
            assertEquals(2, snapshot.tags.size)
            storageRoot(storage).deleteRecursively()
        }

    @Test
    fun `increment accumulates counters`() =
        runTest {
            val storage = createStorage(shouldStore = true, testScope = this)

            storage.increment("counter", 5)
            storage.increment("counter", 3)

            val snapshot = storage.dumpAndClearCurrentSession()
            assertEquals(8, snapshot.counters["counter"])
            storageRoot(storage).deleteRecursively()
        }

    @Test
    fun `recordHistogram tracks stats`() =
        runTest {
            val storage = createStorage(shouldStore = true, testScope = this)

            storage.recordHistogram("metric", 10.0)
            storage.recordHistogram("metric", 5.0)
            storage.recordHistogram("metric", 20.0)

            val snapshot = storage.dumpAndClearCurrentSession()
            val stats = requireNotNull(snapshot.histograms["metric"]?.snapshot())
            assertEquals(3, stats.count)
            assertEquals(35.0, stats.sum)
            assertEquals(5.0, stats.min)
            assertEquals(20.0, stats.max)
            storageRoot(storage).deleteRecursively()
        }

    @Test
    fun `recordEvent supports properties and limit`() =
        runTest {
            val storage = createStorage(shouldStore = true, testScope = this)

            storage.recordEvent("event1", mapOf("k" to "v"))
            for (i in 0 until 10) {
                storage.recordEvent("event_$i", null)
            }

            val snapshot = storage.dumpAndClearCurrentSession()
            assertEquals(10, snapshot.events.size)
            val first = snapshot.events.first { it.eventName == "event1" }
            assertEquals("v", first.eventProperties?.get("k") as? String)
            storageRoot(storage).deleteRecursively()
        }

    @Test
    fun `loadPreviousSessions includes rotated event logs`() =
        runTest {
            val sessionStartAt = "session-one"
            val storage =
                createStorage(
                    shouldStore = true,
                    testScope = this,
                    sessionStartAt = sessionStartAt,
                )

            storage.recordEvent("event_1", null)
            storage.persistIfNeeded()

            val storageDir = storageRoot(storage)
            val diagnosticsRoot = File(storageDir, "com.amplitude.diagnostics")
            val instanceDir = diagnosticsRoot.listFiles()?.firstOrNull { it.isDirectory }
            assertNotNull(instanceDir)
            val sessionDir = File(requireNotNull(instanceDir), sessionStartAt)
            val eventsLog = File(sessionDir, "events.log")
            assertTrue(eventsLog.exists())

            FileOutputStream(eventsLog, true).use { output ->
                output.write(ByteArray(300 * 1024))
            }

            storage.recordEvent("event_2", null)
            storage.persistIfNeeded()

            val newStorage =
                createStorage(
                    shouldStore = true,
                    sessionStartAt = "session-two",
                    storageDirectory = storageDir,
                    testScope = this,
                )

            val snapshots = newStorage.loadAndClearPreviousSessions()
            val allEvents = snapshots.flatMap { it.events }
            assertTrue(allEvents.any { it.eventName == "event_1" })
            assertTrue(allEvents.any { it.eventName == "event_2" })

            storageDir.deleteRecursively()
        }

    @Test
    fun `dump clears counters histograms events but keeps tags`() =
        runTest {
            val storage = createStorage(shouldStore = true, testScope = this)

            storage.setTag("tag1", "value1")
            storage.increment("counter", 1)
            storage.recordHistogram("metric", 1.0)
            storage.recordEvent("event1", null)

            val snapshot1 = storage.dumpAndClearCurrentSession()
            assertEquals("value1", snapshot1.tags["tag1"])
            assertEquals(1, snapshot1.counters["counter"])
            assertEquals(1, snapshot1.events.size)

            val snapshot2 = storage.dumpAndClearCurrentSession()
            assertEquals("value1", snapshot2.tags["tag1"])
            assertTrue(snapshot2.counters.isEmpty())
            assertTrue(snapshot2.histograms.isEmpty())
            assertTrue(snapshot2.events.isEmpty())
            storageRoot(storage).deleteRecursively()
        }

    @Test
    fun `persist and load previous sessions`() =
        runTest {
            val storage = createStorage(shouldStore = true, testScope = this)
            val storageDir = storageRoot(storage)
            storage.setTag("tag", "value")
            storage.increment("counter", 42)
            storage.recordHistogram("metric", 10.0)
            storage.recordEvent("event", mapOf("k" to "v"))
            storage.persistIfNeeded()

            val newStorage =
                createStorage(
                    shouldStore = true,
                    sessionStartAt = "new-session",
                    storageDirectory = storageDir,
                    testScope = this,
                )
            val snapshots = newStorage.loadAndClearPreviousSessions()
            assertTrue(snapshots.isNotEmpty())
            val snapshot = snapshots.first()
            assertEquals("value", snapshot.tags["tag"])
            assertEquals(42, snapshot.counters["counter"])
            assertNotNull(snapshot.histograms["metric"])
            assertTrue(snapshot.events.any { it.eventName == "event" })

            storageRoot(storage).deleteRecursively()
        }

    @Test
    fun `no persistence when shouldStore is false`() =
        runTest {
            val storage = createStorage(shouldStore = false, testScope = this)
            val storageDir = storageRoot(storage)
            storage.setTag("tag", "value")
            storage.persistIfNeeded()

            val newStorage =
                createStorage(
                    shouldStore = true,
                    sessionStartAt = "new-session",
                    storageDirectory = storageDir,
                    testScope = this,
                )
            val snapshots = newStorage.loadAndClearPreviousSessions()
            assertEquals(0, snapshots.size)

            storageRoot(storage).deleteRecursively()
        }

    private fun createStorage(
        shouldStore: Boolean,
        sessionStartAt: String = "session-${System.currentTimeMillis()}",
        storageDirectory: File? = null,
        testScope: TestScope,
    ): DiagnosticsStorage {
        val logger =
            object : Logger {
                override var logMode: Logger.LogMode = Logger.LogMode.OFF

                override fun debug(message: String) {}

                override fun error(message: String) {}

                override fun info(message: String) {}

                override fun warn(message: String) {}
            }
        val dispatcher = StandardTestDispatcher(testScope.testScheduler)
        val storageDir = storageDirectory ?: createTempDir(prefix = "diag-storage")
        return DiagnosticsStorage(
            storageDirectory = storageDir,
            instanceName = "test-instance",
            sessionStartAt = sessionStartAt,
            logger = logger,
            coroutineScope = testScope,
            storageIODispatcher = dispatcher,
            persistIntervalMillis = 1,
            shouldStore = shouldStore,
        )
    }

    private fun storageRoot(storage: DiagnosticsStorage): File {
        val field = DiagnosticsStorage::class.java.getDeclaredField("storageDirectory")
        field.isAccessible = true
        return field.get(storage) as File
    }
}
