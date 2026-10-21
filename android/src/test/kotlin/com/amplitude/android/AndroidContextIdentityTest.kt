package com.amplitude.android

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.amplitude.android.plugins.AndroidContextPlugin
import com.amplitude.android.utilities.setupMockAndroidContext
import com.amplitude.core.Amplitude
import com.amplitude.core.events.BaseEvent
import com.amplitude.core.platform.Plugin
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

@RunWith(RobolectricTestRunner::class)
class AndroidContextIdentityTest {
    @Test
    fun `tracked events keep user and device ids from the same identity`() {
        val amplitude = amplitude()
        amplitude.setIdentity("a", "a")
        val ids = ConcurrentLinkedQueue<Pair<String?, String?>>()
        amplitude.add(recorder { event ->
            if (event.eventType == "paired") ids += event.userId to event.deviceId
        })
        val start = CountDownLatch(1)
        val finished = CountDownLatch(3)
        val failures = ConcurrentLinkedQueue<Throwable>()

        repeat(2) { writer ->
            thread {
                try {
                    start.await()
                    repeat(1_000) { index ->
                        val id = if ((writer + index) % 2 == 0) "a" else "b"
                        amplitude.setIdentity(id, id)
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
                start.await()
                repeat(2_000) { amplitude.track("paired") }
            } catch (failure: Throwable) {
                failures += failure
            } finally {
                finished.countDown()
            }
        }
        start.countDown()

        assertTrue(finished.await(20, TimeUnit.SECONDS))
        assertTrue(failures.isEmpty())
        assertEquals(2_000, ids.size)
        assertTrue(ids.all { (userId, deviceId) -> userId == deviceId && userId in setOf("a", "b") })
    }

    @Test
    fun `explicit event ids are preserved while missing ids are enriched`() {
        val amplitude = amplitude()
        amplitude.setIdentity("current-user", "current-device")
        val events = mutableListOf<BaseEvent>()
        amplitude.add(recorder { events += it })

        amplitude.track(BaseEvent().apply { eventType = "both"; userId = "event-user"; deviceId = "event-device" })
        amplitude.track(BaseEvent().apply { eventType = "user"; userId = "event-user" })
        amplitude.track(BaseEvent().apply { eventType = "device"; deviceId = "event-device" })

        assertEquals(listOf("event-user" to "event-device", "event-user" to "current-device", "current-user" to "event-device"),
            events.map { it.userId to it.deviceId })
    }

    private fun amplitude(): Amplitude {
        setupMockAndroidContext()
        val configuration =
            Configuration(
                apiKey = "test-api-key",
                context = ApplicationProvider.getApplicationContext<Context>(),
                autocapture = emptySet(),
                offline = true,
                enableDiagnostics = false,
            )
        return SynchronousAmplitude(configuration).add(AndroidContextPlugin())
    }

    private fun recorder(block: (BaseEvent) -> Unit): Plugin =
        object : Plugin {
            override val type = Plugin.Type.Destination
            override lateinit var amplitude: Amplitude

            override fun execute(event: BaseEvent): BaseEvent {
                block(event)
                return event
            }
        }

    private class SynchronousAmplitude(configuration: Configuration) : Amplitude(configuration) {
        override fun build(): Deferred<Boolean> = CompletableDeferred(true)
    }
}
