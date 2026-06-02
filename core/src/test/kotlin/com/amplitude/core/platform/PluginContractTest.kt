package com.amplitude.core.platform

import com.amplitude.core.Amplitude
import com.amplitude.core.events.BaseEvent
import com.amplitude.core.utils.FakeAmplitude
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.RepeatedTest
import org.junit.jupiter.api.Test
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class PluginContractTest {
    @Test
    fun `state callbacks fan out to timeline and observe plugins`() {
        val amplitude = FakeAmplitude()
        val timeline = RecordingPlugin("timeline")
        val observe = RecordingObservePlugin("observe")

        amplitude.add(timeline)
        amplitude.add(observe)

        amplitude.setUserId("user-1")
        amplitude.setDeviceId("device-1")
        amplitude.optOut = true
        amplitude.reset()

        assertEquals(listOf("user-1", null), timeline.userIds)
        assertEquals(2, timeline.deviceIds.size)
        assertEquals("device-1", timeline.deviceIds.first())
        assertEquals(listOf(true), timeline.optOuts)
        assertEquals(1, timeline.resets)

        assertEquals(listOf("user-1", null), observe.userIds)
        assertEquals(2, observe.deviceIds.size)
        assertEquals("device-1", observe.deviceIds.first())
        assertEquals(listOf(true), observe.optOuts)
        assertEquals(1, observe.resets)
    }

    @Test
    fun `reset uses one plugin snapshot for identity and reset callbacks`() {
        val amplitude = FakeAmplitude()
        val late = RecordingPlugin("late")
        val mutator =
            object : Plugin {
                override val type: Plugin.Type = Plugin.Type.Before
                override val name: String = "mutator"
                override lateinit var amplitude: Amplitude

                override fun execute(event: BaseEvent): BaseEvent = event

                override fun onUserIdChanged(userId: String?) {
                    if (userId == null) {
                        amplitude.add(late)
                    }
                }
            }

        amplitude.add(mutator)
        amplitude.reset()

        assertTrue(late.userIds.isEmpty())
        assertTrue(late.deviceIds.isEmpty())
        assertEquals(0, late.resets)
        assertSame(late, amplitude.findPlugin<RecordingPlugin>())
    }

    @Test
    fun `throwing plugins do not stop later callbacks or later plugins`() {
        val amplitude = FakeAmplitude()
        val thrower = ThrowingObservePlugin()
        val recorder = RecordingObservePlugin("recorder")

        amplitude.add(thrower)
        amplitude.add(recorder)

        amplitude.reset()

        assertEquals(listOf<String?>(null), recorder.userIds)
        assertEquals(1, recorder.deviceIds.size)
        assertEquals(1, recorder.resets)
        assertEquals(1, thrower.deviceCallbackAttempts)
    }

    @Test
    fun `reentrant userId callback does not get overwritten by outer mutation`() {
        val amplitude = FakeAmplitude()
        val completed = CountDownLatch(1)
        val plugin =
            object : ObservePlugin() {
                override lateinit var amplitude: Amplitude
                private var reentered = false

                override fun onUserIdChanged(userId: String?) {
                    if (!reentered && userId == "outer") {
                        reentered = true
                        amplitude.setUserId("inner")
                    }
                    if (userId == "inner") {
                        completed.countDown()
                    }
                }

                override fun onDeviceIdChanged(deviceId: String?) {}
            }

        amplitude.add(plugin)
        amplitude.setUserId("outer")

        assertTrue(completed.await(5, TimeUnit.SECONDS))
        assertEquals("inner", amplitude.store.userId)
    }

    @Test
    fun `named plugin add replaces existing plugin across stores`() {
        val amplitude = FakeAmplitude()
        val first = NamedPlugin("shared")
        val replacement = NamedObservePlugin("shared")

        amplitude.add(first)
        amplitude.add(replacement)

        assertTrue(first.tornDown)
        assertNull(amplitude.findPlugin<NamedPlugin>())
        assertSame(replacement, amplitude.findPlugin<NamedObservePlugin>())
    }

    @Test
    fun `bare observe type plugin is stored in the timeline`() {
        val amplitude = FakeAmplitude()
        val plugin = RecordingPlugin("bare-observe", Plugin.Type.Observe)

        amplitude.add(plugin)
        amplitude.setUserId("user-1")

        assertEquals(listOf<String?>("user-1"), plugin.userIds)
        assertSame(plugin, amplitude.findPlugin<RecordingPlugin>())
    }

    @RepeatedTest(10)
    fun `concurrent add setUserId and reset do not crash or deadlock`() {
        val amplitude = FakeAmplitude()
        val errors = Collections.synchronizedList(mutableListOf<Throwable>())
        val barrier = CyclicBarrier(3)
        val done = CountDownLatch(3)

        fun launch(block: () -> Unit) {
            Thread {
                try {
                    barrier.await()
                    block()
                } catch (t: Throwable) {
                    errors += t
                } finally {
                    done.countDown()
                }
            }.start()
        }

        launch {
            repeat(50) { amplitude.add(RecordingObservePlugin("observe-$it")) }
        }
        launch {
            repeat(50) { amplitude.setUserId("user-$it") }
        }
        launch {
            repeat(50) { amplitude.reset() }
        }

        assertTrue(done.await(5, TimeUnit.SECONDS))
        assertTrue(errors.isEmpty(), errors.joinToString { it.message.orEmpty() })
    }
}

