package com.amplitude.id.utilities

import com.amplitude.common.Logger
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTimeoutPreemptively
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.time.Duration
import java.util.Properties
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

@OptIn(ExperimentalCoroutinesApi::class)
class PropertiesFileTest {
    @TempDir
    lateinit var directory: File

    private val logger = mockk<Logger>(relaxed = true)
    private val propertiesFile get() = File(directory, "values.properties")
    private val backupFile get() = File(directory, "values.properties.bak")

    @Nested
    inner class Loading {
        @Test
        fun `should return defaults without creating a missing file`() = runTest {
            val store = createStore(StandardTestDispatcher(testScheduler))

            assertEquals(42L, store.getLong("missing", 42L))
            advanceUntilIdle()

            assertFalse(propertiesFile.exists())
        }

        @Test
        fun `should load existing values without rewriting the file`() = runTest {
            propertiesFile.writeText("# keep this comment\ncounter=7\ninvalid=hello\n")
            val original = propertiesFile.readBytes()
            val store = createStore(StandardTestDispatcher(testScheduler))
            advanceUntilIdle()

            assertEquals(7L, store.getLong("counter", 0L))
            assertEquals(9L, store.getLong("invalid", 9L))
            assertArrayEquals(original, propertiesFile.readBytes())
        }

        @Test
        fun `should allow first access on the injected dispatcher`() {
            assertTimeoutPreemptively(Duration.ofSeconds(5)) {
                runTest {
                    propertiesFile.writeText("counter=7\n")
                    val dispatcher = StandardTestDispatcher(testScheduler)
                    val result = async(dispatcher) { createStore(dispatcher).getLong("counter", 0L) }

                    advanceUntilIdle()

                    assertEquals(7L, result.await())
                }
            }
        }

        @Test
        fun `should allow first access on an already limited IO dispatcher`() {
            propertiesFile.writeText("counter=7\n")
            val executor = Executors.newSingleThreadExecutor { Thread(it).apply { isDaemon = true } }
            executor.asCoroutineDispatcher().use { dispatcher ->
                assertTimeoutPreemptively(Duration.ofSeconds(5)) {
                    runTest {
                        val serialized = dispatcher.limitedParallelism(1)
                        val result = async(serialized) { createStore(serialized).getLong("counter", 0L) }

                        assertEquals(7L, result.await())
                    }
                }
            }
        }

        @Test
        fun `should preserve loaded values when writing before background loading starts`() = runTest {
            propertiesFile.writeText("existing=7\n")
            val store = createStore(StandardTestDispatcher(testScheduler))

            assertTrue(store.putLong("new", 8L))
            advanceUntilIdle()

            val saved = readProperties()
            assertEquals("7", saved.getProperty("existing"))
            assertEquals("8", saved.getProperty("new"))
        }

        @Test
        fun `should load before deleting a key`() = runTest {
            propertiesFile.writeText("keep=7\nremove=8\n")
            val store = createStore(StandardTestDispatcher(testScheduler))

            store.deleteKey("remove")
            advanceUntilIdle()

            assertEquals("7", readProperties().getProperty("keep"))
            assertFalse(readProperties().containsKey("remove"))
        }

        @Test
        fun `should reject mutations and preserve the file after a load failure`() = runTest {
            propertiesFile.writeText("counter=7\nbroken=\\uZZZZ\n")
            val original = propertiesFile.readBytes()
            val store = createStore(StandardTestDispatcher(testScheduler))

            assertEquals(42L, store.getLong("counter", 42L))
            assertFalse(store.putLong("new", 8L))
            store.deleteKey("counter")
            advanceUntilIdle()

            assertArrayEquals(original, propertiesFile.readBytes())
            verify(exactly = 1) { logger.error(match { it.startsWith("Failed to load") }) }
        }

        @Test
        fun `should wait for an already running load even when it fails`() {
            propertiesFile.writeText("broken=\\uZZZZ\n")
            val loadEntered = CountDownLatch(1)
            val releaseLoad = CountDownLatch(1)
            val readStarted = CountDownLatch(1)
            val executor = Executors.newSingleThreadExecutor { Thread(it).apply { isDaemon = true } }
            every { logger.error(any()) } answers {
                loadEntered.countDown()
                check(releaseLoad.await(5, TimeUnit.SECONDS))
            }

            try {
                val store = createStore(Dispatchers.IO)
                assertTrue(loadEntered.await(5, TimeUnit.SECONDS))
                val result =
                    executor.submit<Long> {
                        readStarted.countDown()
                        store.getLong("counter", 42L)
                    }
                assertTrue(readStarted.await(5, TimeUnit.SECONDS))
                assertThrows<TimeoutException> { result.get(100, TimeUnit.MILLISECONDS) }

                releaseLoad.countDown()

                assertEquals(42L, result.get(5, TimeUnit.SECONDS))
            } finally {
                releaseLoad.countDown()
                executor.shutdownNow()
            }
        }
    }

