package com.amplitude.core.platform

import com.amplitude.core.Amplitude
import com.amplitude.core.AnalyticsClient
import com.amplitude.core.AnalyticsIdentity
import com.amplitude.core.events.BaseEvent
import com.amplitude.core.utils.FakeAmplitude
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class UniversalPluginTest {
    private fun amplitude(): Amplitude = FakeAmplitude()

    // A minimal UniversalPlugin that is NOT a Plugin — has no Amplitude dependency.
    private class RecordingUniversalPlugin : UniversalPlugin {
        val identitySnapshots = mutableListOf<AnalyticsIdentity>()
        val sessionIds = mutableListOf<Long>()
        val optOuts = mutableListOf<Boolean>()
        var setupClient: AnalyticsClient? = null
        var resets = 0

        override fun setup(client: AnalyticsClient) {
            setupClient = client
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

    // A Plugin subclass that records both per-field and bundled identity callbacks.
    private class RecordingPlugin : Plugin {
        override val type: Plugin.Type = Plugin.Type.Before
        override lateinit var amplitude: Amplitude

        val userIds = mutableListOf<String?>()
        val deviceIds = mutableListOf<String?>()
        val identitySnapshots = mutableListOf<AnalyticsIdentity>()
        var resets = 0

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

        override fun onReset() {
            resets += 1
        }
    }

    @Nested
    inner class UniversalPluginContract {
        @Test
        fun `UniversalPlugin has no-op defaults for all callbacks`() {
            val plugin = object : UniversalPlugin {}
            val identity =
                object : AnalyticsIdentity {
                    override val userId: String? = "u"
                    override val deviceId: String? = "d"
                }
            // None of these should throw.
            plugin.setup(FakeAmplitude())
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

            // Two separate mutations → two onIdentityChanged calls.
            assertEquals(2, recorder.identitySnapshots.size)
            // After the second mutation, the snapshot has both values.
            val last = recorder.identitySnapshots.last()
            assertEquals("snap-user", last.userId)
            assertEquals("snap-device", last.deviceId)
        }

        @Test
        fun `per-field callbacks fire before onIdentityChanged`() {
            // We verify ordering by checking that when onIdentityChanged fires,
            // onUserIdChanged has already been called (order within same plugin).
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

            // reset fires one userId + one deviceId notification → two onIdentityChanged calls.
            // The last snapshot reflects the fully-reset state.
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
        fun `Amplitude track(eventType) satisfies AnalyticsClient contract`() {
            val client: AnalyticsClient = amplitude()
            // Just verify it doesn't throw — events are processed asynchronously.
            client.track("test-event")
            client.track("test-event", mapOf("key" to "value"))
        }
    }
}
