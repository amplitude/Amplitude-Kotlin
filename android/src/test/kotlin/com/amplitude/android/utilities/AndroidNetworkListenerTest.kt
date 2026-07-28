package com.amplitude.android.utilities

import android.content.Context
import android.net.ConnectivityManager
import android.net.ConnectivityManager.NetworkCallback
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import com.amplitude.common.Logger
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.slot
import io.mockk.verify
import org.junit.Before
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.fail
import java.lang.ref.WeakReference
import kotlin.test.Test

class AndroidNetworkListenerTest {
    private val fakeContext = mockk<Context>(relaxed = true)
    private val fakeLogger = mockk<Logger>(relaxed = true)
    private val fakeConnectivityManager = mockk<ConnectivityManager>(relaxed = true)

    @Before
    fun setup() {
        every {
            fakeContext.getSystemService(Context.CONNECTIVITY_SERVICE)
        } returns fakeConnectivityManager

        mockkConstructor(NetworkRequest.Builder::class)
        every {
            anyConstructed<NetworkRequest.Builder>().addCapability(any()).build()
        } returns mockk()
    }

    @Test
    fun `setup network callback should notify states`() {
        val networkChangeCallback =
            object : AndroidNetworkListener.NetworkChangeCallback {
                var available = false

                override fun onNetworkAvailable() {
                    available = true
                }

                override fun onNetworkUnavailable() {
                    available = false
                }
            }
        val networkListener =
            AndroidNetworkListener(
                context = fakeContext,
                logger = fakeLogger,
                networkCallback = networkChangeCallback,
            )

        networkListener.setupNetworkCallback()
        val networkCallbackSlot = slot<NetworkCallback>()
        verify {
            fakeConnectivityManager.registerNetworkCallback(
                any<NetworkRequest>(),
                capture(networkCallbackSlot),
            )
        }
        val networkCallback = networkCallbackSlot.captured
        val network = mockk<Network>()
        val availableCapability =
            mockk<NetworkCapabilities> {
                every { hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) } returns true
                every { hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) } returns true
            }
        val unavailableCapability =
            mockk<NetworkCapabilities> {
                every { hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) } returns false
                every { hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) } returns false
            }
        every { fakeConnectivityManager.getNetworkCapabilities(network) } returns availableCapability

        // available: true, blocked: false
        networkCallback.onAvailable(network)
        assertTrue(networkChangeCallback.available)

        // available: true, blocked: false -> true
        networkCallback.onBlockedStatusChanged(network, true)
        assertFalse(networkChangeCallback.available)

        // available: true, blocked: true (nothing toggled)
        networkCallback.onCapabilitiesChanged(network, availableCapability)
        assertFalse(networkChangeCallback.available) // still blocked

        // available: true, blocked: true -> false
        networkCallback.onBlockedStatusChanged(network, false)
        assertTrue(networkChangeCallback.available)

        // available: true -> false, blocked: false
        networkCallback.onCapabilitiesChanged(network, unavailableCapability)
        assertFalse(networkChangeCallback.available) // now unavailable

        // available: false -> true, blocked: false
        networkCallback.onCapabilitiesChanged(network, availableCapability)
        assertTrue(networkChangeCallback.available)

        // available: true -> false, blocked: false
        networkCallback.onLost(network)
        assertFalse(networkChangeCallback.available)

        // available: false -> true, blocked: false
        networkCallback.onCapabilitiesChanged(network, availableCapability)
        assertTrue(networkChangeCallback.available) // available again

        // available: true, blocked: false (new state)
        networkCallback.onAvailable(network)
        assertTrue(networkChangeCallback.available)

        // N/A
        networkCallback.onUnavailable()
        assertFalse(networkChangeCallback.available)
    }

    @Test
    fun `stop listening should unregister the network callback`() {
        val networkListener =
            AndroidNetworkListener(
                context = fakeContext,
                logger = fakeLogger,
                networkCallback = noopNetworkChangeCallback(),
            )

        networkListener.setupNetworkCallback()
        val networkCallbackSlot = slot<NetworkCallback>()
        verify {
            fakeConnectivityManager.registerNetworkCallback(
                any<NetworkRequest>(),
                capture(networkCallbackSlot),
            )
        }

        networkListener.stopListening()

        verify {
            fakeConnectivityManager.unregisterNetworkCallback(networkCallbackSlot.captured)
        }
    }

    @Test
    fun `stop listening twice should unregister only once`() {
        val networkListener =
            AndroidNetworkListener(
                context = fakeContext,
                logger = fakeLogger,
                networkCallback = noopNetworkChangeCallback(),
            )
        networkListener.setupNetworkCallback()

        networkListener.stopListening()
        networkListener.stopListening()

        verify(exactly = 1) {
            fakeConnectivityManager.unregisterNetworkCallback(any<NetworkCallback>())
        }
    }

    @Test
    fun `setup after stop should register again`() {
        val networkListener =
            AndroidNetworkListener(
                context = fakeContext,
                logger = fakeLogger,
                networkCallback = noopNetworkChangeCallback(),
            )

        networkListener.setupNetworkCallback()
        networkListener.stopListening()
        networkListener.setupNetworkCallback()

        verify(exactly = 2) {
            fakeConnectivityManager.registerNetworkCallback(
                any<NetworkRequest>(),
                any<NetworkCallback>(),
            )
        }
    }

    @Test
    fun `setup network callback twice should register only once`() {
        val networkListener =
            AndroidNetworkListener(
                context = fakeContext,
                logger = fakeLogger,
                networkCallback = noopNetworkChangeCallback(),
            )

        networkListener.setupNetworkCallback()
        networkListener.setupNetworkCallback()

        verify(exactly = 1) {
            fakeConnectivityManager.registerNetworkCallback(
                any<NetworkRequest>(),
                any<NetworkCallback>(),
            )
        }
    }

    @Test
    fun `registered network callback should not pin a garbage collected listener`() {
        val (registeredCallback, delegateRef) = registerAndAbandonListener()

        awaitCollected(delegateRef)

        // The first event after collection should release the system registration
        // without notifying anyone.
        registeredCallback.onAvailable(mockk(relaxed = true))

        verify {
            fakeConnectivityManager.unregisterNetworkCallback(registeredCallback)
        }
    }

    @Test
    fun `starting a new listener should unregister abandoned network callbacks`() {
        val (abandonedCallback, delegateRef) = registerAndAbandonListener()

        awaitCollected(delegateRef)

        val networkListener =
            AndroidNetworkListener(
                context = fakeContext,
                logger = fakeLogger,
                networkCallback = noopNetworkChangeCallback(),
            )
        networkListener.setupNetworkCallback()

        verify {
            fakeConnectivityManager.unregisterNetworkCallback(abandonedCallback)
        }
    }

    @Test
    fun `starting a new listener should not unregister callbacks of live listeners`() {
        val liveNetworkChangeCallback = noopNetworkChangeCallback()
        val liveListener =
            AndroidNetworkListener(
                context = fakeContext,
                logger = fakeLogger,
                networkCallback = liveNetworkChangeCallback,
            )
        liveListener.setupNetworkCallback()
        val liveCallback = lastRegisteredNetworkCallback()

        val (abandonedCallback, delegateRef) = registerAndAbandonListener()
        awaitCollected(delegateRef)

        val newListener =
            AndroidNetworkListener(
                context = fakeContext,
                logger = fakeLogger,
                networkCallback = noopNetworkChangeCallback(),
            )
        newListener.setupNetworkCallback()

        // The sweep frees only the abandoned registration, sparing the live one.
        verify {
            fakeConnectivityManager.unregisterNetworkCallback(abandonedCallback)
        }
        verify(exactly = 0) {
            fakeConnectivityManager.unregisterNetworkCallback(liveCallback)
        }
        liveListener.stopListening()
        verify(exactly = 1) {
            fakeConnectivityManager.unregisterNetworkCallback(liveCallback)
        }
    }

    /**
     * Registers a listener and drops every strong reference to it and its
     * [AndroidNetworkListener.NetworkChangeCallback], simulating an app abandoning an
     * Amplitude instance without calling [AndroidNetworkListener.stopListening].
     */
    private fun registerAndAbandonListener(): Pair<NetworkCallback, WeakReference<AndroidNetworkListener.NetworkChangeCallback>> {
        val networkChangeCallback =
            object : AndroidNetworkListener.NetworkChangeCallback {
                override fun onNetworkAvailable() {
                    fail<Unit>("Collected delegate should not be notified")
                }

                override fun onNetworkUnavailable() {
                    fail<Unit>("Collected delegate should not be notified")
                }
            }
        val networkListener =
            AndroidNetworkListener(
                context = fakeContext,
                logger = fakeLogger,
                networkCallback = networkChangeCallback,
            )
        networkListener.setupNetworkCallback()
        return lastRegisteredNetworkCallback() to WeakReference(networkChangeCallback)
    }

    private fun lastRegisteredNetworkCallback(): NetworkCallback {
        val capturedCallbacks = mutableListOf<NetworkCallback>()
        verify {
            fakeConnectivityManager.registerNetworkCallback(
                any<NetworkRequest>(),
                capture(capturedCallbacks),
            )
        }
        return capturedCallbacks.last()
    }

    private fun awaitCollected(ref: WeakReference<*>) {
        repeat(100) {
            if (ref.get() == null) return
            System.gc()
            Thread.sleep(10)
        }
        fail<Unit>("Reference was not garbage collected")
    }

    private fun noopNetworkChangeCallback() =
        object : AndroidNetworkListener.NetworkChangeCallback {
            override fun onNetworkAvailable() {}

            override fun onNetworkUnavailable() {}
        }
}
