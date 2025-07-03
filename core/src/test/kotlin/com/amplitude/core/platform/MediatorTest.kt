package com.amplitude.core.platform

import com.amplitude.core.Amplitude
import com.amplitude.core.events.BaseEvent
import com.amplitude.core.events.GroupIdentifyEvent
import com.amplitude.core.events.IdentifyEvent
import com.amplitude.core.events.RevenueEvent
import io.mockk.mockk
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.newSingleThreadContext
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicInteger

private const val UNIT_OF_WORK_IN_MS = 100L

@OptIn(ExperimentalCoroutinesApi::class, DelicateCoroutinesApi::class)
class MediatorTest {
    private lateinit var mediator: Mediator
    private lateinit var mockAmplitude: Amplitude

    @BeforeEach
    fun setUp() {
        mediator = Mediator()
        mockAmplitude = mockk<Amplitude>()
    }

    /**
     * Fake [DestinationPlugin] that does work on [flush] for 1 second
     */
    private class FakeDestinationPlugin : DestinationPlugin() {
        var amountOfWorkDone = AtomicInteger()
        var processCallCount = AtomicInteger(0)
        var shouldThrowException = false

        override fun flush() {
            super.flush()
            Thread.sleep(UNIT_OF_WORK_IN_MS)
            amountOfWorkDone.incrementAndGet()
        }

        override fun process(event: BaseEvent): BaseEvent {
            processCallCount.incrementAndGet()
            if (shouldThrowException) {
                throw RuntimeException("Test exception")
            }
            return event
        }
    }

    /**
     * Fake [EventPlugin] for testing
     */
    private class FakeEventPlugin : EventPlugin {
        override val type: Plugin.Type = Plugin.Type.Before
        override lateinit var amplitude: Amplitude

        var trackCallCount = AtomicInteger(0)
        var identifyCallCount = AtomicInteger(0)
        var groupIdentifyCallCount = AtomicInteger(0)
        var revenueCallCount = AtomicInteger(0)
        var shouldReturnNull = false
        var shouldThrowException = false

        override fun track(payload: BaseEvent): BaseEvent? {
            trackCallCount.incrementAndGet()
            if (shouldThrowException) {
                throw RuntimeException("Test exception")
            }
            return if (shouldReturnNull) null else payload
        }

        override fun identify(payload: IdentifyEvent): IdentifyEvent? {
            identifyCallCount.incrementAndGet()
            if (shouldThrowException) {
                throw RuntimeException("Test exception")
            }
            return if (shouldReturnNull) null else payload
        }

        override fun groupIdentify(payload: GroupIdentifyEvent): GroupIdentifyEvent? {
            groupIdentifyCallCount.incrementAndGet()
            if (shouldThrowException) {
                throw RuntimeException("Test exception")
            }
            return if (shouldReturnNull) null else payload
        }

        override fun revenue(payload: RevenueEvent): RevenueEvent? {
            revenueCallCount.incrementAndGet()
            if (shouldThrowException) {
                throw RuntimeException("Test exception")
            }
            return if (shouldReturnNull) null else payload
        }
    }

    /**
     * Fake [Plugin] for testing
     */
    private class FakePlugin : Plugin {
        override val type: Plugin.Type = Plugin.Type.Before
        override lateinit var amplitude: Amplitude

        var executeCallCount = AtomicInteger(0)
        var shouldReturnNull = false
        var shouldThrowException = false

        override fun execute(event: BaseEvent): BaseEvent? {
            executeCallCount.incrementAndGet()
            if (shouldThrowException) {
                throw RuntimeException("Test exception")
            }
            return if (shouldReturnNull) null else event
        }
    }

    // ========== EXECUTE METHOD TESTS ==========

    @Test
    fun `execute with no plugins returns original event`() {
        val event = BaseEvent()
        event.eventType = "test_event"

        val result = mediator.execute(event)

        assertSame(event, result)
    }

    @Test
    fun `execute with DestinationPlugin calls process method`() {
        val destinationPlugin = FakeDestinationPlugin()
        destinationPlugin.amplitude = mockAmplitude
        mediator.add(destinationPlugin)

        val event = BaseEvent()
        event.eventType = "test_event"

        val result = mediator.execute(event)

        assertEquals(1, destinationPlugin.processCallCount.get())
        assertSame(event, result)
    }

    @Test
    fun `execute with DestinationPlugin that throws exception continues processing`() {
        val destinationPlugin = FakeDestinationPlugin()
        destinationPlugin.amplitude = mockAmplitude
        destinationPlugin.shouldThrowException = true
        mediator.add(destinationPlugin)

        val event = BaseEvent()
        event.eventType = "test_event"

        val result = mediator.execute(event)

        assertEquals(1, destinationPlugin.processCallCount.get())
        assertSame(event, result)
    }

