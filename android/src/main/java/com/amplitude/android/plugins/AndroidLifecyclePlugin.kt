package com.amplitude.android.plugins

import android.app.Activity
import android.app.Application
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Bundle
import com.amplitude.android.AutocaptureOption
import com.amplitude.android.Configuration
import com.amplitude.android.utilities.DefaultEventUtils
import com.amplitude.core.Amplitude
import com.amplitude.core.platform.Plugin
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class AndroidLifecyclePlugin : Application.ActivityLifecycleCallbacks, Plugin {
    override val type: Plugin.Type = Plugin.Type.Utility
    override lateinit var amplitude: Amplitude
    private lateinit var packageInfo: PackageInfo
    private lateinit var androidAmplitude: com.amplitude.android.Amplitude
    private lateinit var androidConfiguration: Configuration

    private val hasTrackedApplicationLifecycleEvents = AtomicBoolean(false)
    private val numberOfActivities = AtomicInteger(1)
    private val isFirstLaunch = AtomicBoolean(false)

    override fun setup(amplitude: Amplitude) {
        super.setup(amplitude)
        androidAmplitude = amplitude as com.amplitude.android.Amplitude
        androidConfiguration = amplitude.configuration as Configuration

        val application = androidConfiguration.context as Application
        val packageManager: PackageManager = application.packageManager
        packageInfo = try {
            packageManager.getPackageInfo(application.packageName, 0)
        } catch (e: PackageManager.NameNotFoundException) {
            // This shouldn't happen, but in case it happens, fallback to empty package info.
            amplitude.logger.error("Cannot find package with application.packageName: " + application.packageName)
            PackageInfo()
        }
        application.registerActivityLifecycleCallbacks(this)
    }

    override fun onActivityCreated(activity: Activity, bundle: Bundle?) {
        if (!hasTrackedApplicationLifecycleEvents.getAndSet(true) && AutocaptureOption.APP_LIFECYCLES in androidConfiguration.autocapture) {
            numberOfActivities.set(0)
            isFirstLaunch.set(true)
            DefaultEventUtils(androidAmplitude).trackAppUpdatedInstalledEvent(packageInfo)
        }
        if (AutocaptureOption.DEEP_LINKS in androidConfiguration.autocapture) {
            DefaultEventUtils(androidAmplitude).trackDeepLinkOpenedEvent(activity)
        }
    }

    override fun onActivityStarted(activity: Activity) {
        if (AutocaptureOption.SCREEN_VIEWS in androidConfiguration.autocapture) {
            DefaultEventUtils(androidAmplitude).trackScreenViewedEvent(activity)
        }
    }

    override fun onActivityResumed(activity: Activity) {
        androidAmplitude.onEnterForeground(getCurrentTimeMillis())

        // numberOfActivities makes sure it only fires after activity creation or activity stopped
        if (AutocaptureOption.APP_LIFECYCLES in androidConfiguration.autocapture && numberOfActivities.incrementAndGet() == 1) {
            val isFromBackground = !isFirstLaunch.getAndSet(false)
            DefaultEventUtils(androidAmplitude).trackAppOpenedEvent(packageInfo, isFromBackground)
        }
        if (AutocaptureOption.ELEMENT_INTERACTIONS in androidConfiguration.autocapture) {
            DefaultEventUtils(androidAmplitude).startUserInteractionEventTracking(activity)
        }
    }

    override fun onActivityPaused(activity: Activity) {
        androidAmplitude.onExitForeground(getCurrentTimeMillis())
        if (AutocaptureOption.ELEMENT_INTERACTIONS in androidConfiguration.autocapture) {
            DefaultEventUtils(androidAmplitude).stopUserInteractionEventTracking(activity)
        }
    }

    override fun onActivityStopped(activity: Activity) {
        // numberOfActivities makes sure it only fires after setup or activity resumed
        if (AutocaptureOption.APP_LIFECYCLES in androidConfiguration.autocapture && numberOfActivities.decrementAndGet() == 0) {
            DefaultEventUtils(androidAmplitude).trackAppBackgroundedEvent()
        }
    }

    override fun onActivitySaveInstanceState(activity: Activity, bundle: Bundle) {
    }

    override fun onActivityDestroyed(activity: Activity) {
    }

    override fun teardown() {
        super.teardown()
        (androidConfiguration.context as Application).unregisterActivityLifecycleCallbacks(this)
    }

    companion object {
        @JvmStatic
        fun getCurrentTimeMillis(): Long {
            return System.currentTimeMillis()
        }
    }
}
