package com.amplitude.android.utilities

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.NetworkInfo
import com.amplitude.common.Logger
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class AndroidNetworkConnectivityCheckerTest {

    private lateinit var context: Context
    private lateinit var connectivityManager: ConnectivityManager
    private lateinit var networkCapabilities: NetworkCapabilities
    private lateinit var networkInfo: NetworkInfo
    private lateinit var logger: Logger
    private lateinit var networkConnectivityChecker: AndroidNetworkConnectivityChecker

    @BeforeEach
    fun setUp() {
        context = mockk()
        connectivityManager = mockk()
        networkCapabilities = mockk()
        networkInfo = mockk()
        logger = mockk(relaxed = true)

        every { context.getSystemService(Context.CONNECTIVITY_SERVICE) } returns connectivityManager
        every { context.checkCallingOrSelfPermission("android.permission.ACCESS_NETWORK_STATE") } returns 0
        networkConnectivityChecker = AndroidNetworkConnectivityChecker(context, logger)
    }

    @Test
    fun `should return true when connected to network on devices with API 23 and above`() {
        every { connectivityManager.activeNetwork } returns mockk()
        every { connectivityManager.getNetworkCapabilities(any()) } returns networkCapabilities
        every { networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) } returns true
        networkConnectivityChecker.isMarshmallowAndAbove = true

        assertTrue(networkConnectivityChecker.isConnected())
    }

    @Test
    fun `should return false when not connected to network on devices with API 23 and above`() {
        every { connectivityManager.activeNetwork } returns null
        networkConnectivityChecker.isMarshmallowAndAbove = true

        assertFalse(networkConnectivityChecker.isConnected())
    }

    @Test
    fun `should return true when connected to network devices with API lower than 23`() {
        every { connectivityManager.activeNetworkInfo } returns networkInfo
        every { networkInfo.isConnectedOrConnecting } returns true
        networkConnectivityChecker.isMarshmallowAndAbove = false

        assertTrue(networkConnectivityChecker.isConnected())
    }

    @Test
    fun `should return false when not connected to network devices with API lower than 23`() {
        every { connectivityManager.activeNetworkInfo } returns null
        networkConnectivityChecker.isMarshmallowAndAbove = false

        assertFalse(networkConnectivityChecker.isConnected())
    }
}
