package com.amplitude.core.platform.intercept

import com.amplitude.core.Configuration
import com.amplitude.core.EventCallBack
import com.amplitude.core.Storage
import com.amplitude.core.events.BaseEvent
import com.amplitude.core.events.IdentifyEvent
import com.amplitude.core.events.IdentifyOperation
import com.amplitude.core.platform.EventPipeline
import com.amplitude.core.platform.plugins.AmplitudeDestination
import com.amplitude.core.utilities.EventsFileStorage
import com.amplitude.core.utilities.JSONUtil
import com.amplitude.core.utilities.http.ResponseHandler
import com.amplitude.core.utils.FakeAmplitude
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.json.JSONArray
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class IdentifyInterceptorConcurrencyTest {
    @Test
    fun `overlapping transfers emit a batch once`() {
        val amplitude = FakeAmplitude(Configuration(apiKey = "test-api-key"))
        val storage = BlockingEventsStorage()
        val transferred = mutableListOf<BaseEvent>()
        val destination = mockk<AmplitudeDestination>()
        every { destination.enqueuePipeline(capture(transferred)) } just runs
        val interceptor =
            IdentifyInterceptor(
                storage,
                amplitude,
                amplitude.logger,
                amplitude.configuration,
                destination,
            )

        runTest(amplitude.testDispatcher) {
            storage.writeEvent(identifyEvent("batch-id", mapOf("key1" to "value1")))
            storage.blockNextRead()
            val first = launch { interceptor.transferInterceptedIdentify() }
            storage.readStarted.await()
            val overlapping = List(3) { launch { interceptor.transferInterceptedIdentify() } }

            storage.continueRead.complete(Unit)
            first.join()
            overlapping.forEach { it.join() }

            assertEquals(1, transferred.size)
            assertEquals("batch-id", transferred.single().insertId)
        }
    }

    @Test
    fun `cancellation during transfer still hands consumed batch to pipeline`() {
        val amplitude = FakeAmplitude(Configuration(apiKey = "test-api-key"))
        val storage = BlockingEventsStorage()
        val transferred = mutableListOf<BaseEvent>()
        val destination = mockk<AmplitudeDestination>()
        every { destination.enqueuePipeline(capture(transferred)) } just runs
        val interceptor =
            IdentifyInterceptor(
                storage,
                amplitude,
                amplitude.logger,
                amplitude.configuration,
                destination,
            )

        runTest(amplitude.testDispatcher) {
            storage.writeEvent(identifyEvent("batch-id", mapOf("key1" to "value1")))
            storage.blockNextRead()
            val transfer = launch { interceptor.transferInterceptedIdentify() }
            storage.readStarted.await()

            transfer.cancel()
            runCurrent()
            assertFalse(transfer.isCompleted)
            storage.continueRead.complete(Unit)
            transfer.join()

            assertEquals(1, transferred.size)
            assertEquals("batch-id", transferred.single().insertId)
        }
    }

    @Test
    fun `failed merge of prior user cannot join a later identity batch`() {
        val amplitude = FakeAmplitude(Configuration(apiKey = "test-api-key"))
        val storage = BlockingEventsStorage()
        val transferred = mutableListOf<BaseEvent>()
        val destination = mockk<AmplitudeDestination>()
        every { destination.enqueuePipeline(capture(transferred)) } just runs
        val interceptor =
            IdentifyInterceptor(
                storage,
                amplitude,
                amplitude.logger,
                amplitude.configuration,
                destination,
            )

        runTest(amplitude.testDispatcher) {
            interceptor.intercept(identifyEvent("first-id", mapOf("key1" to "old"), userId = "user-a"))
            storage.failNextRead()
            interceptor.intercept(identifyEvent("second-id", mapOf("key2" to "new"), userId = "user-b"))
            interceptor.transferInterceptedIdentify()

            assertEquals(1, transferred.size)
            assertEquals("second-id", transferred.single().insertId)
            assertEquals("user-b", transferred.single().userId)
            assertEquals(
                mapOf("key2" to "new"),
                transferred.single().userProperties?.get(IdentifyOperation.SET.operationType),
            )
        }
    }

    @Test
    fun `identify written during transfer belongs to the next batch`() {
        val amplitude = FakeAmplitude(Configuration(apiKey = "test-api-key"))
        val storage = BlockingEventsStorage()
        val transferred = mutableListOf<BaseEvent>()
        val destination = mockk<AmplitudeDestination>()
        every { destination.enqueuePipeline(capture(transferred)) } just runs
        val interceptor =
            IdentifyInterceptor(
                storage,
                amplitude,
                amplitude.logger,
                amplitude.configuration,
                destination,
            )

        runTest(amplitude.testDispatcher) {
            storage.writeEvent(identifyEvent("first-id", mapOf("key1" to "value1")))
            storage.blockNextRead()
            val firstTransfer = launch { interceptor.transferInterceptedIdentify() }
            storage.readStarted.await()
            val secondIdentify =
                async {
                    interceptor.intercept(identifyEvent("second-id", mapOf("key2" to "value2")))
                }
            runCurrent()

            assertFalse(secondIdentify.isCompleted)
            storage.continueRead.complete(Unit)
            firstTransfer.join()
            assertNull(secondIdentify.await())
            interceptor.transferInterceptedIdentify()

            assertEquals(2, transferred.size)
            assertEquals("first-id", transferred[0].insertId)
            assertEquals(
                mapOf("key1" to "value1"),
                transferred[0].userProperties?.get(IdentifyOperation.SET.operationType),
            )
            assertEquals("second-id", transferred[1].insertId)
            assertEquals(
                mapOf("key2" to "value2"),
                transferred[1].userProperties?.get(IdentifyOperation.SET.operationType),
            )
        }
    }

    private fun identifyEvent(
        insertId: String,
        properties: Map<String, Any?>,
        userId: String = "user-id",
    ): IdentifyEvent =
        IdentifyEvent().apply {
            this.userId = userId
            this.insertId = insertId
            userProperties =
                mutableMapOf(
                    IdentifyOperation.SET.operationType to properties.toMutableMap(),
                )
        }

    private class BlockingEventsStorage : Storage, EventsFileStorage {
        private val files = linkedMapOf<String, List<BaseEvent>>()
        private val currentEvents = mutableListOf<BaseEvent>()
        private var nextFileIndex = 0
        private var shouldBlockNextRead = false
        private var shouldFailNextRead = false
        val readStarted = CompletableDeferred<Unit>()
        val continueRead = CompletableDeferred<Unit>()

        fun blockNextRead() {
            shouldBlockNextRead = true
        }

        fun failNextRead() {
            shouldFailNextRead = true
        }

        override suspend fun writeEvent(event: BaseEvent) {
            currentEvents.add(event)
        }

        override suspend fun rollover() {
            if (currentEvents.isNotEmpty()) {
                files["file-${nextFileIndex++}"] = currentEvents.toList()
                currentEvents.clear()
            }
        }

        override fun readEventsContent(): List<Any> = files.keys.toList()

        @Suppress("PARAMETER_NAME_CHANGED_ON_OVERRIDE")
        override suspend fun getEventsString(filePath: Any): String {
            if (shouldBlockNextRead) {
                shouldBlockNextRead = false
                readStarted.complete(Unit)
                continueRead.await()
            }
            if (shouldFailNextRead) {
                shouldFailNextRead = false
                error("read failed")
            }
            return JSONUtil.eventsToString(files[filePath as String].orEmpty())
        }

        override fun removeFile(filePath: String): Boolean = files.remove(filePath) != null

        override fun releaseFile(filePath: String) = Unit

        override fun getEventCallback(insertId: String): EventCallBack? = null

        override fun removeEventCallback(insertId: String) = Unit

        override fun splitEventFile(
            filePath: String,
            events: JSONArray,
        ) = Unit

        override suspend fun write(
            key: Storage.Constants,
            value: String,
        ) = Unit

        override suspend fun remove(key: Storage.Constants) = Unit

        override fun read(key: Storage.Constants): String? = null

        override fun getResponseHandler(
            eventPipeline: EventPipeline,
            configuration: Configuration,
            scope: CoroutineScope,
            storageDispatcher: CoroutineDispatcher,
        ): ResponseHandler = error("Not used")
    }
}
