package com.amplitude.android.utilities

import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.Test
import java.lang.ref.WeakReference

class AndroidNetworkCallbackTest {
    private val connectivityManager = mockk<ConnectivityManager>(relaxed = true)
    private val network = mockk<Network>()
    private val delegate = RecordingNetworkChangeCallback()
    private val callback = AndroidNetworkCallback(delegate, connectivityManager)

    @Test
    fun `register should register with the connectivity manager`() {
        val networkRequest = mockk<NetworkRequest>()

        callback.register(networkRequest)

        verify { connectivityManager.registerNetworkCallback(networkRequest, callback) }
    }

    @Test
    fun `unregister should unregister from the connectivity manager`() {
        callback.register(mockk())

        callback.unregister()

        verify { connectivityManager.unregisterNetworkCallback(callback) }
    }

    @Test
    fun `onAvailable should notify the delegate based on capabilities`() {
        every { connectivityManager.getNetworkCapabilities(network) } returns capabilities(internet = true)

        callback.onAvailable(network)

        assertEquals(listOf(true), delegate.notifications)
    }

    @Test
    fun `onAvailable should notify unavailable when capabilities lack internet`() {
        every { connectivityManager.getNetworkCapabilities(network) } returns capabilities(internet = false)

        callback.onAvailable(network)

        assertEquals(listOf(false), delegate.notifications)
    }

    @Test
    fun `onAvailable should assume available when capabilities are unknown`() {
        every { connectivityManager.getNetworkCapabilities(network) } returns null

        callback.onAvailable(network)

        assertEquals(listOf(true), delegate.notifications)
    }

    @Test
    fun `onUnavailable should notify the delegate`() {
        callback.onUnavailable()

        assertEquals(listOf(false), delegate.notifications)
    }

    @Test
    fun `onLost should notify the delegate for the tracked network`() {
        every { connectivityManager.getNetworkCapabilities(network) } returns capabilities(internet = true)
        callback.onAvailable(network)

        callback.onLost(network)

        assertEquals(listOf(true, false), delegate.notifications)
    }

    @Test
    fun `events before onAvailable should be ignored`() {
        callback.onLost(network)
        callback.onCapabilitiesChanged(network, capabilities(internet = true))
        callback.onBlockedStatusChanged(network, true)

        assertEquals(emptyList<Boolean>(), delegate.notifications)
    }

    @Test
    fun `network event should unregister the callback when the delegate is collected`() {
        val (abandonedCallback, delegateRef) = registerAndAbandonCallback()

        awaitCollected(delegateRef)
        abandonedCallback.onUnavailable()

        verify { connectivityManager.unregisterNetworkCallback(abandonedCallback) }
    }

    @Test
    fun `unregisterAbandonedCallbacks should unregister only collected delegates`() {
        val liveCallback = AndroidNetworkCallback(delegate, connectivityManager)
        liveCallback.register(mockk())
        val (abandonedCallback, delegateRef) = registerAndAbandonCallback()

        awaitCollected(delegateRef)
        AndroidNetworkCallback.unregisterAbandonedCallbacks()

        verify { connectivityManager.unregisterNetworkCallback(abandonedCallback) }
        verify(exactly = 0) { connectivityManager.unregisterNetworkCallback(liveCallback) }
    }

    /**
     * Registers a callback whose delegate has no remaining strong references, simulating
     * an abandoned Amplitude instance.
     */
    private fun registerAndAbandonCallback(): Pair<AndroidNetworkCallback, WeakReference<AndroidNetworkListener.NetworkChangeCallback>> {
        val abandonedDelegate = RecordingNetworkChangeCallback()
        val abandonedCallback = AndroidNetworkCallback(abandonedDelegate, connectivityManager)
        abandonedCallback.register(mockk())
        return abandonedCallback to WeakReference(abandonedDelegate)
    }

    private fun awaitCollected(ref: WeakReference<*>) {
        repeat(100) {
            if (ref.get() == null) return
            System.gc()
            Thread.sleep(10)
        }
        fail<Unit>("Reference was not garbage collected")
    }

    private fun capabilities(internet: Boolean): NetworkCapabilities =
        mockk {
            every { hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) } returns internet
            every { hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) } returns internet
        }

    private class RecordingNetworkChangeCallback : AndroidNetworkListener.NetworkChangeCallback {
        /** One entry per notification: true for available, false for unavailable. */
        val notifications = mutableListOf<Boolean>()

        override fun onNetworkAvailable() {
            notifications.add(true)
        }

        override fun onNetworkUnavailable() {
            notifications.add(false)
        }
    }
}
