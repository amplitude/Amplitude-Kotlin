package com.amplitude.android

import android.app.Application
import android.content.Context
import com.amplitude.android.plugins.AndroidLifecyclePlugin
import io.mockk.mockk
import io.mockk.mockkStatic
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ConfigurationTest {
    private var context: Context? = null

    @BeforeEach
    fun setUp() {
        context = mockk<Application>(relaxed = true)
        mockkStatic(AndroidLifecyclePlugin::class)
    }

    @Test
    fun configuration_init_isValid() {
        val configuration = Configuration("test-apikey", context!!)
        Assertions.assertTrue(configuration.isValid())
    }

    @Test
    fun configuration_allows_propertyUpdate() {
        val configuration = Configuration("test-apikey", context!!)
        Assertions.assertTrue(configuration.trackingSessionEvents)
        Assertions.assertFalse(configuration.trackingAppLifecycleEvents)
        Assertions.assertFalse(configuration.trackingDeepLinks)
        Assertions.assertFalse(configuration.trackingScreenViews)
        configuration.trackingSessionEvents = false
        configuration.trackingAppLifecycleEvents = true
        configuration.trackingDeepLinks = true
        configuration.trackingScreenViews = true
        Assertions.assertFalse(configuration.trackingSessionEvents)
        Assertions.assertTrue(configuration.trackingAppLifecycleEvents)
        Assertions.assertTrue(configuration.trackingDeepLinks)
        Assertions.assertTrue(configuration.trackingScreenViews)
    }
}
