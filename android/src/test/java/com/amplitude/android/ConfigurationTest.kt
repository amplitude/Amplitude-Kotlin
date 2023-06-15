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

    @Suppress("DEPRECATION")
    @Test
    fun configuration_allows_propertyUpdate() {
        val configuration = Configuration("test-apikey", context!!)
        Assertions.assertTrue(configuration.trackingSessionEvents)
        Assertions.assertTrue(configuration.defaultTracking.trackingSessionEvents)
        Assertions.assertFalse(configuration.defaultTracking.trackingAppLifecycleEvents)
        Assertions.assertFalse(configuration.defaultTracking.trackingDeepLinks)
        Assertions.assertFalse(configuration.defaultTracking.trackingScreenViews)
        configuration.trackingSessionEvents = false
        configuration.defaultTracking.trackingSessionEvents = false
        configuration.defaultTracking.trackingAppLifecycleEvents = true
        configuration.defaultTracking.trackingDeepLinks = true
        configuration.defaultTracking.trackingScreenViews = true
        Assertions.assertFalse(configuration.trackingSessionEvents)
        Assertions.assertFalse(configuration.defaultTracking.trackingSessionEvents)
        Assertions.assertTrue(configuration.defaultTracking.trackingAppLifecycleEvents)
        Assertions.assertTrue(configuration.defaultTracking.trackingDeepLinks)
        Assertions.assertTrue(configuration.defaultTracking.trackingScreenViews)
    }

    @Test
    fun configuration_defaultTracking_quick_update() {
        val configuration = Configuration(
            "test-apikey",
            context!!,
            defaultTracking = DefaultTrackingOptions.ALL
        )
        Assertions.assertTrue(configuration.defaultTracking.trackingSessionEvents)
        Assertions.assertTrue(configuration.defaultTracking.trackingAppLifecycleEvents)
        Assertions.assertTrue(configuration.defaultTracking.trackingDeepLinks)
        Assertions.assertTrue(configuration.defaultTracking.trackingScreenViews)

        configuration.defaultTracking = DefaultTrackingOptions.NONE
        Assertions.assertFalse(configuration.defaultTracking.trackingSessionEvents)
        Assertions.assertFalse(configuration.defaultTracking.trackingAppLifecycleEvents)
        Assertions.assertFalse(configuration.defaultTracking.trackingDeepLinks)
        Assertions.assertFalse(configuration.defaultTracking.trackingScreenViews)
    }
}
