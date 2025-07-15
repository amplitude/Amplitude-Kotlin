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
        Assertions.assertTrue(configuration.defaultTracking.sessions)
        Assertions.assertFalse(configuration.defaultTracking.appLifecycles)
        Assertions.assertFalse(configuration.defaultTracking.deepLinks)
        Assertions.assertFalse(configuration.defaultTracking.screenViews)

        configuration.trackingSessionEvents = false
        configuration.defaultTracking.sessions = false
        configuration.defaultTracking.appLifecycles = true
        configuration.defaultTracking.deepLinks = true
        configuration.defaultTracking.screenViews = true

        Assertions.assertFalse(configuration.trackingSessionEvents)
        Assertions.assertFalse(configuration.defaultTracking.sessions)
        Assertions.assertTrue(configuration.defaultTracking.appLifecycles)
        Assertions.assertTrue(configuration.defaultTracking.deepLinks)
        Assertions.assertTrue(configuration.defaultTracking.screenViews)

        Assertions.assertFalse(AutocaptureOption.SESSIONS in configuration.autocapture)
        Assertions.assertTrue(AutocaptureOption.APP_LIFECYCLES in configuration.autocapture)
        Assertions.assertTrue(AutocaptureOption.DEEP_LINKS in configuration.autocapture)
        Assertions.assertTrue(AutocaptureOption.SCREEN_VIEWS in configuration.autocapture)
    }

    @Test
    fun configuration_defaultTracking_quick_update() {
        val configuration =
            Configuration(
                "test-apikey",
                context!!,
                autocapture =
                    autocaptureOptions {
                        +sessions
                        +appLifecycles
                        +deepLinks
                        +screenViews
                        +elementInteractions
                    },
            )
        Assertions.assertTrue(AutocaptureOption.SESSIONS in configuration.autocapture)
        Assertions.assertTrue(AutocaptureOption.APP_LIFECYCLES in configuration.autocapture)
        Assertions.assertTrue(AutocaptureOption.DEEP_LINKS in configuration.autocapture)
        Assertions.assertTrue(AutocaptureOption.SCREEN_VIEWS in configuration.autocapture)
        Assertions.assertTrue(AutocaptureOption.ELEMENT_INTERACTIONS in configuration.autocapture)
    }

    @Suppress("DEPRECATION")
    @Test
    fun configuration_defaultTracking_replace_instance() {
        val configuration =
            Configuration(
                "test-apikey",
                context!!,
                autocapture =
                    autocaptureOptions {
                        +sessions
                        +appLifecycles
                        +deepLinks
                        +screenViews
                    },
            )
        configuration.defaultTracking =
            DefaultTrackingOptions(
                sessions = false,
                appLifecycles = true,
                deepLinks = false,
                screenViews = true,
            )
        Assertions.assertFalse(AutocaptureOption.SESSIONS in configuration.autocapture)
        Assertions.assertTrue(AutocaptureOption.APP_LIFECYCLES in configuration.autocapture)
        Assertions.assertFalse(AutocaptureOption.DEEP_LINKS in configuration.autocapture)
        Assertions.assertTrue(AutocaptureOption.SCREEN_VIEWS in configuration.autocapture)
    }

    @Suppress("DEPRECATION")
    @Test
    fun configuration_defaultTracking_configuration() {
        val configuration =
            Configuration(
                "test-apikey",
                context!!,
                defaultTracking =
                    DefaultTrackingOptions(
                        sessions = false,
                        appLifecycles = true,
                        deepLinks = false,
                        screenViews = true,
                    ),
            )
        Assertions.assertFalse(AutocaptureOption.SESSIONS in configuration.autocapture)
        Assertions.assertTrue(AutocaptureOption.APP_LIFECYCLES in configuration.autocapture)
        Assertions.assertFalse(AutocaptureOption.DEEP_LINKS in configuration.autocapture)
        Assertions.assertTrue(AutocaptureOption.SCREEN_VIEWS in configuration.autocapture)
    }

    @Suppress("DEPRECATION")
    @Test
    fun configuration_trackingSessionEvents_configuration() {
        val configuration =
            Configuration(
                "test-apikey",
                context!!,
                trackingSessionEvents = false,
            )
        Assertions.assertFalse(AutocaptureOption.SESSIONS in configuration.autocapture)
        Assertions.assertFalse(AutocaptureOption.APP_LIFECYCLES in configuration.autocapture)
        Assertions.assertFalse(AutocaptureOption.DEEP_LINKS in configuration.autocapture)
        Assertions.assertFalse(AutocaptureOption.SCREEN_VIEWS in configuration.autocapture)
    }

    @Test
    fun autocaptureOption_requiresActivityCallbacks_containsExpectedOptions() {
        val expectedOptions =
            setOf(
                AutocaptureOption.APP_LIFECYCLES,
                AutocaptureOption.SCREEN_VIEWS,
                AutocaptureOption.ELEMENT_INTERACTIONS,
                AutocaptureOption.DEEP_LINKS,
            )

        Assertions.assertEquals(expectedOptions, AutocaptureOption.REQUIRES_ACTIVITY_CALLBACKS)

        // Verify SESSIONS is NOT included (it doesn't need activity callbacks)
        Assertions.assertFalse(AutocaptureOption.SESSIONS in AutocaptureOption.REQUIRES_ACTIVITY_CALLBACKS)
    }

    @Suppress("DEPRECATION")
    @Test
    fun configuration_defaultTracking_replaces_autocapture_entirely_configuration() {
        val configuration =
            Configuration(
                "test-apikey",
                context!!,
                autocapture =
                    autocaptureOptions {
                        +sessions
                        +appLifecycles
                        +deepLinks
                        +screenViews
                    },
            )
        configuration.defaultTracking.deepLinks = true
        Assertions.assertTrue(AutocaptureOption.SESSIONS in configuration.autocapture)
        Assertions.assertFalse(AutocaptureOption.APP_LIFECYCLES in configuration.autocapture)
        Assertions.assertTrue(AutocaptureOption.DEEP_LINKS in configuration.autocapture)
        Assertions.assertFalse(AutocaptureOption.SCREEN_VIEWS in configuration.autocapture)
    }
}
