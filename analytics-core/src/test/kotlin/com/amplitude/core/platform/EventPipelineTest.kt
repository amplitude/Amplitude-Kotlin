package com.amplitude.core.platform

import com.amplitude.core.Amplitude
import com.amplitude.core.Configuration
import com.amplitude.core.State
import com.amplitude.core.events.BaseEvent
import com.amplitude.core.utilities.ConsoleLoggerProvider
import com.amplitude.core.utilities.ExponentialBackoffRetryHandler
import com.amplitude.core.utilities.InMemoryStorageProvider
import com.amplitude.core.utilities.http.AnalyticsResponse
import com.amplitude.core.utilities.http.BadRequestResponse
import com.amplitude.core.utilities.http.HttpClientInterface
import com.amplitude.core.utilities.http.HttpStatus.BAD_REQUEST
import com.amplitude.core.utilities.http.HttpStatus.FAILED
import com.amplitude.core.utilities.http.HttpStatus.PAYLOAD_TOO_LARGE
import com.amplitude.core.utilities.http.HttpStatus.SUCCESS
import com.amplitude.core.utilities.http.HttpStatus.TIMEOUT
import com.amplitude.core.utilities.http.HttpStatus.TOO_MANY_REQUESTS
import com.amplitude.core.utilities.http.PayloadTooLargeResponse
import com.amplitude.core.utilities.http.ResponseHandler
import com.amplitude.core.utilities.http.SuccessResponse
import com.amplitude.core.utilities.http.TimeoutResponse
import com.amplitude.core.utils.FakeAmplitude
import com.amplitude.id.IMIdentityStorageProvider
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.jupiter.api.Test

