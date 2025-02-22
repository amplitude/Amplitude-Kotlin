package com.amplitude.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class ConfigurationTest {

    @Test
    fun `test valid configuration`() {
        val configuration = Configuration("test-apikey")
        assertTrue(configuration.isValid())
    }

    @Test
    fun `test invalid api key`() {
        val configuration = Configuration("")
        assertFalse(configuration.isValid())
    }

    @Test
    fun `test invalid flush size`() {
        val configuration = Configuration("test-apikey", flushQueueSize = 0)
        assertFalse(configuration.isValid())
    }

    @Test
    fun `test api host`() {
        var configuration = Configuration("test-apikey")
        assertEquals("https://api2.amplitude.com/2/httpapi", configuration.getApiHost())

        configuration = Configuration("test-apikey", serverZone = ServerZone.EU)
        assertEquals("https://api.eu.amplitude.com/2/httpapi", configuration.getApiHost())

        configuration = Configuration("test-apikey", serverUrl = "https://custom.amplitude.com")
        assertEquals("https://custom.amplitude.com", configuration.getApiHost())

        configuration = Configuration(
            "test-apikey",
            serverUrl = "https://custom.amplitude.com",
            serverZone = ServerZone.EU
        )
        assertEquals("https://custom.amplitude.com", configuration.getApiHost())

        configuration = Configuration(
            "test-apikey",
            serverUrl = "https://custom.amplitude.com",
            useBatch = true
        )
        assertEquals("https://custom.amplitude.com", configuration.getApiHost())

        configuration = Configuration("test-apikey", useBatch = true)
        assertEquals("https://api2.amplitude.com/batch", configuration.getApiHost())

        configuration = Configuration("test-apikey", useBatch = true, serverZone = ServerZone.EU)
        assertEquals("https://api.eu.amplitude.com/batch", configuration.getApiHost())
    }
}
