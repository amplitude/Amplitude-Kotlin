package com.amplitude.core.platform

import com.amplitude.core.Amplitude
import com.amplitude.core.events.BaseEvent
import com.amplitude.core.utils.FakeAmplitude
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Phase 0 Plugin contract for unified blades — interface additions, wiring,
 * dedup, and lookup helpers.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PluginContractTest {
    private val amplitude: Amplitude get() = FakeAmplitude()

    @Nested
    inner class StateCallbacks {
        @Test
        fun `setUserId fires onUserIdChanged on every timeline plugin`() {
            val a = amplitude
            val recorder = RecordingPlugin()
            a.add(recorder)

            a.setUserId("user-1")

            assertEquals(listOf<String?>("user-1"), recorder.userIds)
        }

        @Test
        fun `setDeviceId fires onDeviceIdChanged on every timeline plugin`() {
            val a = amplitude
            val recorder = RecordingPlugin()
            a.add(recorder)

            a.setDeviceId("device-1")

            assertEquals(listOf<String?>("device-1"), recorder.deviceIds)
        }

        @Test
        fun `setting optOut fires onOptOutChanged on every plugin`() {
            val a = amplitude
            val recorder = RecordingPlugin()
            a.add(recorder)

            a.optOut = true

            assertEquals(listOf(true), recorder.optOuts)
        }

        @Test
        fun `reset fires one bundled identity change plus onReset, not two separate identity changes`() {
            val a = amplitude
            val recorder = RecordingPlugin()
            a.add(recorder)

            a.reset()

            // Exactly one userId observation (null) and one deviceId observation (the new id),
            // not interleaved double-fires.
            assertEquals(1, recorder.userIds.size, "expected one userId callback, saw ${recorder.userIds}")
            assertEquals(1, recorder.deviceIds.size, "expected one deviceId callback, saw ${recorder.deviceIds}")
            assertNull(recorder.userIds.single())
            assertEquals(1, recorder.resets)
        }

        @Test
        fun `ObservePlugins also receive onOptOutChanged and onReset`() {
            // Finding 2 resolution: ObservePlugins are notified for state callbacks too.
            val a = amplitude
            val observer = RecordingObservePlugin()
            a.add(observer)

            a.optOut = true
            a.reset()

            assertEquals(listOf(true), observer.optOuts)
            assertEquals(1, observer.resets)
        }
    }

    @Nested
    inner class Dedup {
        @Test
        fun `add with non-null name evicts a previously registered plugin sharing that name`() {
            val a = amplitude
            val first = NamedPlugin("shared")
            val second = NamedPlugin("shared")

            a.add(first)
            a.add(second)

            assertTrue(first.tornDown, "first plugin should have been torn down on dedup")
            assertSame(second, a.findPluginByName("shared"))
        }

        @Test
        fun `add without a name does not deduplicate`() {
            val a = amplitude
            val first = NamedPlugin(name = null)
            val second = NamedPlugin(name = null)

            a.add(first)
            a.add(second)

            // Neither was evicted; both live in the timeline.
            assertEquals(false, first.tornDown)
            assertEquals(false, second.tornDown)
        }

        @Test
        fun `dedup also tears down ObservePlugin in the store`() {
            val a = amplitude
            val first = NamedObservePlugin("obs-shared")
            val second = NamedPlugin("obs-shared")

            a.add(first)
            a.add(second)

            assertTrue(first.tornDown, "ObservePlugin in store should be torn down on dedup")
            assertSame(second, a.findPluginByName("obs-shared"))
        }
    }

    @Nested
    inner class FindPlugin {
        @Test
        fun `findPlugin returns first plugin matching the type across all timeline layers`() {
            val a = amplitude
            val before = TypedPlugin(Plugin.Type.Before)
            val enrichment = MarkerPlugin(Plugin.Type.Enrichment)
            val utility = MarkerPlugin(Plugin.Type.Utility)

            a.add(before)
            a.add(enrichment)
            a.add(utility)

            val found = a.findPlugin<MarkerPlugin>()
            // First MarkerPlugin discovered when iterating layers.
            assertTrue(found === enrichment || found === utility, "expected Enrichment or Utility, got $found")
        }

        @Test
        fun `findPlugin returns null when no plugin matches`() {
            val a = amplitude
            a.add(TypedPlugin(Plugin.Type.Before))

            assertNull(a.findPlugin<MarkerPlugin>())
        }

        @Test
        fun `findPluginByName returns null for an unregistered name`() {
            val a = amplitude
            a.add(NamedPlugin("registered"))

            assertNull(a.findPluginByName("not-registered"))
        }

        @Test
        fun `findPluginByName traverses the observe store too`() {
            val a = amplitude
            val observe = NamedObservePlugin("observed")

            a.add(observe)

            assertSame(observe, a.findPluginByName("observed"))
        }
    }

    @Nested
    inner class ObserveTypeTrap {
        // Finding 1 resolution: a Type.Observe mediator now exists in Timeline,
        // so a bare Plugin declaring Plugin.Type.Observe lands in the timeline
        // and receives state callbacks instead of being silently dropped.
        @Test
        fun `bare Plugin with Type Observe is wired into the timeline and receives callbacks`() {
            val a = amplitude
            val observePlugin =
                object : Plugin {
                    override val type: Plugin.Type = Plugin.Type.Observe
                    override lateinit var amplitude: Amplitude
                    var receivedUserIds = mutableListOf<String?>()

                    override fun onUserIdChanged(userId: String?) {
                        receivedUserIds += userId
                    }
                }

            a.add(observePlugin)
            a.setUserId("trap-test")

            assertEquals(listOf<String?>("trap-test"), observePlugin.receivedUserIds)
            assertSame(observePlugin, a.timeline.findPlugin<Plugin>())
        }
    }
}

