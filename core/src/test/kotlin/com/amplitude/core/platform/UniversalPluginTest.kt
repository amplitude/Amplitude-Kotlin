package com.amplitude.core.platform

import com.amplitude.core.Amplitude
import com.amplitude.core.AmplitudeContext
import com.amplitude.core.AnalyticsClient
import com.amplitude.core.AnalyticsIdentity
import com.amplitude.core.RestrictedAmplitudeFeature
import com.amplitude.core.events.BaseEvent
import com.amplitude.core.utils.FakeAmplitude
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@OptIn(RestrictedAmplitudeFeature::class)
class UniversalPluginTest {
    private fun amplitude(): FakeAmplitude = FakeAmplitude()

    private class RecordingUniversalPlugin(
        override val name: String? = null,
    ) : UniversalPlugin {
        var setupClient: AnalyticsClient? = null
        var setupContext: AmplitudeContext? = null
        var teardownCount = 0
        val executedTypes = mutableListOf<String>()
        val identitySnapshots = mutableListOf<AnalyticsIdentity>()
        val sessionIds = mutableListOf<Long>()
        val optOuts = mutableListOf<Boolean>()
        var resets = 0

        override fun setup(
            client: AnalyticsClient,
            context: AmplitudeContext,
        ) {
            setupClient = client
            setupContext = context
        }

        override fun execute(event: BaseEvent): BaseEvent? {
            executedTypes += event.eventType
            return event
        }

        override fun teardown() {
            teardownCount += 1
        }

        override fun onIdentityChanged(identity: AnalyticsIdentity) {
            identitySnapshots += identity
        }

        override fun onSessionIdChanged(sessionId: Long) {
            sessionIds += sessionId
        }

        override fun onOptOutChanged(optOut: Boolean) {
            optOuts += optOut
        }

        override fun onReset() {
            resets += 1
        }
    }

    private class RecordingPlugin : Plugin {
        override val type: Plugin.Type = Plugin.Type.Before
        override lateinit var amplitude: Amplitude

        var universalSetupCount = 0
        var setupClient: AnalyticsClient? = null
        var setupContext: AmplitudeContext? = null
        val userIds = mutableListOf<String?>()
        val deviceIds = mutableListOf<String?>()
        val identitySnapshots = mutableListOf<AnalyticsIdentity>()
        val optOuts = mutableListOf<Boolean>()
        var resets = 0

        override fun setup(
            client: AnalyticsClient,
            context: AmplitudeContext,
        ) {
            universalSetupCount += 1
            setupClient = client
            setupContext = context
        }

        override fun execute(event: BaseEvent): BaseEvent = event

        override fun onUserIdChanged(userId: String?) {
            userIds += userId
        }

        override fun onDeviceIdChanged(deviceId: String?) {
            deviceIds += deviceId
        }

        override fun onIdentityChanged(identity: AnalyticsIdentity) {
            identitySnapshots += identity
        }

        override fun onOptOutChanged(optOut: Boolean) {
            optOuts += optOut
        }

        override fun onReset() {
            resets += 1
        }
    }

    @Nested
    inner class UniversalPluginContract {
        @Test
        fun `UniversalPlugin has no-op defaults for all callbacks`() {
            val plugin = object : UniversalPlugin {}
            val amplitude = FakeAmplitude()
            val identity =
                object : AnalyticsIdentity {
                    override val userId: String? = "u"
                    override val deviceId: String? = "d"
                }
            plugin.setup(amplitude, amplitude.amplitudeContext)
            plugin.onIdentityChanged(identity)
            plugin.onSessionIdChanged(1L)
            plugin.onOptOutChanged(true)
            plugin.onReset()
            val result = plugin.execute(BaseEvent().also { it.eventType = "test" })
            assertNotNull(result)
        }

        @Test
        fun `UniversalPlugin name defaults to null`() {
            val plugin = object : UniversalPlugin {}
            assertNull(plugin.name)
        }

        @Test
        fun `AnalyticsIdentity userProperties defaults to empty map`() {
            val identity =
                object : AnalyticsIdentity {
                    override val userId: String? = "u"
                    override val deviceId: String? = "d"
                }
            assertTrue(identity.userProperties.isEmpty())
        }
    }

