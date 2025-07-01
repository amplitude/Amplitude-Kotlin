package com.amplitude.core.platform

import com.amplitude.core.Amplitude
import com.amplitude.core.events.BaseEvent
import com.amplitude.core.events.IdentifyEvent
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicInteger

class TimelineTest {
    private lateinit var mockAmplitude: Amplitude
    private lateinit var timeline: Timeline

    @BeforeEach
    fun setUp() {
        mockAmplitude = mockk<Amplitude>(relaxed = true) {
            every { configuration } returns mockk(relaxed = true) {
                every { optOut } returns false
            }
        }
        timeline = Timeline(mockAmplitude)
    }

    /**
     * Fake plugin for testing
     */
    private class FakePlugin(
        override val type: Plugin.Type,
        override val name: String = "FakePlugin"
    ) : Plugin {
        override lateinit var amplitude: Amplitude
        val executeCallCount = AtomicInteger(0)
        val setupCallCount = AtomicInteger(0)
        val teardownCallCount = AtomicInteger(0)
        var shouldReturnNull = false
        var shouldThrowException = false
        var returnEvent: BaseEvent? = null

        override fun setup(amplitude: Amplitude) {
            this.amplitude = amplitude
            setupCallCount.incrementAndGet()
        }

        override fun execute(event: BaseEvent): BaseEvent? {
            executeCallCount.incrementAndGet()
            if (shouldThrowException) {
                throw RuntimeException("Test exception")
            }
            return if (shouldReturnNull) null else (returnEvent ?: event)
        }

        override fun teardown() {
            teardownCallCount.incrementAndGet()
        }
    }

    /**
     * Fake destination plugin for testing
     */
    private class FakeDestinationPlugin(
        override val name: String = "FakeDestinationPlugin"
    ) : DestinationPlugin() {
        val processCallCount = AtomicInteger(0)
        var shouldReturnNull = false
        var shouldThrowException = false
        var returnEvent: BaseEvent? = null

        override fun process(event: BaseEvent): BaseEvent? {
            processCallCount.incrementAndGet()
            if (shouldThrowException) {
                throw RuntimeException("Test exception")
            }
            return if (shouldReturnNull) null else (returnEvent ?: event)
        }
    }

    @Test
    fun `timeline should have all plugin types initialized`() {
        assertNotNull(timeline.plugins[Plugin.Type.Before])
        assertNotNull(timeline.plugins[Plugin.Type.Enrichment])
        assertNotNull(timeline.plugins[Plugin.Type.Destination])
        assertNotNull(timeline.plugins[Plugin.Type.Utility])
    }

    @Test
    fun `add plugin should setup plugin and add to correct mediator`() {
        val plugin = FakePlugin(Plugin.Type.Before)
        
        timeline.add(plugin)

        assertEquals(1, plugin.setupCallCount.get())
        assertSame(mockAmplitude, plugin.amplitude)
        assertEquals(1, timeline.plugins[Plugin.Type.Before]?.size() ?: 0)
    }

    @Test
    fun `add multiple plugins of same type should add to same mediator`() {
        val plugin1 = FakePlugin(Plugin.Type.Before, "Plugin1")
        val plugin2 = FakePlugin(Plugin.Type.Before, "Plugin2")
        
        timeline.add(plugin1)
        timeline.add(plugin2)
        
        assertEquals(2, timeline.plugins[Plugin.Type.Before]?.size() ?: 0)
    }

    @Test
    fun `add plugins of different types should add to different mediators`() {
        val beforePlugin = FakePlugin(Plugin.Type.Before, "BeforePlugin")
        val enrichmentPlugin = FakePlugin(Plugin.Type.Enrichment, "EnrichmentPlugin")
        val destinationPlugin = FakePlugin(Plugin.Type.Destination, "DestinationPlugin")
        val utilityPlugin = FakePlugin(Plugin.Type.Utility, "UtilityPlugin")
        
        timeline.add(beforePlugin)
        timeline.add(enrichmentPlugin)
        timeline.add(destinationPlugin)
        timeline.add(utilityPlugin)
        
        assertEquals(1, timeline.plugins[Plugin.Type.Before]?.size() ?: 0)
        assertEquals(1, timeline.plugins[Plugin.Type.Enrichment]?.size() ?: 0)
        assertEquals(1, timeline.plugins[Plugin.Type.Destination]?.size() ?: 0)
        assertEquals(1, timeline.plugins[Plugin.Type.Utility]?.size() ?: 0)
    }

