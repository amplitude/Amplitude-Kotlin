package com.amplitude.android.streaming.internal.storage

import android.content.Context
import com.amplitude.android.Configuration
import com.amplitude.android.streaming.internal.DelayedEvent
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.File
import java.io.FileNotFoundException
import java.util.UUID
import kotlin.io.path.createTempDirectory

@OptIn(ExperimentalCoroutinesApi::class)
class DelayedEventStorageTest {
    private lateinit var amplitudeDir: File
    private lateinit var context: Context

    @BeforeEach
    fun setup() {
        amplitudeDir = createTempDirectory("amplitude").toFile()
        context =
            mockk {
                every { getDir("amplitude", Context.MODE_PRIVATE) } returns amplitudeDir
                every { packageName } returns "com.example.app"
            }
    }

    @AfterEach
    fun tearDown() {
        amplitudeDir.deleteRecursively()
    }

    @Nested
    inner class WriteAndRead {
        @Test
        fun `should create the queue directory and round trip a request`() =
            runTest {
                val instanceName = uniqueInstance()
                val storage = storage(instanceName)
                val request = request("view-1")

                storage.write("0000000000000000001-abc", request)

                assertTrue(queueDir(instanceName).isDirectory)
                assertEquals(request, storage.read("0000000000000000001-abc"))
            }

        @Test
        fun `should replace an existing file for the same key`() =
            runTest {
                val storage = storage()
                storage.write("key-1", request("view-1", timeoutMillis = 1_000L))
                storage.write("key-1", request("view-1", timeoutMillis = 9_000L))

                assertEquals(9_000L, storage.read("key-1").timeoutMillis)
                assertEquals(listOf("key-1"), storage.keys())
            }

        @Test
        fun `should ignore leftover tmp files after a successful write`() =
            runTest {
                val instanceName = uniqueInstance()
                val storage = storage(instanceName)
                storage.write("key-1", request())

                File(queueDir(instanceName), "key-1-leftover.tmp").writeText("{not-json")

                assertEquals(listOf("key-1"), storage.keys())
                assertEquals(request(), storage.read("key-1"))
            }

        @Test
        fun `should ignore unknown json keys when reading`() =
            runTest {
                val instanceName = uniqueInstance()
                val storage = storage(instanceName)
                val stored = request("view-1")
                storage.write("key-1", stored)

                val file = File(queueDir(instanceName), "key-1.json")
                val parsed = delayedEventsStorageJson.parseToJsonElement(file.readText()).jsonObject
                file.writeText(
                    JsonObject(
                        parsed.toMutableMap().apply {
                            put("schemaVersion", JsonPrimitive(2))
                        },
                    ).toString(),
                )

                assertEquals(stored, storage.read("key-1"))
            }

        @Test
        fun `should throw when the file is missing`() =
            runTest {
                val storage = storage()
                assertThrows<FileNotFoundException> { storage.read("missing") }
            }
    }

    @Nested
    inner class Keys {
        @Test
        fun `should return no keys when the directory is empty`() =
            runTest {
                val storage = storage()
                assertEquals(emptyList<String>(), storage.keys())
            }

        @Test
        fun `should return every stored key`() =
            runTest {
                val storage = storage()
                storage.write("0000000000000000002-bbb", request("view-2"))
                storage.write("0000000000000000001-aaa", request("view-1"))
                storage.write("0000000000000000003-ccc", request("view-3"))

                assertEquals(
                    listOf(
                        "0000000000000000001-aaa",
                        "0000000000000000002-bbb",
                        "0000000000000000003-ccc",
                    ),
                    storage.keys().sorted(),
                )
            }

        @Test
        fun `should isolate files by instance name`() =
            runTest {
                val first = uniqueInstance()
                val second = uniqueInstance()
                storage(first).write("key-1", request("view-1"))
                storage(second).write("key-2", request("view-2"))

                assertEquals(listOf("key-1"), storage(first).keys())
                assertEquals(listOf("key-2"), storage(second).keys())
            }
    }

    @Nested
    inner class FindKey {
        @Test
        fun `should return null when no filename matches the hashed id`() =
            runTest {
                val storage = storage()
                storage.write("0000000000000000001-aaa", request())
                assertNull(storage.findKey("bbb"))
            }

        @Test
        fun `should keep the oldest duplicate and delete the rest`() =
            runTest {
                val storage = storage()
                storage.write("0000000000000000001-deadbeef", request("view-1", timeoutMillis = 1_000L))
                storage.write("0000000000000000003-deadbeef", request("view-1", timeoutMillis = 3_000L))
                storage.write("0000000000000000002-deadbeef", request("view-1", timeoutMillis = 2_000L))
                storage.write("0000000000000000004-other", request("view-2"))

                assertEquals("0000000000000000001-deadbeef", storage.findKey("deadbeef"))
                assertEquals(
                    setOf("0000000000000000001-deadbeef", "0000000000000000004-other"),
                    storage.keys().toSet(),
                )
                assertEquals(1_000L, storage.read("0000000000000000001-deadbeef").timeoutMillis)
            }
    }

    @Nested
    inner class Delete {
        @Test
        fun `should remove a stored request`() =
            runTest {
                val storage = storage()
                storage.write("key-1", request())
                storage.delete("key-1")

                assertEquals(emptyList<String>(), storage.keys())
                assertThrows<FileNotFoundException> { storage.read("key-1") }
            }

        @Test
        fun `should no-op when the key does not exist`() =
            runTest {
                val storage = storage()
                storage.delete("missing")
                assertEquals(emptyList<String>(), storage.keys())
            }
    }

    private fun TestScope.storage(instanceName: String = uniqueInstance()): DelayedEventStorage =
        DelayedEventStorage(
            context = context,
            configuration =
                Configuration(
                    apiKey = "test-api-key",
                    context = context,
                    instanceName = instanceName,
                ),
            ioDispatcher = StandardTestDispatcher(testScheduler),
        )

    private fun uniqueInstance(): String = UUID.randomUUID().toString()

    private fun queueDir(instanceName: String): File =
        File(
            amplitudeDir,
            "com.example.app/$instanceName/analytics/streaming-delayed-events",
        )

    private fun request(
        id: String = "view-1",
        timeoutMillis: Long = 5_000L,
    ): DelayedEventsRequestEntity =
        DelayedEventsRequestEntity(
            id = id,
            timeoutMillis = timeoutMillis,
            events =
                listOf(
                    DelayedEvent(
                        eventType = "video_stopped",
                        kind = DelayedEvent.Kind.DELAYED,
                        timestamp = 1L,
                        eventProperties = mutableMapOf("video_id" to "v-100"),
                    ).toEntity(),
                ),
        )
}
