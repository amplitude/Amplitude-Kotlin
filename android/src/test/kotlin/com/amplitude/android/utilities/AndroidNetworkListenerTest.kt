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
import kotlin.test.Test
import org.junit.Before
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue

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
        val networkChangeCallback = object : AndroidNetworkListener.NetworkChangeCallback {
            var available = false
            override fun onNetworkAvailable() {
                available = true
            }

            override fun onNetworkUnavailable() {
                available = false
            }
        }
        val networkListener = AndroidNetworkListener(
            context = fakeContext,
            logger = fakeLogger,
            networkCallback = networkChangeCallback
        )

        networkListener.setupNetworkCallback()
        val networkCallbackSlot = slot<NetworkCallback>()
        verify {
            fakeConnectivityManager.registerNetworkCallback(
                any<NetworkRequest>(), capture(networkCallbackSlot)
            )
        }
        val networkCallback = networkCallbackSlot.captured
        val network = mockk<Network>()
        val availableCapability = mockk<NetworkCapabilities>() {
            every { hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) } returns true
            every { hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) } returns true
        }
        val unavailableCapability = mockk<NetworkCapabilities>() {
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
}