    @Test
    fun `process event should execute all plugin stages in order`() {
        val beforePlugin = FakePlugin(Plugin.Type.Before, "BeforePlugin")
        val enrichmentPlugin = FakePlugin(Plugin.Type.Enrichment, "EnrichmentPlugin")
        val destinationPlugin = FakeDestinationPlugin("DestinationPlugin")
        
        timeline.add(beforePlugin)
        timeline.add(enrichmentPlugin)
        timeline.add(destinationPlugin)
        
        val event = BaseEvent()
        event.eventType = "test_event"
        
        timeline.process(event)
        
        assertEquals(1, beforePlugin.executeCallCount.get())
        assertEquals(1, enrichmentPlugin.executeCallCount.get())
        assertEquals(1, destinationPlugin.processCallCount.get())
    }

    @Test
    fun `process event should return early when opt out is enabled`() {

        every { mockAmplitude.configuration.optOut } returns true
        
        val beforePlugin = FakePlugin(Plugin.Type.Before)
        timeline.add(beforePlugin)
        
        val event = BaseEvent()
        event.eventType = "test_event"
        
        timeline.process(event)
        
        assertEquals(0, beforePlugin.executeCallCount.get())
    }

    @Test
    fun `process event should return early when before plugin returns null`() {
        val beforePlugin = FakePlugin(Plugin.Type.Before)
        beforePlugin.shouldReturnNull = true
        val enrichmentPlugin = FakePlugin(Plugin.Type.Enrichment)
        
        timeline.add(beforePlugin)
        timeline.add(enrichmentPlugin)
        
        val event = BaseEvent()
        event.eventType = "test_event"
        
        timeline.process(event)
        
        assertEquals(1, beforePlugin.executeCallCount.get())
        assertEquals(0, enrichmentPlugin.executeCallCount.get())
    }

    @Test
    fun `process event should return early when enrichment plugin returns null`() {
        val beforePlugin = FakePlugin(Plugin.Type.Before, "BeforePlugin")
        val enrichmentPlugin = FakePlugin(Plugin.Type.Enrichment, "EnrichmentPlugin")
        enrichmentPlugin.shouldReturnNull = true
        val destinationPlugin = FakeDestinationPlugin()
        
        timeline.add(beforePlugin)
        timeline.add(enrichmentPlugin)
        timeline.add(destinationPlugin)
        
        val event = BaseEvent()
        event.eventType = "test_event"
        
        timeline.process(event)
        
        assertEquals(1, beforePlugin.executeCallCount.get())
        assertEquals(1, enrichmentPlugin.executeCallCount.get())
        assertEquals(0, destinationPlugin.processCallCount.get())
    }

    @Test
    fun `process event should continue when destination plugin returns null`() {
        val beforePlugin = FakePlugin(Plugin.Type.Before, "BeforePlugin")
        val enrichmentPlugin = FakePlugin(Plugin.Type.Enrichment, "EnrichmentPlugin")
        val destinationPlugin = FakeDestinationPlugin()
        destinationPlugin.shouldReturnNull = true
        
        timeline.add(beforePlugin)
        timeline.add(enrichmentPlugin)
        timeline.add(destinationPlugin)
        
        val event = BaseEvent()
        event.eventType = "test_event"
        
        timeline.process(event)
        
        assertEquals(1, beforePlugin.executeCallCount.get())
        assertEquals(1, enrichmentPlugin.executeCallCount.get())
        assertEquals(1, destinationPlugin.processCallCount.get())
    }

    @Test
    fun `applyPlugins should return original event when no plugins exist`() {
        val event = BaseEvent()
        event.eventType = "test_event"
        
        val result = timeline.applyPlugins(Plugin.Type.Before, event)
        
        assertSame(event, result)
    }

    @Test
    fun `applyPlugins should execute plugins and return result`() {
        val plugin1 = FakePlugin(Plugin.Type.Before, "Plugin1")
        val plugin2 = FakePlugin(Plugin.Type.Before, "Plugin2")
        val modifiedEvent = BaseEvent()
        modifiedEvent.eventType = "modified_event"
        plugin2.returnEvent = modifiedEvent
        
        timeline.add(plugin1)
        timeline.add(plugin2)
        
        val event = BaseEvent()
        event.eventType = "test_event"
        
        val result = timeline.applyPlugins(Plugin.Type.Before, event)
        
        assertEquals(1, plugin1.executeCallCount.get())
        assertEquals(1, plugin2.executeCallCount.get())
        assertSame(modifiedEvent, result)
    }

    @Test
    fun `applyPlugins should return null when plugin returns null`() {
        val plugin = FakePlugin(Plugin.Type.Before)
        plugin.shouldReturnNull = true
        
        timeline.add(plugin)
        
        val event = BaseEvent()
        event.eventType = "test_event"
        
        val result = timeline.applyPlugins(Plugin.Type.Before, event)
        
        assertNull(result)
    }