private open class RecordingPlugin(override val name: String? = "recorder") : Plugin {
    override val type: Plugin.Type = Plugin.Type.Before
    override lateinit var amplitude: Amplitude

    val userIds = mutableListOf<String?>()
    val deviceIds = mutableListOf<String?>()
    val optOuts = mutableListOf<Boolean>()
    var resets: Int = 0

    override fun execute(event: BaseEvent): BaseEvent = event

    override fun onUserIdChanged(userId: String?) {
        userIds += userId
    }

    override fun onDeviceIdChanged(deviceId: String?) {
        deviceIds += deviceId
    }

    override fun onOptOutChanged(optOut: Boolean) {
        optOuts += optOut
    }

    override fun onReset() {
        resets += 1
    }
}

private class RecordingObservePlugin : ObservePlugin() {
    override lateinit var amplitude: Amplitude
    val optOuts = mutableListOf<Boolean>()
    var resets: Int = 0

    override fun onUserIdChanged(userId: String?) {}

    override fun onDeviceIdChanged(deviceId: String?) {}

    override fun onOptOutChanged(optOut: Boolean) {
        optOuts += optOut
    }

    override fun onReset() {
        resets += 1
    }
}

private class NamedPlugin(override val name: String?) : Plugin {
    override val type: Plugin.Type = Plugin.Type.Before
    override lateinit var amplitude: Amplitude
    var tornDown: Boolean = false

    override fun execute(event: BaseEvent): BaseEvent = event

    override fun teardown() {
        tornDown = true
    }
}

private class NamedObservePlugin(override val name: String?) : ObservePlugin() {
    override lateinit var amplitude: Amplitude
    var tornDown: Boolean = false

    override fun onUserIdChanged(userId: String?) {}

    override fun onDeviceIdChanged(deviceId: String?) {}

    override fun teardown() {
        tornDown = true
    }
}

private class TypedPlugin(override val type: Plugin.Type) : Plugin {
    override lateinit var amplitude: Amplitude
}

private class MarkerPlugin(override val type: Plugin.Type) : Plugin {
    override lateinit var amplitude: Amplitude
}