    @Test
    fun `execute with EventPlugin and BaseEvent calls track method`() {
        val eventPlugin = FakeEventPlugin()
        eventPlugin.amplitude = mockAmplitude
        mediator.add(eventPlugin)

        val event = BaseEvent()
        event.eventType = "test_event"

        val result = mediator.execute(event)

        assertEquals(1, eventPlugin.trackCallCount.get())
        assertEquals(0, eventPlugin.identifyCallCount.get())
        assertEquals(0, eventPlugin.groupIdentifyCallCount.get())
        assertEquals(0, eventPlugin.revenueCallCount.get())
        assertSame(event, result)
    }

    @Test
    fun `execute with EventPlugin and IdentifyEvent calls identify method`() {
        val eventPlugin = FakeEventPlugin()
        eventPlugin.amplitude = mockAmplitude
        mediator.add(eventPlugin)

        val event = IdentifyEvent()
        event.eventType = "test_identify"

        val result = mediator.execute(event)

        assertEquals(0, eventPlugin.trackCallCount.get())
        assertEquals(1, eventPlugin.identifyCallCount.get())
        assertEquals(0, eventPlugin.groupIdentifyCallCount.get())
        assertEquals(0, eventPlugin.revenueCallCount.get())
        assertSame(event, result)
    }

    @Test
    fun `execute with EventPlugin and GroupIdentifyEvent calls groupIdentify method`() {
        val eventPlugin = FakeEventPlugin()
        eventPlugin.amplitude = mockAmplitude
        mediator.add(eventPlugin)

        val event = GroupIdentifyEvent()
        event.eventType = "test_group_identify"

        val result = mediator.execute(event)

        assertEquals(0, eventPlugin.trackCallCount.get())
        assertEquals(0, eventPlugin.identifyCallCount.get())
        assertEquals(1, eventPlugin.groupIdentifyCallCount.get())
        assertEquals(0, eventPlugin.revenueCallCount.get())
        assertSame(event, result)
    }

    @Test
    fun `execute with EventPlugin and RevenueEvent calls revenue method`() {
        val eventPlugin = FakeEventPlugin()
        eventPlugin.amplitude = mockAmplitude
        mediator.add(eventPlugin)

        val event = RevenueEvent()
        event.eventType = "test_revenue"

        val result = mediator.execute(event)

        assertEquals(0, eventPlugin.trackCallCount.get())
        assertEquals(0, eventPlugin.identifyCallCount.get())
        assertEquals(0, eventPlugin.groupIdentifyCallCount.get())
        assertEquals(1, eventPlugin.revenueCallCount.get())
        assertSame(event, result)
    }

    @Test
    fun `execute with regular Plugin calls execute method`() {
        val plugin = FakePlugin()
        plugin.amplitude = mockAmplitude
        mediator.add(plugin)

        val event = BaseEvent()
        event.eventType = "test_event"

        val result = mediator.execute(event)

        assertEquals(1, plugin.executeCallCount.get())
        assertSame(event, result)
    }

    @Test
    fun `execute with EventPlugin that returns null stops processing`() {
        val eventPlugin1 = FakeEventPlugin()
        eventPlugin1.amplitude = mockAmplitude
        eventPlugin1.shouldReturnNull = true
        mediator.add(eventPlugin1)

        val eventPlugin2 = FakeEventPlugin()
        eventPlugin2.amplitude = mockAmplitude
        mediator.add(eventPlugin2)

        val event = BaseEvent()
        event.eventType = "test_event"

        val result = mediator.execute(event)

        assertEquals(1, eventPlugin1.trackCallCount.get())
        assertEquals(0, eventPlugin2.trackCallCount.get())
        assertNull(result)
    }

    @Test
    fun `execute with Plugin that returns null stops processing`() {
        val plugin1 = FakePlugin()
        plugin1.amplitude = mockAmplitude
        plugin1.shouldReturnNull = true
        mediator.add(plugin1)

        val plugin2 = FakePlugin()
        plugin2.amplitude = mockAmplitude
        mediator.add(plugin2)

        val event = BaseEvent()
        event.eventType = "test_event"

        val result = mediator.execute(event)

        assertEquals(1, plugin1.executeCallCount.get())
        assertEquals(0, plugin2.executeCallCount.get())
        assertNull(result)
    }

