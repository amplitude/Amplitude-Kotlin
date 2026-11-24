package com.amplitude.android.streaming.internal

import com.amplitude.android.Amplitude
import com.amplitude.android.streaming.internal.network.UploadPipeline
import com.amplitude.android.streaming.internal.network.uploadPipeline
import com.amplitude.android.streaming.internal.player.PlayerBindingFactory
import com.amplitude.android.streaming.internal.player.playerBindingFactory
import com.amplitude.android.streaming.internal.storage.StoragePipeline
import com.amplitude.android.streaming.internal.storage.storagePipeline
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

@OptIn(ExperimentalCoroutinesApi::class)
class StreamingAnalyticsLifecycleTest {
    @ParameterizedTest
    @ValueSource(strings = ["none", "storage", "upload"])
    fun `teardown closes intake and bounds the ordered final drain`(stalledAt: String) = runTest {
        mockkStatic(
            "com.amplitude.android.streaming.internal.storage.StoragePipelineKt",
            "com.amplitude.android.streaming.internal.network.UploadPipelineKt",
            "com.amplitude.android.streaming.internal.player.PlayerBindingFactoryKt",
            "com.amplitude.android.streaming.internal.StreamTrackerKt",
            "com.amplitude.android.streaming.internal.DelayedEventBatcherKt",
        )
        try {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val scope = CoroutineScope(SupervisorJob(backgroundScope.coroutineContext[Job]) + dispatcher)
            val graph = mockk<StreamingDiGraph>()
            val amplitude = mockk<Amplitude>(relaxed = true)
            val storage = mockk<StoragePipeline>()
            val uploader = mockk<UploadPipeline>(relaxed = true)
            val bindings = mockk<PlayerBindingFactory>()
            val tracker = mockk<StreamTracker>()
            every { graph.scope } returns scope
            every { graph.amplitude } returns amplitude
            every { graph.ioDispatcher } returns dispatcher
            every { graph.logger } returns mockk(relaxed = true)
            every { amplitude.amplitudeScope } returns backgroundScope
            every { graph.storagePipeline } returns storage
            every { graph.uploadPipeline } returns uploader
            every { graph.playerBindingFactory } returns bindings
            every { graph.streamTracker } returns tracker
            every { graph.delayedEventBatcher } returns DelayedEventBatcher()
            var sink: ((DelayedEvent) -> Unit)? = null
            every { tracker.routeDelayedEvents(any()) } answers { sink = firstArg() }
            val heartbeat = event(DelayedEvent.Kind.DELAYED)
            val stop = event(DelayedEvent.Kind.INSTANT)
            val write = CompletableDeferred<Unit>()
            val upload = CompletableDeferred<Unit>()
            coEvery { uploader.flushAndDrain() } coAnswers {
                if (stalledAt == "upload") upload.await()
            }
            val persisted = mutableListOf<DelayedEvent.Kind>()
            coEvery { storage.onDelayedEvents(any()) } coAnswers {
                val events = firstArg<List<DelayedEvent>>()
                if (events.single().kind == DelayedEvent.Kind.DELAYED) write.await()
                persisted.add(events.single().kind)
            }
            coEvery { bindings.detachAll() } answers { sink!!(stop) }
            val streaming = StreamingAnalytics(graph)
            runCurrent()
            streaming.onDelayedEvent(heartbeat)
            streaming.teardown()
            streaming.onDelayedEvent(event(DelayedEvent.Kind.INSTANT))
            runCurrent()
            assertEquals(emptyList<DelayedEvent.Kind>(), persisted)
            coVerify(exactly = 0) { uploader.flushAndDrain() }
            if (stalledAt != "storage") {
                write.complete(Unit)
                runCurrent()
                assertEquals(listOf(DelayedEvent.Kind.DELAYED, DelayedEvent.Kind.INSTANT), persisted)
                coVerify(exactly = 2) { storage.onDelayedEvents(any()) }
                coVerify(exactly = 1) { uploader.flushAndDrain() }
            }
            if (stalledAt != "none") {
                advanceTimeBy(9_999L)
                runCurrent()
                assertEquals(true, scope.coroutineContext[Job]!!.isActive)
                advanceTimeBy(1L)
                runCurrent()
            }
            assertEquals(true, scope.coroutineContext[Job]!!.isCompleted)
        } finally {
            unmockkStatic(
                "com.amplitude.android.streaming.internal.storage.StoragePipelineKt",
                "com.amplitude.android.streaming.internal.network.UploadPipelineKt",
                "com.amplitude.android.streaming.internal.player.PlayerBindingFactoryKt",
                "com.amplitude.android.streaming.internal.StreamTrackerKt",
                "com.amplitude.android.streaming.internal.DelayedEventBatcherKt",
            )
        }
    }

    private fun event(kind: DelayedEvent.Kind) = DelayedEvent(
        eventType = StreamEvents.STREAM_STOPPED,
        kind = kind,
        timestamp = 1L,
        eventProperties = mutableMapOf("[Streaming] Stream Session ID" to "stream-1"),
    )
}
