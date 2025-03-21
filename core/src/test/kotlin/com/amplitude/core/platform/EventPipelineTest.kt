package com.amplitude.core.platform

import com.amplitude.core.Amplitude
import com.amplitude.core.Configuration
import com.amplitude.core.State
import com.amplitude.core.events.BaseEvent
import com.amplitude.core.utilities.ConsoleLoggerProvider
import com.amplitude.core.utilities.ExponentialBackoffRetryHandler
import com.amplitude.core.utilities.InMemoryStorageProvider
import com.amplitude.core.utilities.http.BadRequestResponse
import com.amplitude.core.utilities.http.FailedResponse
import com.amplitude.core.utilities.http.HttpStatus
import com.amplitude.core.utilities.http.PayloadTooLargeResponse
import com.amplitude.core.utilities.http.ResponseHandler
import com.amplitude.core.utilities.http.SuccessResponse
import com.amplitude.core.utilities.http.TimeoutResponse
import com.amplitude.core.utilities.http.TooManyRequestsResponse
import com.amplitude.id.IMIdentityStorageProvider
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.Channel.Factory.UNLIMITED
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@ExperimentalCoroutinesApi
class EventPipelineTest {
    private val config = Configuration(
        apiKey = "API_KEY",
        flushIntervalMillis = 1,
        storageProvider = InMemoryStorageProvider(),
        loggerProvider = ConsoleLoggerProvider(),
        identifyInterceptStorageProvider = InMemoryStorageProvider(),
        identityStorageProvider = IMIdentityStorageProvider(),
    )
    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)
    private val amplitude: Amplitude = Amplitude(
        configuration = config,
        store = State(),
        amplitudeScope = testScope,
        amplitudeDispatcher = testDispatcher,
        networkIODispatcher = testDispatcher,
        storageIODispatcher = testDispatcher,
        retryDispatcher = testDispatcher
    )
    private val fakeResponseHandler: ResponseHandler = mockk(relaxed= true)

    @Test
    fun `should not flush when put and offline`() = runTest(testDispatcher) {
        amplitude.isBuilt.await()
        amplitude.configuration.offline = true
        val eventPipeline = spyk(EventPipeline(amplitude))
        val event = BaseEvent().apply { eventType = "test_event" }

        eventPipeline.start()
        eventPipeline.put(event)
        advanceUntilIdle()

        verify(exactly = 0) { eventPipeline.flush() }
    }

    @Test
    fun `should flush when put and online`() = runTest(testDispatcher) {
        amplitude.isBuilt.await()
        amplitude.configuration.offline = false
        val eventPipeline = spyk(EventPipeline(amplitude))
        val event = BaseEvent().apply { eventType = "test_event" }

        eventPipeline.start()
        eventPipeline.put(event)
        advanceUntilIdle()

        verify(exactly = 1) { eventPipeline.flush() }
    }

    @Test
    fun `should flush when put and offline is disabled`() = runTest(testDispatcher) {
        amplitude.isBuilt.await()
        amplitude.configuration.offline = null
        val eventPipeline = spyk(EventPipeline(amplitude))
        val event = BaseEvent().apply { eventType = "test_event" }

        eventPipeline.start()
        eventPipeline.put(event)
        advanceUntilIdle()

        verify(exactly = 1) { eventPipeline.flush() }
    }

    @Test
    fun `should reset retry handler on successful upload`() = runTest(testDispatcher) {
        amplitude.isBuilt.await()
        val retryUploadHandler = spyk(ExponentialBackoffRetryHandler())
        val eventPipeline = EventPipeline(
            amplitude,
            retryUploadHandler = retryUploadHandler,
            overrideResponseHandler = fakeResponseHandler
        )
        every { fakeResponseHandler.handle(any(), any(), any()) } returns true
        val event = BaseEvent().apply { eventType = "test_event" }

        eventPipeline.start()
        eventPipeline.put(event)
        advanceUntilIdle()

        verify { retryUploadHandler.reset() }
    }

    @Test
    fun `should retry on failure`() = runTest(testDispatcher) {
        amplitude.isBuilt.await()
        val retryUploadHandler = spyk(ExponentialBackoffRetryHandler())
        val eventPipeline = EventPipeline(
            amplitude,
            retryUploadHandler = retryUploadHandler,
            overrideResponseHandler = fakeResponseHandler
        )
        val event = BaseEvent().apply { eventType = "test_event" }

        eventPipeline.start()
        eventPipeline.put(event)
        advanceUntilIdle()

        coVerify { retryUploadHandler.retryWithDelay(any<suspend () -> Unit>()) }
    }

    @Test
    fun `should reset after max retry attempts`() = runTest(testDispatcher) {
        amplitude.isBuilt.await()
        val retryUploadHandler = spyk(
            ExponentialBackoffRetryHandler(
                maxRetryAttempt = 0
            )
        )
        val uploadChannel = Channel<String>(UNLIMITED)
        uploadChannel.trySend("test1")
        uploadChannel.trySend("test2")
        val eventPipeline = EventPipeline(
            amplitude,
            retryUploadHandler = retryUploadHandler,
            overrideResponseHandler = fakeResponseHandler,
            uploadChannel = uploadChannel
        )
        val event = BaseEvent().apply { eventType = "test_event" }

        eventPipeline.start()
        eventPipeline.put(event)
        advanceUntilIdle()

        verify { retryUploadHandler.reset() }
        assertTrue(uploadChannel.isClosedForSend)
        assertTrue(uploadChannel.isClosedForReceive)
    }
}