    @Test
    fun `execute with EventPlugin that throws exception continues processing`() {
        val eventPlugin1 = FakeEventPlugin()
        eventPlugin1.amplitude = mockAmplitude
        eventPlugin1.shouldThrowException = true
        mediator.add(eventPlugin1)

        val eventPlugin2 = FakeEventPlugin()
        eventPlugin2.amplitude = mockAmplitude
        mediator.add(eventPlugin2)

        val event = BaseEvent()
        event.eventType = "test_event"

        val result = mediator.execute(event)

        assertEquals(1, eventPlugin1.trackCallCount.get())
        assertEquals(1, eventPlugin2.trackCallCount.get())
        assertSame(event, result)
    }

    @Test
    fun `execute with Plugin that throws exception continues processing`() {
        val plugin1 = FakePlugin()
        plugin1.amplitude = mockAmplitude
        plugin1.shouldThrowException = true
        mediator.add(plugin1)

        val plugin2 = FakePlugin()
        plugin2.amplitude = mockAmplitude
        mediator.add(plugin2)

        val event = BaseEvent()
        event.eventType = "test_event"

        val result = mediator.execute(event)

        assertEquals(1, plugin1.executeCallCount.get())
        assertEquals(1, plugin2.executeCallCount.get())
        assertSame(event, result)
    }

    @Test
    fun `execute with multiple plugins processes in order`() {
        val destinationPlugin = FakeDestinationPlugin()
        destinationPlugin.amplitude = mockAmplitude
        mediator.add(destinationPlugin)

        val eventPlugin = FakeEventPlugin()
        eventPlugin.amplitude = mockAmplitude
        mediator.add(eventPlugin)

        val plugin = FakePlugin()
        plugin.amplitude = mockAmplitude
        mediator.add(plugin)

        val event = BaseEvent()
        event.eventType = "test_event"

        val result = mediator.execute(event)

        assertEquals(1, destinationPlugin.processCallCount.get())
        assertEquals(1, eventPlugin.trackCallCount.get())
        assertEquals(1, plugin.executeCallCount.get())
        assertSame(event, result)
    }

    @Test
    fun `execute with null result from EventPlugin stops processing`() {
        val eventPlugin1 = FakeEventPlugin()
        eventPlugin1.amplitude = mockAmplitude
        eventPlugin1.shouldReturnNull = true
        mediator.add(eventPlugin1)

        val destinationPlugin = FakeDestinationPlugin()
        destinationPlugin.amplitude = mockAmplitude
        mediator.add(destinationPlugin)

        val event = BaseEvent()
        event.eventType = "test_event"

        val result = mediator.execute(event)

        assertEquals(1, eventPlugin1.trackCallCount.get())
        assertEquals(0, destinationPlugin.processCallCount.get())
        assertNull(result)
    }

    @Test
    fun `execute with null result from Plugin stops processing`() {
        val plugin1 = FakePlugin()
        plugin1.amplitude = mockAmplitude
        plugin1.shouldReturnNull = true
        mediator.add(plugin1)

        val destinationPlugin = FakeDestinationPlugin()
        destinationPlugin.amplitude = mockAmplitude
        mediator.add(destinationPlugin)

        val event = BaseEvent()
        event.eventType = "test_event"

        val result = mediator.execute(event)

        assertEquals(1, plugin1.executeCallCount.get())
        assertEquals(0, destinationPlugin.processCallCount.get())
        assertNull(result)
    }

    @Test
    fun `execute with mixed plugin types and different event types`() {
        val destinationPlugin = FakeDestinationPlugin()
        destinationPlugin.amplitude = mockAmplitude
        mediator.add(destinationPlugin)

        val eventPlugin = FakeEventPlugin()
        eventPlugin.amplitude = mockAmplitude
        mediator.add(eventPlugin)

        val plugin = FakePlugin()
        plugin.amplitude = mockAmplitude
        mediator.add(plugin)

        // Test with IdentifyEvent
        val identifyEvent = IdentifyEvent()
        identifyEvent.eventType = "test_identify"

        val identifyResult = mediator.execute(identifyEvent)

        assertEquals(1, destinationPlugin.processCallCount.get())
        assertEquals(0, eventPlugin.trackCallCount.get())
        assertEquals(1, eventPlugin.identifyCallCount.get())
        assertEquals(1, plugin.executeCallCount.get())
        assertSame(identifyEvent, identifyResult)

        // Reset counters
        destinationPlugin.processCallCount.set(0)
        eventPlugin.trackCallCount.set(0)
        eventPlugin.identifyCallCount.set(0)
        plugin.executeCallCount.set(0)

        // Test with GroupIdentifyEvent
        val groupIdentifyEvent = GroupIdentifyEvent()
        groupIdentifyEvent.eventType = "test_group_identify"

        val groupIdentifyResult = mediator.execute(groupIdentifyEvent)

        assertEquals(1, destinationPlugin.processCallCount.get())
        assertEquals(0, eventPlugin.trackCallCount.get())
        assertEquals(0, eventPlugin.identifyCallCount.get())
        assertEquals(1, eventPlugin.groupIdentifyCallCount.get())
        assertEquals(1, plugin.executeCallCount.get())
        assertSame(groupIdentifyEvent, groupIdentifyResult)

        // Reset counters
        destinationPlugin.processCallCount.set(0)
        eventPlugin.trackCallCount.set(0)
        eventPlugin.groupIdentifyCallCount.set(0)
        plugin.executeCallCount.set(0)

        // Test with RevenueEvent
        val revenueEvent = RevenueEvent()
        revenueEvent.eventType = "test_revenue"

        val revenueResult = mediator.execute(revenueEvent)

        assertEquals(1, destinationPlugin.processCallCount.get())
        assertEquals(0, eventPlugin.trackCallCount.get())
        assertEquals(0, eventPlugin.identifyCallCount.get())
        assertEquals(0, eventPlugin.groupIdentifyCallCount.get())
        assertEquals(1, eventPlugin.revenueCallCount.get())
        assertEquals(1, plugin.executeCallCount.get())
        assertSame(revenueEvent, revenueResult)
    }