private open class RecordingPlugin(
    override val name: String?,
    override val type: Plugin.Type = Plugin.Type.Before,
) : Plugin {
    override lateinit var amplitude: Amplitude
    val userIds = Collections.synchronizedList(mutableListOf<String?>())
    val deviceIds = Collections.synchronizedList(mutableListOf<String?>())
    val optOuts = Collections.synchronizedList(mutableListOf<Boolean>())
    private val resetCount = AtomicInteger()
    val resets: Int get() = resetCount.get()

    override fun execute(event: BaseEvent): BaseEvent = event

    override fun onUserIdChanged(userId: String?) {
        userIds.add(userId)
    }

    override fun onDeviceIdChanged(deviceId: String?) {
        deviceIds.add(deviceId)
    }

    override fun onOptOutChanged(optOut: Boolean) {
        optOuts.add(optOut)
    }

    override fun onReset() {
        resetCount.incrementAndGet()
    }
}

private class RecordingObservePlugin(
    override val name: String?,
) : ObservePlugin() {
    override lateinit var amplitude: Amplitude
    val userIds = Collections.synchronizedList(mutableListOf<String?>())
    val deviceIds = Collections.synchronizedList(mutableListOf<String?>())
    val optOuts = Collections.synchronizedList(mutableListOf<Boolean>())
    private val resetCount = AtomicInteger()
    val resets: Int get() = resetCount.get()

    override fun onUserIdChanged(userId: String?) {
        userIds.add(userId)
    }

    override fun onDeviceIdChanged(deviceId: String?) {
        deviceIds.add(deviceId)
    }

    override fun onOptOutChanged(optOut: Boolean) {
        optOuts.add(optOut)
    }

    override fun onReset() {
        resetCount.incrementAndGet()
    }
}

private class ThrowingObservePlugin : ObservePlugin() {
    override val name: String = "thrower"
    override lateinit var amplitude: Amplitude
    var deviceCallbackAttempts = 0

    override fun onUserIdChanged(userId: String?) {
        throw RuntimeException("user")
    }

    override fun onDeviceIdChanged(deviceId: String?) {
        deviceCallbackAttempts += 1
        throw RuntimeException("device")
    }

    override fun onReset() {
        throw RuntimeException("reset")
    }
}

private class NamedPlugin(override val name: String) : Plugin {
    override val type: Plugin.Type = Plugin.Type.Before
    override lateinit var amplitude: Amplitude
    var tornDown = false

    override fun execute(event: BaseEvent): BaseEvent = event

    override fun teardown() {
        tornDown = true
    }
}

private class NamedObservePlugin(override val name: String) : ObservePlugin() {
    override lateinit var amplitude: Amplitude

    override fun onUserIdChanged(userId: String?) {}

    override fun onDeviceIdChanged(deviceId: String?) {}
}