    @Nested
    inner class Ownership {
        @Test
        fun `should share the store across canonical paths`() = runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val first = createStore(dispatcher)
            File(directory, "nested").mkdir()
            val second =
                PropertiesFile.getInstance(
                    File(directory, "nested/..").path,
                    "values.properties",
                    ioDispatcher = dispatcher,
                )

            assertSame(first, second)
        }

        @Test
        fun `should preserve changes through different handles to the same file`() = runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val first = createStore(dispatcher)
            val second = createStore(dispatcher)

            first.putLong("first", 1L)
            second.putLong("second", 2L)
            first.deleteKey("first")
            assertEquals(2L, first.getLong("second", 0L))
            assertEquals(0L, second.getLong("first", 0L))
            advanceUntilIdle()

            assertEquals(mapOf("second" to "2"), readProperties())
        }

        @Test
        fun `should share the store when factories race`() = runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val executor = Executors.newFixedThreadPool(4)
            try {
                val handles = executor.invokeAll(List(8) { Callable { createStore(dispatcher) } })
                val first = handles.first().get(5, TimeUnit.SECONDS)

                handles.forEach { assertSame(first, it.get(5, TimeUnit.SECONDS)) }
            } finally {
                executor.shutdownNow()
            }
            advanceUntilIdle()
        }
    }

    @Nested
    inner class Saving {
        @Test
        fun `should update memory immediately and save asynchronously`() = runTest {
            propertiesFile.writeText("counter=1\n")
            val store = createStore(StandardTestDispatcher(testScheduler))

            assertTrue(store.putLong("counter", 2L))
            assertEquals(2L, store.getLong("counter", 0L))
            assertEquals("1", readProperties().getProperty("counter"))
            advanceUntilIdle()

            assertEquals("2", readProperties().getProperty("counter"))
            assertFalse(backupFile.exists())
            assertEquals(listOf(propertiesFile.name), directory.list()?.toList())
        }

        @Test
        fun `should delete from memory immediately and persist the deletion asynchronously`() = runTest {
            propertiesFile.writeText("counter=1\n")
            val store = createStore(StandardTestDispatcher(testScheduler))

            store.deleteKey("counter")
            assertEquals(42L, store.getLong("counter", 42L))
            assertEquals("1", readProperties().getProperty("counter"))
            advanceUntilIdle()

            assertFalse(readProperties().containsKey("counter"))
        }

        @Test
        fun `should create missing parent directories`() = runTest {
            val parent = File(directory, "nested/storage")
            val store =
                PropertiesFile.getInstance(
                    parent.path,
                    "values.properties",
                    ioDispatcher = StandardTestDispatcher(testScheduler),
                )

            store.putLong("counter", 1L)
            advanceUntilIdle()

            assertEquals("1", readProperties(File(parent, "values.properties")).getProperty("counter"))
        }

        @Test
        fun `should save when another writer creates the parent directory first`() = runTest {
            var racedCreation = false
            val parent =
                object : File(directory, "raced") {
                    override fun mkdirs(): Boolean {
                        // Simulate another writer creating the directory before our mkdirs fails.
                        racedCreation = File(path).mkdirs()
                        return false
                    }
                }
            val file =
                object : File(parent, "values.properties") {
                    override fun getAbsoluteFile(): File = this

                    override fun getParentFile(): File = parent
                }
            val constructor =
                PropertiesFile::class.java.getDeclaredConstructor(
                    File::class.java,
                    Logger::class.java,
                    CoroutineDispatcher::class.java,
                )
            constructor.isAccessible = true
            val store = constructor.newInstance(file, logger, StandardTestDispatcher(testScheduler))

            assertTrue(store.putLong("counter", 1L))
            advanceUntilIdle()

            assertTrue(racedCreation)
            assertEquals("1", readProperties(file).getProperty("counter"))
            verify(exactly = 0) { logger.error(any()) }
        }

        @Test
        fun `should replace an existing file on successive saves`() = runTest {
            val store = createStore(StandardTestDispatcher(testScheduler))

            repeat(3) {
                store.putLong("counter", it.toLong())
                advanceUntilIdle()
                assertEquals(it.toString(), readProperties().getProperty("counter"))
                assertFalse(backupFile.exists())
            }
        }

        @Test
        fun `should coalesce pending saves and retain the original on failure`() = runTest {
            propertiesFile.writeText("counter=1\n")
            val original = propertiesFile.readBytes()
            val store = createStore(StandardTestDispatcher(testScheduler))
            store.getLong("counter", 0L)
            backupFile.mkdir()

            repeat(10) { store.putLong("counter", it.toLong()) }
            advanceUntilIdle()

            assertEquals(9L, store.getLong("counter", 0L))
            assertArrayEquals(original, propertiesFile.readBytes())
            verify(exactly = 1) { logger.error(match { it.startsWith("Failed to save") }) }

            backupFile.delete()
            store.putLong("counter", 10L)
            advanceUntilIdle()

            assertEquals("10", readProperties().getProperty("counter"))
        }

        @Test
        fun `should not rewrite the file when writing an unchanged persisted value`() = runTest {
            propertiesFile.writeText("# keep this comment\ncounter=1\n")
            val original = propertiesFile.readBytes()
            val store = createStore(StandardTestDispatcher(testScheduler))

            assertTrue(store.putLong("counter", 1L))
            advanceUntilIdle()

            assertArrayEquals(original, propertiesFile.readBytes())
            verify(exactly = 0) { logger.error(any()) }
        }

        @Test
        fun `should retry a failed save when writing the same value again`() = runTest {
            propertiesFile.writeText("counter=1\n")
            val store = createStore(StandardTestDispatcher(testScheduler))
            store.getLong("counter", 0L)
            assertTrue(backupFile.mkdir())

            assertTrue(store.putLong("counter", 2L))
            advanceUntilIdle()

            assertEquals(2L, store.getLong("counter", 0L))
            assertEquals("1", readProperties().getProperty("counter"))
            verify(exactly = 1) { logger.error(match { it.startsWith("Failed to save") }) }

            assertTrue(backupFile.delete())
            assertTrue(store.putLong("counter", 2L))
            advanceUntilIdle()

            assertEquals("2", readProperties().getProperty("counter"))
            assertFalse(backupFile.exists())
            verify(exactly = 1) { logger.error(any()) }

            // A successful retry should clear the failure and skip further unchanged writes.
            assertTrue(backupFile.mkdir())
            assertTrue(store.putLong("counter", 2L))
            advanceUntilIdle()

            assertEquals("2", readProperties().getProperty("counter"))
            verify(exactly = 1) { logger.error(any()) }
        }

        @Test
        fun `should save a mutation queued while a save is running`() = runTest {
            propertiesFile.writeText("counter=1\n")
            val store = createStore(StandardTestDispatcher(testScheduler))
            store.getLong("counter", 0L)
            backupFile.mkdir()
            every { logger.error(any()) } answers {
                check(backupFile.delete())
                store.putLong("counter", 3L)
                Unit
            }

            store.putLong("counter", 2L)
            advanceUntilIdle()

            assertEquals("3", readProperties().getProperty("counter"))
            verify(exactly = 1) { logger.error(any()) }
        }
    }

    @Nested
    inner class KeyValueOperations {
        @Test
        fun `should return the supplied default for a missing string`() = runTest {
            val store = createStore(StandardTestDispatcher(testScheduler))

            assertNull(store.getString("missing", null))
            assertEquals("fallback", store.getString("missing", "fallback"))
            advanceUntilIdle()

            assertFalse(propertiesFile.exists())
        }

        @Test
        fun `should update strings immediately and persist them asynchronously`() = runTest {
            propertiesFile.writeText("existing=7\n")
            val original = propertiesFile.readBytes()
            val store = createStore(StandardTestDispatcher(testScheduler))
            val value = "hello\n世界 = : \\"

            assertTrue(store.putString("message", value))
            assertTrue(store.putString("empty", ""))
            assertEquals(value, store.getString("message", null))
            assertEquals("", store.getString("empty", "fallback"))
            assertArrayEquals(original, propertiesFile.readBytes())
            advanceUntilIdle()

            val saved = readProperties()
            assertEquals(value, saved.getProperty("message"))
            assertEquals("", saved.getProperty("empty"))
            assertEquals("7", saved.getProperty("existing"))
        }

        @Test
        fun `should remove selected keys immediately and persist them together`() = runTest {
            propertiesFile.writeText("counter=7\nremove=value\nkeep=retained\n")
            val store = createStore(StandardTestDispatcher(testScheduler))

            assertTrue(store.remove(listOf("counter", "remove", "missing")))

            assertEquals(42L, store.getLong("counter", 42L))
            assertNull(store.getString("remove", null))
            assertEquals("retained", store.getString("keep", null))
            assertEquals("7", readProperties().getProperty("counter"))
            advanceUntilIdle()

            assertEquals(mapOf("keep" to "retained"), readProperties())
        }

        @Test
        fun `should persist an empty store when removing every key`() = runTest {
            propertiesFile.writeText("counter=7\nmessage=value\n")
            val store = createStore(StandardTestDispatcher(testScheduler))

            assertTrue(store.remove(listOf("counter", "message")))

            assertEquals(42L, store.getLong("counter", 42L))
            assertNull(store.getString("message", null))
            assertFalse(readProperties().isEmpty())
            advanceUntilIdle()

            assertTrue(propertiesFile.exists())
            assertTrue(readProperties().isEmpty())
            assertTrue(store.putString("new", "value"))
            advanceUntilIdle()
            assertEquals(mapOf("new" to "value"), readProperties())
        }

        @Test
        fun `should reject string and bulk mutations after a load failure`() = runTest {
            propertiesFile.writeText("counter=7\nbroken=\\uZZZZ\n")
            val original = propertiesFile.readBytes()
            val store = createStore(StandardTestDispatcher(testScheduler))

            assertNull(store.getString("counter", null))
            assertEquals("fallback", store.getString("counter", "fallback"))
            assertFalse(store.putString("new", "value"))
            assertFalse(store.remove(listOf("counter")))
            advanceUntilIdle()

            assertArrayEquals(original, propertiesFile.readBytes())
            verify(exactly = 1) { logger.error(match { it.startsWith("Failed to load") }) }
        }
    }

    @Nested
    inner class Compatibility {
        @Test
        fun `should load through the existing public constructor`() {
            propertiesFile.writeText("counter=7\nmessage=value\n")
            val store = PropertiesFile(directory, "values", logger)

            store.load()

            assertEquals(7L, store.getLong("counter", 0L))
            assertEquals("value", store.getString("message", null))
        }

        @Test
        fun `should remove a single key through the existing method`() = runTest {
            propertiesFile.writeText("counter=7\nkeep=retained\n")
            val store = createStore(StandardTestDispatcher(testScheduler))

            assertTrue(store.remove("counter"))
            advanceUntilIdle()

            assertEquals(mapOf("keep" to "retained"), readProperties())
        }

        @Test
        fun `should delete the file without pending saves recreating it`() = runTest {
            propertiesFile.writeText("counter=1\n")
            val store = createStore(StandardTestDispatcher(testScheduler))
            store.putLong("counter", 2L)

            store.deletePropertiesFile()

            assertFalse(propertiesFile.exists())
            assertEquals(42L, store.getLong("counter", 42L))
            advanceUntilIdle()
            assertFalse(propertiesFile.exists())
            assertFalse(backupFile.exists())
        }

        @Test
        fun `should persist writes after deletion even when a prior save was queued`() = runTest {
            propertiesFile.writeText("counter=1\nkeep=old\n")
            val store = createStore(StandardTestDispatcher(testScheduler))
            store.putLong("counter", 2L)
            store.deletePropertiesFile()

            assertTrue(store.putLong("counter", 3L))
            advanceUntilIdle()

            assertEquals(mapOf("counter" to "3"), readProperties())
        }
    }

    @Nested
    inner class Recovery {
        @Test
        fun `should restore the previous commit when replacement was interrupted`() = runTest {
            backupFile.writeText("counter=1\n")
            propertiesFile.writeText("counter=2\nbroken=\\uZZZZ\n")
            val store = createStore(StandardTestDispatcher(testScheduler))

            assertEquals(1L, store.getLong("counter", 0L))
            assertFalse(backupFile.exists())
            assertEquals("1", readProperties().getProperty("counter"))

            store.putLong("counter", 3L)
            advanceUntilIdle()

            assertEquals("3", readProperties().getProperty("counter"))
        }

        @Test
        fun `should restore the backup when interrupted before installing the new file`() = runTest {
            backupFile.writeText("counter=1\n")
            val store = createStore(StandardTestDispatcher(testScheduler))

            assertEquals(1L, store.getLong("counter", 0L))
            advanceUntilIdle()

            assertTrue(propertiesFile.isFile)
            assertFalse(backupFile.exists())
        }

        @Test
        fun `should preserve the original if the backup path cannot be recovered`() = runTest {
            propertiesFile.writeText("counter=1\n")
            val original = propertiesFile.readBytes()
            backupFile.mkdir()
            val store = createStore(StandardTestDispatcher(testScheduler))

            assertEquals(42L, store.getLong("counter", 42L))
            assertFalse(store.putLong("counter", 2L))
            advanceUntilIdle()

            assertArrayEquals(original, propertiesFile.readBytes())
            assertTrue(backupFile.isDirectory)
        }
    }

    private fun createStore(dispatcher: CoroutineDispatcher): PropertiesFile =
        PropertiesFile.getInstance(directory.path, propertiesFile.name, logger, dispatcher)

    private fun readProperties(file: File = propertiesFile): Properties = Properties().apply {
        file.inputStream().use { load(it) }
    }
}