    @Test
    fun `execute with empty plugin list returns original event`() {
        val event = BaseEvent()
        event.eventType = "test_event"

        val result = mediator.execute(event)

        assertSame(event, result)
    }

    @Test
    fun `execute with plugin that modifies event returns modified event`() {
        val modifyingPlugin =
            object : Plugin {
                override val type: Plugin.Type = Plugin.Type.Before
                override lateinit var amplitude: Amplitude

                override fun execute(event: BaseEvent): BaseEvent {
                    return event.setEventProperty("modified", true)
                }
            }
        modifyingPlugin.amplitude = mockAmplitude
        mediator.add(modifyingPlugin)

        val event = BaseEvent()
        event.eventType = "test_event"

        val result = mediator.execute(event)

        assertSame(event, result)
        assertEquals(true, event.eventProperties?.get("modified"))
    }

    @Test
    fun `does work twice on two destination plugins`() =
        runTest {
            val dispatcher1 = newSingleThreadContext("Thread-1")
            val dispatcher2 = newSingleThreadContext("Thread-2")
            try {
                val fakeDestinationPlugins = List(2) { FakeDestinationPlugin() }
                fakeDestinationPlugins.forEach {
                    mediator.add(it)
                }

                val work =
                    suspend {
                        println("Doing work on ${Thread.currentThread().name}")
                        println("Mediator plugins: ${mediator.plugins.size}")
                        mediator.applyClosure {
                            (it as EventPlugin).flush()
                        }
                    }

                val job1 = async(dispatcher1) { work() }
                val job2 = async(dispatcher2) { work() }

                job1.await()
                job2.await()

                fakeDestinationPlugins.forEach {
                    assertEquals(2, it.amountOfWorkDone.get())
                }
            } finally {
                dispatcher1.close()
                dispatcher2.close()
            }
        }

    @Test
    fun `work, add a new plugin and work, and work again on two destination plugins`() =
        runTest {
            val dispatcher1 = newSingleThreadContext("Thread-1")
            val dispatcher2 = newSingleThreadContext("Thread-2")
            val dispatcher3 = newSingleThreadContext("Thread-3")
            try {
                val fakeDestinationPlugin1 = FakeDestinationPlugin()
                val fakeDestinationPlugin2 = FakeDestinationPlugin()

                mediator.add(fakeDestinationPlugin1)

                val work =
                    suspend {
                        println("Doing work on ${Thread.currentThread().name}")
                        println("Mediator plugins: ${mediator.plugins.size}")
                        mediator.applyClosure {
                            (it as EventPlugin).flush()
                        }
                    }

                val job1 =
                    async(dispatcher1) {
                        work()
                        work()
                    }
                val job2 =
                    async(dispatcher2) {
                        // give time for the first work() to start
                        delay(UNIT_OF_WORK_IN_MS / 2)
                        // add plugin 2, 2nd work() should catch up with the newly added plugin
                        mediator.add(fakeDestinationPlugin2)
                    }

                job1.await()
                job2.await()

                // work again
                val job3 =
                    async(dispatcher3) {
                        work()
                    }
                job3.await()

                assertEquals(3, fakeDestinationPlugin1.amountOfWorkDone.get())
                assertEquals(2, fakeDestinationPlugin2.amountOfWorkDone.get())
            } finally {
                dispatcher1.close()
                dispatcher2.close()
                dispatcher3.close()
            }
        }
}
