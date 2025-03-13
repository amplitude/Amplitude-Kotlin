package com.amplitude.core.platform

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

class MediatorTest {
    private val mediator = Mediator(CopyOnWriteArrayList())

    /**
     * Fake [DestinationPlugin] that does work on [flush] for 1 second
     */
    private class FakeDestinationPlugin : DestinationPlugin() {
        var amountOfWorkDone = AtomicInteger()

        override fun flush() {
            super.flush()
            Thread.sleep(1_000)
            amountOfWorkDone.incrementAndGet()
        }
    }

    @Test
    @Timeout(3, unit = TimeUnit.SECONDS)
    fun `two threads that call flush twice on two destination plugins`() {
        val fakeDestinationPlugins = List(2) { FakeDestinationPlugin() }
        fakeDestinationPlugins.forEach {
            mediator.add(it)
        }

        // simulate 2 threads executing flush on 2 different DestinationPlugins
        val work = {
            mediator.applyClosure {
                (it as EventPlugin).flush()
            }
        }
        val t1 = thread {
            work()
        }
        val t2 = thread {
            work()
        }
        t1.join()
        t2.join()

        fakeDestinationPlugins.forEach {
            assertEquals(2, it.amountOfWorkDone.get())
        }
    }
}
