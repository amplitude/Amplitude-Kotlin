package com.amplitude.core

import com.amplitude.core.events.BaseEvent
import com.amplitude.core.events.EventOptions
import com.amplitude.core.events.Identify
import com.amplitude.core.platform.Plugin
import com.amplitude.core.platform.UniversalPlugin
import com.amplitude.core.utils.FakeAmplitude
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

internal class IdentityCallbackTest {
    private val amplitude = FakeAmplitude()
    private val callbacks = mutableListOf<String>()
    private val identities = mutableListOf<AnalyticsIdentity>()
    private val plugin =
        object : Plugin {
            override val type = Plugin.Type.Before
            override lateinit var amplitude: Amplitude

            override fun onUserIdChanged(userId: String?) {
                callbacks += "user:$userId"
            }

            override fun onDeviceIdChanged(deviceId: String?) {
                callbacks += "device:$deviceId"
            }

            override fun onIdentityChanged(identity: AnalyticsIdentity) {
                callbacks += "identity"
                identities += identity
            }

            override fun onReset() {
                callbacks += "reset"
            }
        }

    @Nested
    inner class IdSetters {
        @Test
        fun `repeated ids do not notify individual or identity callbacks`() {
            amplitude.setUserId("user")
            amplitude.setDeviceId("device")
            amplitude.add(plugin)

            amplitude.setUserId("user")
            amplitude.setDeviceId("device")

            assertTrue(callbacks.isEmpty())
        }

        @Test
        fun `changed ids notify each individual and identity callback once`() {
            amplitude.add(plugin)

            amplitude.setUserId("user")
            amplitude.setDeviceId("device")
            amplitude.setUserId(null)

            assertEquals(listOf("user:user", "identity", "device:device", "identity", "user:null", "identity"), callbacks)
        }

        @Test
        fun `identify with unchanged option ids does not trigger id callbacks`() {
            amplitude.setUserId("user")
            amplitude.setDeviceId("device")
            amplitude.add(plugin)

            amplitude.identify(
                Identify(),
                EventOptions().apply {
                    userId = "user"
                    deviceId = "device"
                },
            )

            assertTrue(callbacks.isEmpty())
        }
    }

