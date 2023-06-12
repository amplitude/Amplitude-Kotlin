package com.amplitude.android.plugins

import android.app.Activity
import android.app.Application
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Bundle
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

    private val hasTrackedApplicationLifecycleEvents = AtomicBoolean(false)
    private val numberOfActivities = AtomicInteger(1)
    private val isFirstLaunch = AtomicBoolean(false)

    override fun setup(amplitude: Amplitude) {
        super.setup(amplitude)

        val application = (amplitude.configuration as Configuration).context as Application
        val packageManager: PackageManager = application.packageManager
        packageInfo = try {
            packageManager.getPackageInfo(application.packageName, 0)
        } catch (e: PackageManager.NameNotFoundException) {
            throw AssertionError("Cannot find package with application.packageName: " + application.packageName)
        }
        application.registerActivityLifecycleCallbacks(this)
    }

    override fun onActivityCreated(activity: Activity, bundle: Bundle?) {
        val configuration = amplitude.configuration as Configuration
        if (!hasTrackedApplicationLifecycleEvents.getAndSet(true) && configuration.trackingAppLifecycleEvents) {
            numberOfActivities.set(0)
            isFirstLaunch.set(true)
            DefaultEventUtils(amplitude as com.amplitude.android.Amplitude).trackAppUpdatedInstalledEvent(packageInfo)
        }
        if (configuration.trackingDeepLinks) {
            DefaultEventUtils(amplitude as com.amplitude.android.Amplitude).trackDeepLinkOpenedEvent(activity)
        }
    }

    override fun onActivityStarted(activity: Activity) {
        val configuration = amplitude.configuration as Configuration
        if (configuration.trackingScreenViews) {
            DefaultEventUtils(amplitude as com.amplitude.android.Amplitude).trackScreenViewedEvent(activity)
        }
    }

    override fun onActivityResumed(activity: Activity) {
        (amplitude as com.amplitude.android.Amplitude).onEnterForeground(getCurrentTimeMillis())

        val configuration = amplitude.configuration as Configuration
        // numberOfActivities makes sure it only fires after activity creation or activity stopped
        if (configuration.trackingAppLifecycleEvents && numberOfActivities.incrementAndGet() == 1) {
            val isFromBackground = !isFirstLaunch.getAndSet(false)
            DefaultEventUtils(amplitude as com.amplitude.android.Amplitude).trackAppOpenedEvent(packageInfo, isFromBackground)
        }
    }

    override fun onActivityPaused(activity: Activity) {
        (amplitude as com.amplitude.android.Amplitude).onExitForeground(getCurrentTimeMillis())
    }

    override fun onActivityStopped(activity: Activity) {
        val configuration = amplitude.configuration as Configuration
        // numberOfActivities makes sure it only fires after setup or activity resumed
        if (configuration.trackingAppLifecycleEvents && numberOfActivities.decrementAndGet() == 0) {
            DefaultEventUtils(amplitude as com.amplitude.android.Amplitude).trackAppBackgroundedEvent()
        }
    }

    override fun onActivitySaveInstanceState(activity: Activity, bundle: Bundle) {
    }

    override fun onActivityDestroyed(activity: Activity) {
    }

    companion object {
        fun getCurrentTimeMillis(): Long {
            return System.currentTimeMillis()
        }
    }
}