@ExperimentalCoroutinesApi
class EventPipelineTest {
    private lateinit var fakeResponse: AnalyticsResponse
    private val config =
        Configuration(
            apiKey = "API_KEY",
            flushIntervalMillis = 1,
            storageProvider = InMemoryStorageProvider(),
            loggerProvider = ConsoleLoggerProvider(),
            identifyInterceptStorageProvider = InMemoryStorageProvider(),
            identityStorageProvider = IMIdentityStorageProvider(),
            httpClient =
                object : HttpClientInterface {
                    override fun upload(
                        events: String,
                        diagnostics: String?,
                    ): AnalyticsResponse = fakeResponse
                },
        )
    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)
    private val amplitude: Amplitude =
        FakeAmplitude(
            configuration = config,
            store = State(),
            amplitudeScope = testScope,
            amplitudeDispatcher = testDispatcher,
            networkIODispatcher = testDispatcher,
            storageIODispatcher = testDispatcher,
        )
    private val fakeResponseHandler: ResponseHandler =
        mockk(relaxed = true) {
            every { handle(any(), any(), any()) } answers {
                val response = it.invocation.args[0] as AnalyticsResponse
                val shouldRetryUploadOnFailure =
                    when (response.status) {
                        SUCCESS -> null
                        BAD_REQUEST -> false
                        TIMEOUT,
                        PAYLOAD_TOO_LARGE,
                        TOO_MANY_REQUESTS,
                        FAILED,
                        -> true
                    }
                shouldRetryUploadOnFailure
            }
        }

    @Test
    fun `should not flush when put and offline`() =
        runTest(testDispatcher) {
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
    fun `should flush when put and online`() =
        runTest(testDispatcher) {
            amplitude.isBuilt.await()
            amplitude.configuration.offline = false
            val eventPipeline = spyk(EventPipeline(amplitude))
            val event = BaseEvent().apply { eventType = "test_event" }

            fakeResponse = SuccessResponse()

            eventPipeline.start()
            eventPipeline.put(event)

            advanceUntilIdle()

            verify(exactly = 1) { eventPipeline.flush() }
        }

    @Test
    fun `should flush when put and offline is disabled`() =
        runTest(testDispatcher) {
            amplitude.isBuilt.await()
            amplitude.configuration.offline = null
            val eventPipeline = spyk(EventPipeline(amplitude))
            val event = BaseEvent().apply { eventType = "test_event" }

            fakeResponse = SuccessResponse()

            eventPipeline.start()
            eventPipeline.put(event)
            advanceUntilIdle()

            verify(exactly = 1) { eventPipeline.flush() }
        }

    @Test
    fun `should reset retry handler on successful upload`() =
        runTest(testDispatcher) {
            amplitude.isBuilt.await()
            val retryUploadHandler = spyk(ExponentialBackoffRetryHandler())
            val eventPipeline =
                EventPipeline(
                    amplitude,
                    retryUploadHandler = retryUploadHandler,
                    overrideResponseHandler = fakeResponseHandler,
                )
            fakeResponse = SuccessResponse()
            val event = BaseEvent().apply { eventType = "test_event" }

            eventPipeline.start()
            eventPipeline.put(event)
            advanceUntilIdle()

            coVerify(exactly = 0) { retryUploadHandler.attemptRetry(any<(Boolean) -> Unit>()) }
            verify { retryUploadHandler.reset() }
            verify { fakeResponseHandler.handle(fakeResponse, any(), any()) }
        }

    @Test
    fun `should NOT retry on non-retryable error - bad request`() =
        runTest(testDispatcher) {
            amplitude.isBuilt.await()
            val retryUploadHandler = spyk(ExponentialBackoffRetryHandler())
            val eventPipeline =
                EventPipeline(
                    amplitude,
                    retryUploadHandler = retryUploadHandler,
                    overrideResponseHandler = fakeResponseHandler,
                )
            val event = BaseEvent().apply { eventType = "test_event" }

            fakeResponse = BadRequestResponse(JSONObject())
            eventPipeline.start()
            eventPipeline.put(event)
            advanceUntilIdle()

            coVerify(exactly = 0) { retryUploadHandler.attemptRetry(any<(Boolean) -> Unit>()) }
            verify { retryUploadHandler.reset() }
            verify { fakeResponseHandler.handle(fakeResponse, any(), any()) }
        }

    @Test
    fun `should retry on retryable reason - timeout`() =
        runTest(testDispatcher) {
            amplitude.isBuilt.await()
            val retryUploadHandler = spyk(ExponentialBackoffRetryHandler())
            val eventPipeline =
                EventPipeline(
                    amplitude,
                    retryUploadHandler = retryUploadHandler,
                    overrideResponseHandler = fakeResponseHandler,
                )
            val event = BaseEvent().apply { eventType = "test_event" }

            fakeResponse = TimeoutResponse()
            eventPipeline.start()
            eventPipeline.put(event)
            advanceUntilIdle()

            coVerify { retryUploadHandler.attemptRetry(any<(Boolean) -> Unit>()) }
            verify { fakeResponseHandler.handle(fakeResponse, any(), any()) }
            verify(exactly = 0) { retryUploadHandler.reset() }
        }

    @Test
    fun `should retry on retryable reason - too large`() =
        runTest(testDispatcher) {
            amplitude.isBuilt.await()
            val retryUploadHandler = spyk(ExponentialBackoffRetryHandler())
            val eventPipeline =
                EventPipeline(
                    amplitude,
                    retryUploadHandler = retryUploadHandler,
                    overrideResponseHandler = fakeResponseHandler,
                )
            val event = BaseEvent().apply { eventType = "test_event" }

            fakeResponse = PayloadTooLargeResponse(JSONObject())
            eventPipeline.start()
            eventPipeline.put(event)
            advanceUntilIdle()

            coVerify { retryUploadHandler.attemptRetry(any<(Boolean) -> Unit>()) }
            verify { fakeResponseHandler.handle(fakeResponse, any(), any()) }
            verify(exactly = 0) { retryUploadHandler.reset() }
        }

    @Test
    fun `should send MAX_RETRY_ATTEMPT_SIG after max retry attempts`() =
        runTest(testDispatcher) {
            amplitude.isBuilt.await()
            val retryUploadHandler =
                spyk(
                    ExponentialBackoffRetryHandler(
                        maxRetryAttempt = 0,
                    ),
                )
            val eventPipeline =
                EventPipeline(
                    amplitude,
                    retryUploadHandler = retryUploadHandler,
                    overrideResponseHandler = fakeResponseHandler,
                )

            fakeResponse = PayloadTooLargeResponse(JSONObject())
            val event = BaseEvent().apply { eventType = "test_event" }
            eventPipeline.start()
            eventPipeline.put(event)
            advanceUntilIdle()

            coVerify { retryUploadHandler.attemptRetry(any<(Boolean) -> Unit>()) }
            // this will be called on the MAX_RETRY_ATTEMPT_SIG block on upload(),
            // this is because the InMemoryStorage will clear the buffer and the second call to
            // readEventsContent() will return an empty list and will stop the processing
            verify(exactly = 1) { retryUploadHandler.reset() }
        }
}