    @Nested
    inner class IdentifyOptions {
        private val signIn =
            EventOptions().apply {
                userId = "user"
                deviceId = "device"
            }

        @Test
        fun `changed option ids and properties notify one complete identity`() {
            amplitude.add(plugin)

            amplitude.identify(Identify().set("plan", "paid"), signIn)

            assertEquals(listOf("user:user", "device:device", "identity"), callbacks)
            val identity = identities.single()
            assertEquals("user", identity.userId)
            assertEquals("device", identity.deviceId)
            assertEquals(mapOf("plan" to "paid"), identity.userProperties)
        }

        @Test
        fun `property map overload applies option ids with its properties`() {
            amplitude.add(plugin)

            amplitude.identify(mapOf("plan" to "paid"), EventOptions().apply { userId = "user" })

            assertEquals(listOf("user:user", "identity"), callbacks)
            assertEquals("user", identities.single().userId)
            assertEquals(mapOf("plan" to "paid"), identities.single().userProperties)
        }

        @Test
        fun `changed option id with unchanged properties notifies once`() {
            amplitude.identify(Identify().set("plan", "paid"))
            amplitude.add(plugin)

            amplitude.identify(Identify().set("plan", "paid").add("visits", 1), EventOptions().apply { userId = "user" })

            assertEquals(listOf("user:user", "identity"), callbacks)
            assertEquals(mapOf("plan" to "paid"), identities.single().userProperties)
        }

        @Test
        fun `unchanged option ids with changed properties notify identity only`() {
            amplitude.identify(Identify(), signIn)
            amplitude.add(plugin)

            amplitude.identify(Identify().set("plan", "paid"), signIn)

            assertEquals(listOf("identity"), callbacks)
            assertEquals(mapOf("plan" to "paid"), identities.single().userProperties)
        }

        @Test
        fun `opted out identify applies option ids without properties or upload`() {
            val events = mutableListOf<BaseEvent>()
            amplitude.add(plugin).add(destination { events += it })
            amplitude.optOut = true

            amplitude.identify(Identify().set("plan", "paid"), signIn)

            assertEquals(listOf("user:user", "device:device", "identity"), callbacks)
            assertTrue(identities.single().userProperties.isEmpty())
            assertTrue(amplitude.identity.userProperties.isEmpty())
            assertTrue(events.isEmpty())
        }

        @Test
        fun `legacy id callbacks precede the queued event and one complete identity follows`() {
            val events = mutableListOf<BaseEvent>()
            amplitude.add(plugin).add(
                destination {
                    events += it
                    callbacks += "event"
                },
            )

            amplitude.identify(Identify().set("plan", "paid"), signIn)

            assertEquals(listOf("user:user", "device:device", "event", "identity"), callbacks)
            assertEquals("user", events.single().userId)
            assertEquals("device", events.single().deviceId)
        }

        @Test
        fun `readers never observe option ids without their identify properties`() {
            repeat(20) {
                val subject = FakeAmplitude()
                val failures = ConcurrentLinkedQueue<Throwable>()
                val finished = CountDownLatch(3)
                repeat(2) { writer ->
                    thread {
                        try {
                            repeat(200) { index ->
                                val id = "$writer-$index"
                                subject.identify(
                                    Identify().set("owner", id),
                                    EventOptions().apply {
                                        userId = id
                                        deviceId = id
                                    },
                                )
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
                        repeat(2000) {
                            val identity = subject.identity
                            if (identity.userId != null) {
                                assertEquals(identity.userId, identity.deviceId)
                                assertEquals(identity.userId, identity.userProperties["owner"])
                            }
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
        }

        @Test
        fun `throwing pipeline plugin still delivers the complete identity`() {
            amplitude.add(plugin).add(
                object : Plugin {
                    override val type = Plugin.Type.Before
                    override lateinit var amplitude: Amplitude

                    override fun execute(event: BaseEvent): BaseEvent = error("pipeline failed")
                },
            )

            assertThrows(IllegalStateException::class.java) {
                amplitude.identify(Identify().set("plan", "paid"), signIn)
            }

            assertEquals(listOf("user:user", "device:device", "identity"), callbacks)
            assertEquals(mapOf("plan" to "paid"), identities.single().userProperties)
        }

        @Test
        fun `plugin removed by a legacy id callback is not notified afterwards`() {
            val late = mutableListOf<AnalyticsIdentity>()
            val removable =
                object : UniversalPlugin {
                    override fun onIdentityChanged(identity: AnalyticsIdentity) {
                        late += identity
                    }
                }
            amplitude.add(
                object : Plugin {
                    override val type = Plugin.Type.Before
                    override lateinit var amplitude: Amplitude

                    override fun onUserIdChanged(userId: String?) {
                        amplitude.remove(removable)
                    }
                },
            ).add(removable)

            amplitude.identify(Identify().set("plan", "paid"), signIn)

            assertTrue(late.isEmpty())
        }

        private fun destination(onEvent: (BaseEvent) -> Unit) =
            object : Plugin {
                override val type = Plugin.Type.Destination
                override lateinit var amplitude: Amplitude

                override fun execute(event: BaseEvent): BaseEvent {
                    onEvent(event)
                    return event
                }
            }
    }

    @Nested
    inner class Reset {
        @Test
        fun `anonymous reset skips user callback but not device identity or reset callbacks`() {
            amplitude.add(plugin)

            amplitude.reset()

            assertEquals(listOf("device:${amplitude.getDeviceId()}", "identity", "reset"), callbacks)
        }

        @Test
        fun `identified reset notifies changed user and one atomic identity`() {
            amplitude.setUserId("user")
            amplitude.add(plugin)

            amplitude.reset()

            assertEquals(listOf("user:null", "device:${amplitude.getDeviceId()}", "identity", "reset"), callbacks)
        }
    }
}
