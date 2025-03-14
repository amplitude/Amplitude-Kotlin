package com.amplitude.core.platform

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
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
    fun `call flush twice on two destination plugins`() {
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

    @Test
    @Timeout(5, unit = TimeUnit.SECONDS)
    fun `flush, add a new plugin, and flush again on two destination plugins`() {
        val fakeDestinationPlugin1 = FakeDestinationPlugin()
        val fakeDestinationPlugin2 = FakeDestinationPlugin()

        mediator.add(fakeDestinationPlugin1)

        val work = {
            mediator.applyClosure {
                (it as EventPlugin).flush()
            }
        }

        // flush and add
        val latch = CountDownLatch(2)
        val t1 = thread {
            work()
            latch.countDown()
        }
        val t2 = thread {
            // add plugin 2, work() should catch up with the newly added plugin
            mediator.add(fakeDestinationPlugin2)
            latch.countDown()
        }
        latch.await()

        // flush again
        val t3 = thread {
            work()
        }
        t1.join()
        t2.join()
        t3.join()

        assertEquals(2, fakeDestinationPlugin1.amountOfWorkDone.get())
        assertEquals(2, fakeDestinationPlugin2.amountOfWorkDone.get())
    }
}