    @Test
    fun `remove plugin should remove from all mediators and call teardown`() {
        val beforePlugin = FakePlugin(Plugin.Type.Before, "TestPluginBefore")
        val enrichmentPlugin = FakePlugin(Plugin.Type.Enrichment, "TestPluginEnrichment")
        
        timeline.add(beforePlugin)
        timeline.add(enrichmentPlugin)
        
        timeline.remove(beforePlugin)
        
        assertEquals(0, timeline.plugins[Plugin.Type.Before]?.size() ?: 0)
        assertEquals(1, timeline.plugins[Plugin.Type.Enrichment]?.size() ?: 0)
        assertEquals(1, beforePlugin.teardownCallCount.get())
        assertEquals(0, enrichmentPlugin.teardownCallCount.get())
    }

    @Test
    fun `remove plugin should handle plugins not found gracefully`() {
        val plugin = FakePlugin(Plugin.Type.Before)
        
        timeline.remove(plugin)
        
        assertEquals(0, plugin.teardownCallCount.get())
    }

    @Test
    fun `applyClosure should apply closure to all plugins`() {
        val beforePlugin = FakePlugin(Plugin.Type.Before, "BeforePlugin")
        val enrichmentPlugin = FakePlugin(Plugin.Type.Enrichment, "EnrichmentPlugin")
        val destinationPlugin = FakeDestinationPlugin("DestinationPlugin")
        
        timeline.add(beforePlugin)
        timeline.add(enrichmentPlugin)
        timeline.add(destinationPlugin)
        
        val closureCallCount = AtomicInteger(0)
        
        timeline.applyClosure { 
            closureCallCount.incrementAndGet()
        }
        
        assertEquals(3, closureCallCount.get())
    }

    @Test
    fun `applyClosure should work with empty timeline`() {
        val closureCallCount = AtomicInteger(0)
        
        timeline.applyClosure { 
            closureCallCount.incrementAndGet()
        }
        
        assertEquals(0, closureCallCount.get())
    }

    @Test
    fun `process should handle exceptions gracefully`() {
        val beforePlugin = FakePlugin(Plugin.Type.Before, "BeforePlugin")
        beforePlugin.shouldThrowException = true
        val enrichmentPlugin = FakePlugin(Plugin.Type.Enrichment, "EnrichmentPlugin")
        
        timeline.add(beforePlugin)
        timeline.add(enrichmentPlugin)
        
        val event = BaseEvent()
        event.eventType = "test_event"
        
        // Should not throw exception
        timeline.process(event)
        
        assertEquals(1, beforePlugin.executeCallCount.get())
        assertEquals(1, enrichmentPlugin.executeCallCount.get())
    }

    @Test
    fun `process should handle destination plugin exceptions gracefully`() {
        val beforePlugin = FakePlugin(Plugin.Type.Before, "BeforePlugin")
        val enrichmentPlugin = FakePlugin(Plugin.Type.Enrichment, "EnrichmentPlugin")
        val destinationPlugin = FakeDestinationPlugin()
        destinationPlugin.shouldThrowException = true
        
        timeline.add(beforePlugin)
        timeline.add(enrichmentPlugin)
        timeline.add(destinationPlugin)
        
        val event = BaseEvent()
        event.eventType = "test_event"
        
        // Should not throw exception
        timeline.process(event)
        
        assertEquals(1, beforePlugin.executeCallCount.get())
        assertEquals(1, enrichmentPlugin.executeCallCount.get())
        assertEquals(1, destinationPlugin.processCallCount.get())
    }

    @Test
    fun `process should work with IdentifyEvent`() {
        val beforePlugin = FakePlugin(Plugin.Type.Before, "BeforePlugin")
        val enrichmentPlugin = FakePlugin(Plugin.Type.Enrichment, "EnrichmentPlugin")
        val destinationPlugin = FakeDestinationPlugin()
        
        timeline.add(beforePlugin)
        timeline.add(enrichmentPlugin)
        timeline.add(destinationPlugin)
        
        val event = IdentifyEvent()
        event.eventType = "test_identify"
        
        timeline.process(event)
        
        assertEquals(1, beforePlugin.executeCallCount.get())
        assertEquals(1, enrichmentPlugin.executeCallCount.get())
        assertEquals(1, destinationPlugin.processCallCount.get())
    }

    @Test
    fun `timeline should be extensible`() {
        val extendedTimeline = object : Timeline(mockAmplitude) {
            var customProcessCallCount = AtomicInteger(0)
            
            override fun process(incomingEvent: BaseEvent) {
                customProcessCallCount.incrementAndGet()
                super.process(incomingEvent)
            }
        }
        
        val event = BaseEvent()
        event.eventType = "test_event"
        
        extendedTimeline.process(event)
        
        assertEquals(1, extendedTimeline.customProcessCallCount.get())
    }
} 