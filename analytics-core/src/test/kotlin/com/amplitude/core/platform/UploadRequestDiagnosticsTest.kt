package com.amplitude.core.platform

import com.amplitude.core.Amplitude
import com.amplitude.core.Configuration
import com.amplitude.core.RestrictedAmplitudeFeature
import com.amplitude.core.Storage
import com.amplitude.core.UploadRequestStateStorage
import com.amplitude.core.diagnostics.DiagnosticsClient
import com.amplitude.core.utilities.Diagnostics
import com.amplitude.core.utilities.http.HttpClientInterface
import com.amplitude.core.utilities.http.SuccessResponse
import com.amplitude.core.utilities.http.TimeoutResponse
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@OptIn(RestrictedAmplitudeFeature::class, ExperimentalCoroutinesApi::class)
class UploadRequestDiagnosticsTest {
    private class StateStorage(private val delegate: Storage) : Storage by delegate, UploadRequestStateStorage {
        override var uploadRequestPending = false
    }

    @Test
    fun `responses and request errors clear pending state but cancellation preserves it`() =
        runTest {
            for (outcome in listOf("success", "timeout", "exception", "cancelled")) {
                val delegate = mockk<Storage>(relaxed = true)
                every { delegate.readEventsContent() } returns listOf("batch")
                io.mockk.coEvery { delegate.getEventsString(any()) } returns "[]"
                val storage = StateStorage(delegate)
                val http = mockk<HttpClientInterface>()
                every { http.upload(any(), any()) } answers {
                    assertTrue(storage.uploadRequestPending)
                    when (outcome) {
                        "success" -> SuccessResponse()
                        "timeout" -> TimeoutResponse()
                        "exception" -> throw IllegalStateException("request failed")
                        else -> throw CancellationException("upload cancelled")
                    }
                }
                val amplitude = mockk<Amplitude>(relaxed = true)
                every { amplitude.configuration } returns Configuration(apiKey = "test")
                every { amplitude.diagnostics } returns Diagnostics()
                every { amplitude.networkIODispatcher } returns StandardTestDispatcher(testScheduler)
                every { amplitude.storageIODispatcher } returns StandardTestDispatcher(testScheduler)
                val pipeline =
                    EventPipeline(
                        amplitude,
                        httpClient = http,
                        storage = storage,
                        scope = backgroundScope,
                        overrideResponseHandler = mockk(relaxed = true),
                    )
                pipeline.start()
                pipeline.flush()
                runCurrent()
                if (outcome == "cancelled") assertTrue(storage.uploadRequestPending) else assertFalse(storage.uploadRequestPending)
                pipeline.stop()
            }
        }

    @Test
    fun `recovery runs once and respects disabled diagnostics`() =
        runTest {
            for (enabled in listOf(true, false)) {
                val delegate = mockk<Storage>(relaxed = true)
                every { delegate.readEventsContent() } returns emptyList()
                val storage = StateStorage(delegate).apply { uploadRequestPending = true }
                val client = mockk<DiagnosticsClient>(relaxed = true)
                val amplitude = mockk<Amplitude>(relaxed = true)
                every { amplitude.configuration } returns Configuration(apiKey = "test", enableDiagnostics = enabled)
                every { amplitude.diagnosticsClient } returns client
                every { amplitude.networkIODispatcher } returns StandardTestDispatcher(testScheduler)
                every { amplitude.storageIODispatcher } returns StandardTestDispatcher(testScheduler)
                val pipeline = EventPipeline(amplitude, httpClient = mockk(), storage = storage, scope = backgroundScope)
                pipeline.start()
                pipeline.flush()
                runCurrent()
                if (enabled) assertFalse(storage.uploadRequestPending) else assertTrue(storage.uploadRequestPending)
                storage.uploadRequestPending = true
                pipeline.flush()
                runCurrent()
                assertTrue(storage.uploadRequestPending)
                verify(exactly = if (enabled) 1 else 0) { client.increment("analytics.upload.missed_network_callback", 1) }
                pipeline.stop()
            }
        }
}
