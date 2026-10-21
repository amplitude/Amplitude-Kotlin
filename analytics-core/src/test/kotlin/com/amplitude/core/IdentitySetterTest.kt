package com.amplitude.core

import com.amplitude.core.events.BaseEvent
import com.amplitude.core.events.IdentifyEvent
import com.amplitude.core.platform.Plugin
import com.amplitude.core.platform.Timeline
import com.amplitude.core.platform.UniversalPlugin
import com.amplitude.core.platform.plugins.ContextPlugin
import com.amplitude.core.utils.FakeAmplitude
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

internal class IdentitySetterTest {
    @Test
    fun `setter replaces complete identity and notifies each changed id and identity once`() {
        val amplitude = FakeAmplitude()
        val order = mutableListOf<String>()
        amplitude.add(
            object : Plugin {
                override val type = Plugin.Type.Before
                override lateinit var amplitude: Amplitude

                override fun execute(event: BaseEvent): BaseEvent {
                    assertTrue(event is IdentifyEvent)
                    assertEquals("user", event.userId)
                    assertEquals("device", event.deviceId)
                    assertEquals(mapOf("\$set" to mapOf("plan" to "paid")), event.userProperties)
                    order += "upload"
                    return event
                }

                override fun onUserIdChanged(userId: String?) {
                    order += "user"
                    assertEquals(mapOf("plan" to "paid"), amplitude.identity.userProperties)
                }

                override fun onDeviceIdChanged(deviceId: String?) {
                    order += "device"
                }

                override fun onIdentityChanged(identity: AnalyticsIdentity) {
                    order += "identity"
                    assertEquals("user", identity.userId)
                    assertEquals("device", identity.deviceId)
                    assertEquals(mapOf("plan" to "paid"), identity.userProperties)
                }
            },
        )

        assertSame(amplitude, amplitude.setIdentity("user", "device", mapOf("plan" to "paid")))

        assertEquals(listOf("upload", "user", "device", "identity"), order)
    }

    @Test
    fun `property replacements notify plugins and no op has no upload or callbacks`() {
        val amplitude = FakeAmplitude()
        val recorder = Recorder()
        amplitude.add(recorder)
        amplitude.setIdentity("user", "device", mapOf("plan" to "free", "remove" to true))
        recorder.identities.clear()
        recorder.events.clear()

        amplitude.setIdentity("user", "device", mapOf("plan" to "paid"))
        amplitude.setIdentity("user", "device", mapOf("plan" to "paid"))

        assertEquals(1, recorder.identities.size)
        assertEquals(mapOf("plan" to "paid"), amplitude.identity.userProperties)
        assertEquals(1, recorder.events.size)
        assertEquals(mapOf("\$set" to mapOf("plan" to "paid")), recorder.events.single().userProperties)

        amplitude.setIdentity("user", "device")
        assertTrue(amplitude.identity.userProperties.isEmpty())
        assertEquals(2, recorder.identities.size)
        assertEquals(1, recorder.events.size, "clearing local properties must not clear the server profile")
    }

    @Test
    fun `setting identity with unchanged properties does not emit an identify`() {
        val amplitude = FakeAmplitude()
        val recorder = Recorder()
        amplitude.add(recorder)
        amplitude.setIdentity("first", "device", mapOf("plan" to "paid"))
        recorder.events.clear()

        amplitude.setIdentity("second", "device", mapOf("plan" to "paid"))

        assertTrue(recorder.events.isEmpty())
        assertEquals("second", amplitude.identity.userId)
    }

    @Test
    fun `setter copies supplied identity and updates local state while opted out`() {
        val amplitude = FakeAmplitude()
        val recorder = Recorder()
        amplitude.add(recorder)
        amplitude.optOut = true
        val list = mutableListOf<Any>("original")
        val properties = mutableMapOf<String, Any?>("nested" to list)
        val identity =
            object : AnalyticsIdentity {
                override val userId = "user"
                override val deviceId: String? = null
                override val userProperties = properties
            }

        assertSame(amplitude, amplitude.setIdentity(identity))
        list += "later"
        properties.clear()

        assertEquals(mapOf("nested" to listOf("original")), amplitude.identity.userProperties)
        assertNull(amplitude.identity.deviceId)
        assertEquals(1, recorder.identities.size)
        assertTrue(recorder.events.isEmpty())
    }

