package com.amplitude.core.platform

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

class MediatorTest {
    private val mediator = Mediator(CopyOnWriteArrayList())

    /**
     * Fake [DestinationPlugin] that does work on [flush] for 1 second
     */
    private class FakeDestinationPlugin : DestinationPlugin() {
        var workDone = false

        override fun flush() {
            super.flush()
            println("$this start work ${System.currentTimeMillis()} ${Thread.currentThread().name}")
            Thread.sleep(1_000)
            println("$this end work ${System.currentTimeMillis()} ${Thread.currentThread().name}")
            workDone = true
        }
    }

    @Test
    @Timeout(2, unit = TimeUnit.SECONDS)
    fun `multiple threads that call flush on destination plugin`() {
        val fakeDestinationPlugins = List(10) { FakeDestinationPlugin() }
        fakeDestinationPlugins.forEach {
            mediator.add(it)
        }

        // simulate 10 threads executing flush on 10 different [DestinationPlugin]s
        val executor = Executors.newFixedThreadPool(10)
        fakeDestinationPlugins.forEach {
            executor.submit {
                it.flush()
            }
        }
        executor.shutdown()
        executor.awaitTermination(2, TimeUnit.SECONDS)

        assertTrue {
            fakeDestinationPlugins.all { it.workDone }
        }
    }

    @Test
    @Timeout(2, unit = TimeUnit.SECONDS)
    fun `two threads that call flush on destination plugin`() {
        val fakeDestinationPlugins = List(2) { FakeDestinationPlugin() }
        fakeDestinationPlugins.forEach {
            mediator.add(it)
        }

        // simulate 2 threads executing flush on 2 different DestinationPlugins
        val t1 = thread {
            fakeDestinationPlugins[0].flush()
        }
        val t2 = thread {
            fakeDestinationPlugins[1].flush()
        }
        t1.join()
        t2.join()

        assertTrue {
            fakeDestinationPlugins.all { it.workDone }
        }
    }
}
