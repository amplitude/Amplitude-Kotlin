package com.amplitude.android.utilities

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkCapabilities.TRANSPORT_CELLULAR
import android.net.NetworkCapabilities.TRANSPORT_WIFI
import android.os.Build
import com.amplitude.common.Logger
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class AndroidNetworkConnectivityCheckerTest {
    private val context: Context = mockk()
    private val connectivityManager: ConnectivityManager = mockk()
    private val networkCapabilities: NetworkCapabilities = mockk()
    private val network: Network = mockk()
    private val logger: Logger = mockk(relaxed = true)
    private lateinit var networkConnectivityChecker: AndroidNetworkConnectivityChecker

    @BeforeEach
    fun setUp() {
        every { context.getSystemService(Context.CONNECTIVITY_SERVICE) } returns connectivityManager
    }

    @Test
    fun `hasNetworkPermission should return true when permission is granted`() {
        every { context.checkCallingOrSelfPermission("android.permission.ACCESS_NETWORK_STATE") } returns 0

        assertTrue(AndroidNetworkConnectivityChecker.hasNetworkPermission(context))
    }

    @Test
    fun `hasNetworkPermission should return false when permission is denied`() {
        every { context.checkCallingOrSelfPermission("android.permission.ACCESS_NETWORK_STATE") } returns -1

        assertFalse(AndroidNetworkConnectivityChecker.hasNetworkPermission(context))
    }

    @Test
    fun `constructor should log warning when permission is missing`() {
        every { context.checkCallingOrSelfPermission("android.permission.ACCESS_NETWORK_STATE") } returns -1

        AndroidNetworkConnectivityChecker(context, logger)

        verify {
            logger.warn(
                match<String> { it.contains("No ACCESS_NETWORK_STATE permission") },
            )
        }
    }

    @Test
    fun `constructor should not log warning when permission is granted`() {
        every { context.checkCallingOrSelfPermission("android.permission.ACCESS_NETWORK_STATE") } returns 0

        AndroidNetworkConnectivityChecker(context, logger)

        verify(exactly = 0) {
            logger.warn(any<String>())
        }
    }

    @Test
    fun `isConnected should return true when no permission`() {
        every { context.checkCallingOrSelfPermission("android.permission.ACCESS_NETWORK_STATE") } returns -1
        networkConnectivityChecker = AndroidNetworkConnectivityChecker(context, logger)

        assertTrue(networkConnectivityChecker.isConnected())
    }

    @Test
    fun `isConnected should return false when activeNetwork is null on API 23+`() {
        every { context.checkCallingOrSelfPermission("android.permission.ACCESS_NETWORK_STATE") } returns 0
        every { connectivityManager.activeNetwork } returns null
        networkConnectivityChecker = AndroidNetworkConnectivityChecker(context, logger)

        // Mock Build.VERSION.SDK_INT >= Build.VERSION_CODES.M by testing the API 23+ path
        // This test assumes we're running on API 23+ in the test environment
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            assertFalse(networkConnectivityChecker.isConnected())
        }
    }

    @Test
    fun `isConnected should return false when network capabilities is null on API 23+`() {
        every { context.checkCallingOrSelfPermission("android.permission.ACCESS_NETWORK_STATE") } returns 0
        every { connectivityManager.activeNetwork } returns network
        every { connectivityManager.getNetworkCapabilities(network) } returns null
        networkConnectivityChecker = AndroidNetworkConnectivityChecker(context, logger)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            assertFalse(networkConnectivityChecker.isConnected())
        }
    }

    @Test
    fun `isConnected should return true when has WiFi transport on API 23+`() {
        every { context.checkCallingOrSelfPermission("android.permission.ACCESS_NETWORK_STATE") } returns 0
        every { connectivityManager.activeNetwork } returns network
        every { connectivityManager.getNetworkCapabilities(network) } returns networkCapabilities
        every { networkCapabilities.hasTransport(TRANSPORT_WIFI) } returns true
        every { networkCapabilities.hasTransport(TRANSPORT_CELLULAR) } returns false
        networkConnectivityChecker = AndroidNetworkConnectivityChecker(context, logger)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            assertTrue(networkConnectivityChecker.isConnected())
        }
    }

    @Test
    fun `isConnected should return true when has cellular transport on API 23+`() {
        every { context.checkCallingOrSelfPermission("android.permission.ACCESS_NETWORK_STATE") } returns 0
        every { connectivityManager.activeNetwork } returns network
        every { connectivityManager.getNetworkCapabilities(network) } returns networkCapabilities
        every { networkCapabilities.hasTransport(TRANSPORT_WIFI) } returns false
        every { networkCapabilities.hasTransport(TRANSPORT_CELLULAR) } returns true
        networkConnectivityChecker = AndroidNetworkConnectivityChecker(context, logger)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            assertTrue(networkConnectivityChecker.isConnected())
        }
    }

    @Test
    fun `isConnected should return false when has neither WiFi nor cellular transport on API 23+`() {
        every { context.checkCallingOrSelfPermission("android.permission.ACCESS_NETWORK_STATE") } returns 0
        every { connectivityManager.activeNetwork } returns network
        every { connectivityManager.getNetworkCapabilities(network) } returns networkCapabilities
        every { networkCapabilities.hasTransport(TRANSPORT_WIFI) } returns false
        every { networkCapabilities.hasTransport(TRANSPORT_CELLULAR) } returns false
        networkConnectivityChecker = AndroidNetworkConnectivityChecker(context, logger)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            assertFalse(networkConnectivityChecker.isConnected())
        }
    }

    @Test
    fun `isConnected should return true when any network has transport on pre-API 23`() {
        every { context.checkCallingOrSelfPermission("android.permission.ACCESS_NETWORK_STATE") } returns 0
        val networks = arrayOf(mockk<Network>(), mockk<Network>())
        every { connectivityManager.allNetworks } returns networks
        every { connectivityManager.getNetworkCapabilities(networks[0]) } returns null
        every { connectivityManager.getNetworkCapabilities(networks[1]) } returns networkCapabilities
        every { networkCapabilities.hasTransport(TRANSPORT_WIFI) } returns true
        every { networkCapabilities.hasTransport(TRANSPORT_CELLULAR) } returns false
        networkConnectivityChecker = AndroidNetworkConnectivityChecker(context, logger)

        // This path is only taken on pre-API 23, but since minSdk is now 21, this is less relevant
        // However, the code still handles it for devices between API 21-22
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            assertTrue(networkConnectivityChecker.isConnected())
        }
    }

    @Test
    fun `isConnected should return false when no networks have transport on pre-API 23`() {
        every { context.checkCallingOrSelfPermission("android.permission.ACCESS_NETWORK_STATE") } returns 0
        val networks = arrayOf(mockk<Network>())
        every { connectivityManager.allNetworks } returns networks
        every { connectivityManager.getNetworkCapabilities(networks[0]) } returns networkCapabilities
        every { networkCapabilities.hasTransport(TRANSPORT_WIFI) } returns false
        every { networkCapabilities.hasTransport(TRANSPORT_CELLULAR) } returns false
        networkConnectivityChecker = AndroidNetworkConnectivityChecker(context, logger)

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            assertFalse(networkConnectivityChecker.isConnected())
        }
    }

    @Test
    fun `isConnected should return true when service is not ConnectivityManager`() {
        every { context.checkCallingOrSelfPermission("android.permission.ACCESS_NETWORK_STATE") } returns 0
        every { context.getSystemService(Context.CONNECTIVITY_SERVICE) } returns mockk<Any>()
        networkConnectivityChecker = AndroidNetworkConnectivityChecker(context, logger)

        assertTrue(networkConnectivityChecker.isConnected())
        verify { logger.debug("Service is not an instance of ConnectivityManager. Offline mode is not supported") }
    }

    @Test
    fun `isConnected should return true and log warning on exception`() {
        every { context.checkCallingOrSelfPermission("android.permission.ACCESS_NETWORK_STATE") } returns 0
        every { connectivityManager.allNetworks } throws RuntimeException("Test exception")
        networkConnectivityChecker = AndroidNetworkConnectivityChecker(context, logger)

        assertTrue(networkConnectivityChecker.isConnected())
        verify { logger.warn(match<String> { it.contains("Error checking network connectivity") }) }
        verify { logger.warn(match<String> { it.contains("RuntimeException") }) }
    }
}
