package com.amplitude.core.platform.intercept

import com.amplitude.core.Configuration
import com.amplitude.core.events.BaseEvent
import com.amplitude.core.events.IdentifyEvent
import com.amplitude.core.events.IdentifyOperation
import com.amplitude.core.utilities.EventsFileStorage
import com.amplitude.core.utilities.FileStorageProvider
import com.amplitude.core.utilities.InMemoryStorage
import com.amplitude.core.utils.FakeAmplitude
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class IdentifyInterceptStorageHandlerTest {
    @Test
    fun `withFreshInsertId replaces the existing insert id`() {
        val event = BaseEvent()
        event.eventType = "\$identify"
        event.insertId = "original-insert-id"

        val transferred = event.withFreshInsertId()

        assertNotEquals("original-insert-id", transferred.insertId)
        assertNotNull(transferred.insertId)
        assertEquals(event, transferred)
    }

    @Nested
    inner class InMemoryHandler {
        @Test
        fun `transfer identify does not reuse the intercepted insert id`() =
            runTest {
                val storage = InMemoryStorage()
                val handler = IdentifyInterceptInMemoryStorageHandler(storage)
                storage.writeEvent(identifyEvent("original-insert-id", mapOf("key1" to "value1")))

                val transferred = handler.getTransferIdentifyEvent()

                assertNotNull(transferred)
                assertNotEquals("original-insert-id", transferred!!.insertId)
                assertEquals(
                    mapOf("key1" to "value1"),
                    transferred.userProperties?.get(IdentifyOperation.SET.operationType),
                )
            }
    }

    @Nested
    inner class FileHandler {
        @Test
        fun `transfer identify does not reuse the intercepted insert id`() {
            val amplitude =
                FakeAmplitude(
                    configuration =
                        Configuration(
                            apiKey = "test-api-key",
                            instanceName = "identify-intercept-${UUID.randomUUID()}",
                            identifyInterceptStorageProvider = FileStorageProvider(),
                        ),
                )
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
                storage.writeEvent(identifyEvent("original-insert-id", mapOf("key1" to "value1")))
                storage.writeEvent(identifyEvent("later-insert-id", mapOf("amp_flag" to "variant")))

                val transferred = handler.getTransferIdentifyEvent()
                assertNotNull(transferred)
                assertNotEquals("original-insert-id", transferred!!.insertId)
                assertNotEquals("later-insert-id", transferred.insertId)
                assertEquals(
                    mapOf("key1" to "value1", "amp_flag" to "variant"),
                    transferred.userProperties?.get(IdentifyOperation.SET.operationType),
                )
            }
        }
    }

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
