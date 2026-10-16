package com.amplitude.core.platform

import com.amplitude.core.Amplitude
import com.amplitude.core.Configuration
import com.amplitude.core.RestrictedAmplitudeFeature
import com.amplitude.core.Storage
import com.amplitude.core.diagnostics.DiagnosticsClient
import com.amplitude.core.utilities.EventsFileStorage
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

@OptIn(RestrictedAmplitudeFeature::class, ExperimentalCoroutinesApi::class)
class BacklogDiagnosticsTest {
    @TempDir lateinit var directory: File

    private interface FileBackedStorage : Storage, EventsFileStorage

    @Test
    fun `should measure the pending snapshot including missing files and zero backlog`() =
        runTest {
            val batch = File(directory, "batch").apply { writeText("世界") }
            val missing = File(directory, "deleted")
            val storage = mockk<FileBackedStorage>(relaxed = true)
            every { storage.readEventsContent() } returnsMany listOf(listOf(batch.path, missing.path), emptyList())
            val client = mockk<DiagnosticsClient>(relaxed = true)
            val amplitude = mockk<Amplitude>(relaxed = true)
            every { amplitude.configuration } returns Configuration(apiKey = "test")
            every { amplitude.diagnosticsClient } returns client
            every { amplitude.networkIODispatcher } returns StandardTestDispatcher(testScheduler)
            every { amplitude.storageIODispatcher } returns StandardTestDispatcher(testScheduler)
            val pipeline = EventPipeline(amplitude, storage = storage, scope = backgroundScope, httpClient = mockk())
            pipeline.start()
            pipeline.flush()
            runCurrent()
            verify { client.recordHistogram("analytics.storage.backlog.file_count", 2.0) }
            verify { client.recordHistogram("analytics.storage.backlog.bytes", 6.0) }
            pipeline.flush()
            runCurrent()
            verify { client.recordHistogram("analytics.storage.backlog.file_count", 0.0) }
            verify { client.recordHistogram("analytics.storage.backlog.bytes", 0.0) }
            verify(exactly = 2) { storage.readEventsContent() }
            pipeline.stop()
        }

    @Test
    fun `should omit backlog metrics for disabled diagnostics and non file storage`() =
        runTest {
            for (fileBacked in listOf(true, false)) {
                val storage = if (fileBacked) mockk<FileBackedStorage>(relaxed = true) else mockk<Storage>(relaxed = true)
                every { storage.readEventsContent() } returns emptyList()
                val client = mockk<DiagnosticsClient>(relaxed = true)
                val amplitude = mockk<Amplitude>(relaxed = true)
                every { amplitude.configuration } returns Configuration(apiKey = "test", enableDiagnostics = !fileBacked)
                every { amplitude.diagnosticsClient } returns client
                every { amplitude.networkIODispatcher } returns StandardTestDispatcher(testScheduler)
                every { amplitude.storageIODispatcher } returns StandardTestDispatcher(testScheduler)
                val pipeline = EventPipeline(amplitude, storage = storage, scope = backgroundScope, httpClient = mockk())
                pipeline.start()
                pipeline.flush()
                runCurrent()
                verify(exactly = 0) { client.recordHistogram(any(), any()) }
                pipeline.stop()
            }
        }
}