    @Nested
    inner class BareUniversalPluginHosting {
        @Test
        fun `bare UniversalPlugin receives setup with client and context`() {
            val a = amplitude()
            val blade = RecordingUniversalPlugin()

            a.add(blade)

            assertSame(a, blade.setupClient)
            assertEquals("FAKE-API-KEY", blade.setupContext?.apiKey)
        }

        @Test
        fun `bare UniversalPlugin defaults to Enrichment and executes events`() {
            val a = amplitude()
            val blade = RecordingUniversalPlugin("blade")

            a.add(blade)
            a.track("blade-event", null)

            var enrichmentCount = 0
            a.timeline.applyClosure { plugin ->
                if (plugin is UniversalPluginAdapter && plugin.delegate === blade) {
                    enrichmentCount += 1
                    assertEquals(Plugin.Type.Enrichment, plugin.type)
                }
            }
            assertEquals(1, enrichmentCount)
            assertEquals(listOf("blade-event"), blade.executedTypes)
        }

        @Test
        fun `bare UniversalPlugin receives lifecycle callbacks`() {
            val a = amplitude()
            val blade = RecordingUniversalPlugin()

            a.add(blade)
            a.setUserId("uid")
            a.optOut = true
            a.reset()

            assertEquals(2, blade.identitySnapshots.size)
            assertEquals("uid", blade.identitySnapshots.first().userId)
            assertEquals(listOf(true), blade.optOuts)
            assertEquals(1, blade.resets)
        }

        @Test
        fun `PluginHost queries return the original delegate`() {
            val a = amplitude()
            val blade = RecordingUniversalPlugin("blade")

            a.add(blade)

            assertSame(blade, a.plugin("blade"))
            assertEquals(listOf(blade), a.plugins<RecordingUniversalPlugin>())
        }

        @Test
        fun `remove tears down bare UniversalPlugin exactly once`() {
            val a = amplitude()
            val blade = RecordingUniversalPlugin()

            a.add(blade)
            a.remove(blade)

            assertEquals(1, blade.teardownCount)
        }

        @Test
        fun `remove clears every registration of an unnamed UniversalPlugin`() {
            val a = amplitude()
            val blade = RecordingUniversalPlugin()
            a.add(blade)
            a.add(blade)

            a.remove(blade)

            assertTrue(a.plugins<RecordingUniversalPlugin>().isEmpty())
            assertEquals(2, blade.teardownCount)
        }

        @Test
        fun `named bare UniversalPlugin deduplicates on add`() {
            val a = amplitude()
            val first = RecordingUniversalPlugin("dedupe-blade")
            val second = RecordingUniversalPlugin("dedupe-blade")

            a.add(first)
            a.add(second)

            assertEquals(1, a.plugins<RecordingUniversalPlugin>().size)
            assertSame(first, a.plugins<RecordingUniversalPlugin>().single())
        }
    }

