package com.amplitude.android.plugins

import android.app.Activity
import android.app.Application
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Bundle
import com.amplitude.android.AutocaptureOption
import com.amplitude.android.Configuration
import com.amplitude.android.ExperimentalAmplitudeFeature
import com.amplitude.android.utilities.DefaultEventUtils
import com.amplitude.core.Amplitude
import com.amplitude.core.platform.Plugin
import com.amplitude.android.Amplitude as AndroidAmplitude

class AndroidLifecyclePlugin : Application.ActivityLifecycleCallbacks, Plugin {
    override val type: Plugin.Type = Plugin.Type.Utility
    override lateinit var amplitude: Amplitude
    private lateinit var packageInfo: PackageInfo
    private lateinit var androidAmplitude: AndroidAmplitude
    private lateinit var androidConfiguration: Configuration

    private val created: MutableSet<Int> = mutableSetOf()
    private val started: MutableSet<Int> = mutableSetOf()
    private val resumed: MutableSet<Int> = mutableSetOf()

    private var appInBackground = false

    override fun setup(amplitude: Amplitude) {
        super.setup(amplitude)
        androidAmplitude = amplitude as AndroidAmplitude
        androidConfiguration = amplitude.configuration as Configuration

        val application = androidConfiguration.context as Application

        if (AutocaptureOption.APP_LIFECYCLES in androidConfiguration.autocapture) {
            packageInfo = try {
                application.packageManager.getPackageInfo(application.packageName, 0)
            } catch (e: PackageManager.NameNotFoundException) {
                // This shouldn't happen, but in case it happens, fallback to empty package info.
                amplitude.logger.error("Cannot find package with application.packageName: " + application.packageName)
                PackageInfo()
            }

            DefaultEventUtils(androidAmplitude).trackAppUpdatedInstalledEvent(packageInfo)

            application.registerActivityLifecycleCallbacks(this)
        }
    }

    override fun onActivityCreated(activity: Activity, bundle: Bundle?) {
        created.add(activity.hashCode())

        if (AutocaptureOption.DEEP_LINKS in androidConfiguration.autocapture) {
            DefaultEventUtils(androidAmplitude).trackDeepLinkOpenedEvent(activity)
        }
        if (AutocaptureOption.SCREEN_VIEWS in androidConfiguration.autocapture) {
            DefaultEventUtils(androidAmplitude).startFragmentViewedEventTracking(activity)
        }
    }

    override fun onActivityStarted(activity: Activity) {
        started.add(activity.hashCode())

        if (AutocaptureOption.APP_LIFECYCLES in androidConfiguration.autocapture && started.size == 1) {
            DefaultEventUtils(androidAmplitude).trackAppOpenedEvent(
                packageInfo = packageInfo,
                isFromBackground = appInBackground
            )
            appInBackground = false
        }

        if (AutocaptureOption.SCREEN_VIEWS in androidConfiguration.autocapture) {
            DefaultEventUtils(androidAmplitude).trackScreenViewedEvent(activity)
        }
    }

    override fun onActivityResumed(activity: Activity) {
        resumed.add(activity.hashCode())

        androidAmplitude.onEnterForeground(getCurrentTimeMillis())

        @OptIn(ExperimentalAmplitudeFeature::class)
        if (AutocaptureOption.ELEMENT_INTERACTIONS in androidConfiguration.autocapture) {
            DefaultEventUtils(androidAmplitude).startUserInteractionEventTracking(activity)
        }
    }

    override fun onActivityPaused(activity: Activity) {
        resumed.remove(activity.hashCode())

        androidAmplitude.onExitForeground(getCurrentTimeMillis())
        @OptIn(ExperimentalAmplitudeFeature::class)
        if (AutocaptureOption.ELEMENT_INTERACTIONS in androidConfiguration.autocapture) {
            DefaultEventUtils(androidAmplitude).stopUserInteractionEventTracking(activity)
        }
    }

    override fun onActivityStopped(activity: Activity) {
        started.remove(activity.hashCode())

        if (AutocaptureOption.APP_LIFECYCLES in androidConfiguration.autocapture && started.isEmpty()) {
            DefaultEventUtils(androidAmplitude).trackAppBackgroundedEvent()
            appInBackground = true
        }
    }

    override fun onActivitySaveInstanceState(activity: Activity, bundle: Bundle) {
    }

    override fun onActivityDestroyed(activity: Activity) {
        created.remove(activity.hashCode())

        if (AutocaptureOption.SCREEN_VIEWS in androidConfiguration.autocapture) {
            DefaultEventUtils(androidAmplitude).stopFragmentViewedEventTracking(activity)
        }
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
