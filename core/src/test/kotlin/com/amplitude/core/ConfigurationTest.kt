package com.amplitude.core

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
}