    @Test
    fun `generated identify does not overwrite a newer reentrant local identity`() {
        val amplitude = QueuedAmplitude()
        val recorder = Recorder()
        amplitude.add(recorder)
        amplitude.add(
            object : UniversalPlugin {
                override fun onIdentityChanged(identity: AnalyticsIdentity) {
                    if (identity.userProperties["plan"] == "free") {
                        amplitude.setIdentity("new-user", "new-device", mapOf("plan" to "paid"))
                    }
                }
            },
        )

        amplitude.setIdentity("old-user", "old-device", mapOf("plan" to "free"))
        amplitude.drain()

        assertEquals("new-user", amplitude.identity.userId)
        assertEquals(mapOf("plan" to "paid"), amplitude.identity.userProperties)
        assertEquals(listOf("old-user", "new-user"), recorder.events.map { it.userId })
        assertEquals(listOf("old-device", "new-device"), recorder.events.map { it.deviceId })
        assertEquals(listOf("free", "paid"), recorder.events.map { (it.userProperties?.get("\$set") as Map<*, *>)["plan"] })
    }

    @Test
    fun `reentrant legacy id callback leaves observers on the latest identity`() {
        val amplitude = FakeAmplitude()
        amplitude.add(
            object : Plugin {
                override val type = Plugin.Type.Before
                override lateinit var amplitude: Amplitude

                override fun onUserIdChanged(userId: String?) {
                    if (userId == "outer") {
                        amplitude.setIdentity("inner", "inner-device", mapOf("plan" to "paid"))
                    }
                }
            },
        )
        val observer = Recorder()
        amplitude.add(observer)

        amplitude.setIdentity("outer", "outer-device", mapOf("plan" to "free"))

        assertEquals("inner", observer.identities.last().userId)
        assertEquals(mapOf("plan" to "paid"), observer.identities.last().userProperties)
    }

    @Test
    fun `normal events still inherit missing ids and preserve explicit overrides`() {
        val amplitude = QueuedAmplitude()
        val recorder = Recorder()
        amplitude.add(ContextPlugin()).add(recorder)
        amplitude.track("inherited")
        amplitude.track(
            BaseEvent().apply {
                eventType = "override"
                userId = "override-user"
                deviceId = "override-device"
            },
        )
        amplitude.setUserId("current-user")
        amplitude.setDeviceId("current-device")

        amplitude.drain()

        assertEquals(listOf("current-user", "override-user"), recorder.events.map { it.userId })
        assertEquals(listOf("current-device", "override-device"), recorder.events.map { it.deviceId })
    }

    @Test
    fun `concurrent whole replacements and snapshot reads never mix identity fields`() {
        val amplitude = FakeAmplitude()
        val finished = CountDownLatch(3)
        val failures = ConcurrentLinkedQueue<Throwable>()
        repeat(2) { writer ->
            thread {
                try {
                    repeat(100) { index ->
                        val identity = "$writer-$index"
                        amplitude.setIdentity(identity, identity, mapOf("identity" to identity))
                    }
                } catch (failure: Throwable) {
                    failures += failure
                } finally {
                    finished.countDown()
                }
            }
        }
        thread {
            try {
                repeat(1000) {
                    val snapshot = amplitude.identity
                    assertEquals(snapshot.userId, snapshot.deviceId)
                    if (snapshot.userId != null) assertEquals(snapshot.userId, snapshot.userProperties["identity"])
                }
            } catch (failure: Throwable) {
                failures += failure
            } finally {
                finished.countDown()
            }
        }
        assertTrue(finished.await(10, TimeUnit.SECONDS))
        assertTrue(failures.isEmpty(), failures.toString())
    }

    private class Recorder : Plugin {
        override val type = Plugin.Type.Destination
        override lateinit var amplitude: Amplitude
        val identities = mutableListOf<AnalyticsIdentity>()
        val events = mutableListOf<BaseEvent>()

        override fun onIdentityChanged(identity: AnalyticsIdentity) {
            identities += identity
        }

        override fun execute(event: BaseEvent): BaseEvent {
            events += event
            return event
        }
    }

    private class QueuedAmplitude : Amplitude(Configuration("test")) {
        override fun createTimeline(): Timeline = QueuedTimeline().also { it.amplitude = this }

        // Keep setup/persistence outside these tests; only the event queue is delayed.
        override fun build(): kotlinx.coroutines.Deferred<Boolean> = kotlinx.coroutines.CompletableDeferred(true)

        fun drain() = (timeline as QueuedTimeline).drain()
    }

    private class QueuedTimeline : Timeline() {
        private val events = mutableListOf<BaseEvent>()

        override fun process(incomingEvent: BaseEvent) {
            events += incomingEvent
        }

        fun drain() {
            events.toList().forEach { super.process(it) }
            events.clear()
        }
    }
}