    @Nested
    inner class IdentityChangedWiring {
        @Test
        fun `Plugin receives onIdentityChanged after setUserId`() {
            val a = amplitude()
            val recorder = RecordingPlugin()
            a.add(recorder)

            a.setUserId("user-1")

            assertEquals(1, recorder.identitySnapshots.size)
            assertEquals("user-1", recorder.identitySnapshots[0].userId)
        }

        @Test
        fun `Plugin receives onIdentityChanged after setDeviceId`() {
            val a = amplitude()
            val recorder = RecordingPlugin()
            a.add(recorder)

            a.setDeviceId("device-1")

            assertEquals(1, recorder.identitySnapshots.size)
            assertEquals("device-1", recorder.identitySnapshots[0].deviceId)
        }

        @Test
        fun `onIdentityChanged carries the current identity snapshot`() {
            val a = amplitude()
            val recorder = RecordingPlugin()
            a.add(recorder)

            a.setUserId("snap-user")
            a.setDeviceId("snap-device")

            assertEquals(2, recorder.identitySnapshots.size)
            val last = recorder.identitySnapshots.last()
            assertEquals("snap-user", last.userId)
            assertEquals("snap-device", last.deviceId)
        }

        @Test
        fun `per-field callbacks fire before onIdentityChanged`() {
            val a = amplitude()
            val callOrder = mutableListOf<String>()
            val ordered =
                object : Plugin {
                    override val type: Plugin.Type = Plugin.Type.Before
                    override lateinit var amplitude: Amplitude

                    override fun execute(event: BaseEvent): BaseEvent = event

                    override fun onUserIdChanged(userId: String?) {
                        callOrder += "userId"
                    }

                    override fun onIdentityChanged(identity: AnalyticsIdentity) {
                        callOrder += "identity"
                    }
                }
            a.add(ordered)

            a.setUserId("order-test")

            assertEquals(listOf("userId", "identity"), callOrder)
        }

        @Test
        fun `onIdentityChanged fires on reset with null userId and new deviceId`() {
            val a = amplitude()
            val recorder = RecordingPlugin()
            a.add(recorder)

            a.reset()

            assertEquals(1, recorder.identitySnapshots.size)
            val last = recorder.identitySnapshots.last()
            assertNull(last.userId)
            assertNotNull(last.deviceId)
        }

        @Test
        fun `existing Plugin subclasses still receive onUserIdChanged and onDeviceIdChanged`() {
            val a = amplitude()
            val recorder = RecordingPlugin()
            a.add(recorder)

            a.setUserId("legacy-user")
            a.setDeviceId("legacy-device")

            assertEquals(listOf<String?>("legacy-user"), recorder.userIds)
            assertEquals(listOf<String?>("legacy-device"), recorder.deviceIds)
        }

        @Test
        fun `onIdentityChanged reflects re-entrant identity mutation from legacy callback`() {
            val a = amplitude()
            val recorder = RecordingPlugin()
            val reentrant =
                object : Plugin {
                    override val type: Plugin.Type = Plugin.Type.Before
                    override lateinit var amplitude: Amplitude

                    override fun execute(event: BaseEvent): BaseEvent = event

                    override fun onUserIdChanged(userId: String?) {
                        amplitude.setDeviceId("reentrant-device")
                    }
                }
            a.add(reentrant)
            a.add(recorder)

            a.setUserId("outer-user")

            val last = recorder.identitySnapshots.last()
            assertEquals("outer-user", last.userId)
            assertEquals("reentrant-device", last.deviceId)
        }

        @Test
        fun `Plugin setup bridges to universal setup via super`() {
            val a = amplitude()
            val recorder = RecordingPlugin()

            a.add(recorder)

            assertEquals(1, recorder.universalSetupCount)
            assertSame(a, recorder.amplitude)
            assertSame(a, recorder.setupClient)
            assertSame(a.amplitudeContext, recorder.setupContext)
        }
    }

    @Nested
    inner class AnalyticsClientImplementation {
        @Test
        fun `Amplitude identity reflects current store state`() {
            val a = amplitude()
            a.store.userId = "id-user"
            a.store.deviceId = "id-device"

            assertEquals("id-user", a.identity.userId)
            assertEquals("id-device", a.identity.deviceId)
            assertTrue(a.identity.userProperties.isEmpty())
        }

        @Test
        fun `Amplitude sessionId returns -1 in core`() {
            val a = amplitude()
            assertEquals(-1L, a.sessionId)
        }

        @Test
        fun `Amplitude optOut reflects configuration`() {
            val a = amplitude()
            a.optOut = true
            assertEquals(true, a.optOut)
        }

        @Test
        fun `flipping optOut invokes onOptOutChanged exactly once per assignment`() {
            val a = amplitude()
            val recorder = RecordingPlugin()
            a.add(recorder)

            a.optOut = true
            a.optOut = false
            a.optOut = true

            assertEquals(listOf(true, false, true), recorder.optOuts)
        }

        @Test
        fun `assigning the current optOut value still notifies plugins`() {
            val a = amplitude()
            val recorder = RecordingPlugin()
            a.add(recorder)

            a.optOut = false

            assertEquals(listOf(false), recorder.optOuts)
        }

        @Test
        fun `Amplitude trackEvent satisfies AnalyticsClient contract`() {
            val client: AnalyticsClient = amplitude()
            client.trackEvent("test-event", null)
            client.trackEvent("test-event", mapOf("key" to "value"))
        }
    }
}
