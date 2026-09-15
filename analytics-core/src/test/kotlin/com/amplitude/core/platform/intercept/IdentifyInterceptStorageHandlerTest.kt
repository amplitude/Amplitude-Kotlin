package com.amplitude.core.platform.intercept

import com.amplitude.core.Configuration
import com.amplitude.core.events.IdentifyEvent
import com.amplitude.core.events.IdentifyOperation
import com.amplitude.core.utilities.EventsFileStorage
import com.amplitude.core.utilities.FileStorageProvider
import com.amplitude.core.utilities.InMemoryStorage
import com.amplitude.core.utilities.JSONUtil
import com.amplitude.core.utils.FakeAmplitude
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class IdentifyInterceptStorageHandlerTest {
    @Nested
    inner class InMemoryHandler {
        @Test
        fun `transfer consumes identifies and preserves the batch insert id`() =
            runTest {
                val storage = InMemoryStorage()
                val handler = IdentifyInterceptInMemoryStorageHandler(storage)
                storage.writeEvent(identifyEvent("first-id", mapOf("key1" to "value1")))
                storage.writeEvent(identifyEvent("merged-id", mapOf("key2" to "value2")))

                val transferred = handler.getTransferIdentifyEvent()

                assertNotNull(transferred)
                assertEquals("first-id", transferred!!.insertId)
                assertEquals(
                    mapOf("key1" to "value1", "key2" to "value2"),
                    transferred.userProperties?.get(IdentifyOperation.SET.operationType),
                )
                assertNull(handler.getTransferIdentifyEvent())
            }
    }

    @Nested
    inner class FileHandler {
        @Test
        fun `transfer consumes one batch before accepting another`() {
            val amplitude = createAmplitude()
            runTest(amplitude.testDispatcher) {
                amplitude.isBuilt.await()
                advanceUntilIdle()

                val storage = amplitude.identifyInterceptStorage
                val handler =
                    IdentifyInterceptFileStorageHandler(
                        storage as EventsFileStorage,
                        amplitude.logger,
                        amplitude,
                    )
                storage.writeEvent(identifyEvent("first-id", mapOf("key1" to "value1")))

                val first = handler.getTransferIdentifyEvent()

                assertNotNull(first)
                assertEquals("first-id", first!!.insertId)
                assertNull(handler.getTransferIdentifyEvent())

                storage.writeEvent(identifyEvent("second-id", mapOf("key2" to "value2")))
                val second = handler.getTransferIdentifyEvent()

                assertNotNull(second)
                assertEquals("second-id", second!!.insertId)
                assertEquals(
                    mapOf("key2" to "value2"),
                    second.userProperties?.get(IdentifyOperation.SET.operationType),
                )
                assertEquals(
                    mapOf("key1" to "value1"),
                    first.userProperties?.get(IdentifyOperation.SET.operationType),
                )
            }
        }

        @Test
        fun `failed deletion does not make a consumed batch eligible again`() {
            val amplitude = createAmplitude()
            val files =
                linkedMapOf(
                    "batch-a" to listOf(identifyEvent("first-id", mapOf("key1" to "value1"))),
                )
            val storage = mockk<EventsFileStorage>()
            coEvery { storage.rollover() } returns Unit
            every { storage.readEventsContent() } answers { files.keys.toList() }
            coEvery { storage.getEventsString(any()) } answers {
                JSONUtil.eventsToString(files[firstArg()]!!)
            }
            every { storage.removeFile(any()) } returns false
            val handler = IdentifyInterceptFileStorageHandler(storage, amplitude.logger, amplitude)

            runTest(amplitude.testDispatcher) {
                val first = handler.getTransferIdentifyEvent()
                files["batch-b"] = listOf(identifyEvent("second-id", mapOf("key2" to "value2")))

                val second = handler.getTransferIdentifyEvent()

                assertNotNull(first)
                assertEquals("first-id", first!!.insertId)
                assertEquals(
                    mapOf("key1" to "value1"),
                    first.userProperties?.get(IdentifyOperation.SET.operationType),
                )
                assertNotNull(second)
                assertEquals("second-id", second!!.insertId)
                assertEquals(
                    mapOf("key2" to "value2"),
                    second.userProperties?.get(IdentifyOperation.SET.operationType),
                )
                assertNull(handler.getTransferIdentifyEvent())
            }
        }

        @Test
        fun `clear keeps failed deletions ineligible for transfer`() {
            val amplitude = createAmplitude()
            val files =
                linkedMapOf(
                    "batch-a" to listOf(identifyEvent("first-id", mapOf("key1" to "value1"))),
                )
            val storage = mockk<EventsFileStorage>()
            coEvery { storage.rollover() } returns Unit
            every { storage.readEventsContent() } answers { files.keys.toList() }
            coEvery { storage.getEventsString(any()) } answers {
                JSONUtil.eventsToString(files[firstArg()]!!)
            }
            every { storage.removeFile(any()) } returns false
            val handler = IdentifyInterceptFileStorageHandler(storage, amplitude.logger, amplitude)

            runTest(amplitude.testDispatcher) {
                handler.clearIdentifyIntercepts()

                assertNull(handler.getTransferIdentifyEvent())
            }
        }

        @Test
        fun `failed merge cannot join a later batch`() {
            val amplitude = createAmplitude()
            val files =
                linkedMapOf(
                    "batch-a" to listOf(identifyEvent("first-id", mapOf("key1" to "old"))),
                    "batch-b" to listOf(identifyEvent("second-id", mapOf("key2" to "new"))),
                )
            var failOldBatch = true
            val storage = mockk<EventsFileStorage>()
            coEvery { storage.rollover() } returns Unit
            every { storage.readEventsContent() } answers { files.keys.toList() }
            coEvery { storage.getEventsString(any()) } answers {
                val file = firstArg<String>()
                if (file == "batch-a" && failOldBatch) {
                    error("read failed")
                }
                JSONUtil.eventsToString(files[file]!!)
            }
            every { storage.removeFile(any()) } returns false
            val handler = IdentifyInterceptFileStorageHandler(storage, amplitude.logger, amplitude)

            runTest(amplitude.testDispatcher) {
                val first = handler.getTransferIdentifyEvent()
                failOldBatch = false
                files["batch-c"] = listOf(identifyEvent("third-id", mapOf("key3" to "later")))
                val second = handler.getTransferIdentifyEvent()

                assertEquals("second-id", first!!.insertId)
                assertEquals(
                    mapOf("key2" to "new"),
                    first.userProperties?.get(IdentifyOperation.SET.operationType),
                )
                assertEquals("third-id", second!!.insertId)
                assertEquals(
                    mapOf("key3" to "later"),
                    second.userProperties?.get(IdentifyOperation.SET.operationType),
                )
            }
        }
    }

    private fun createAmplitude(): FakeAmplitude =
        FakeAmplitude(
            configuration =
                Configuration(
                    apiKey = "test-api-key",
                    instanceName = "identify-intercept-${UUID.randomUUID()}",
                    identifyInterceptStorageProvider = FileStorageProvider(),
                ),
        )

    private fun identifyEvent(
        insertId: String,
        properties: Map<String, Any?>,
    ): IdentifyEvent {
        val event = IdentifyEvent()
        event.userId = "user_id"
        event.insertId = insertId
        event.userProperties =
            mutableMapOf(
                IdentifyOperation.SET.operationType to properties.toMutableMap(),
            )
        return event
    }
}
