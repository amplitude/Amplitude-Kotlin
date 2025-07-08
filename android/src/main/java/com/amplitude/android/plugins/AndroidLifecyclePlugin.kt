package com.amplitude.android.plugins

import android.app.Activity
import android.app.Application
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.annotation.VisibleForTesting
import com.amplitude.android.AutocaptureOption
import com.amplitude.android.AutocaptureOption.APP_LIFECYCLES
import com.amplitude.android.AutocaptureOption.DEEP_LINKS
import com.amplitude.android.AutocaptureOption.ELEMENT_INTERACTIONS
import com.amplitude.android.AutocaptureOption.SCREEN_VIEWS
import com.amplitude.android.Configuration
import com.amplitude.android.GuardedAmplitudeFeature
import com.amplitude.android.utilities.ActivityCallbackType
import com.amplitude.android.utilities.ActivityLifecycleObserver
import com.amplitude.android.utilities.DefaultEventUtils
import com.amplitude.core.Amplitude
import com.amplitude.core.platform.Plugin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import com.amplitude.android.Amplitude as AndroidAmplitude

@OptIn(GuardedAmplitudeFeature::class)
class AndroidLifecyclePlugin(
    private val activityLifecycleObserver: ActivityLifecycleObserver,
) : Application.ActivityLifecycleCallbacks, Plugin {
    override val type: Plugin.Type = Plugin.Type.Utility
    override lateinit var amplitude: Amplitude
    private lateinit var packageInfo: PackageInfo
    private lateinit var androidAmplitude: AndroidAmplitude
    private lateinit var autocapture: Set<AutocaptureOption>

    private val created: MutableSet<Int> = mutableSetOf()
    private val started: MutableSet<Int> = mutableSetOf()

    private var appInBackground = false

    @VisibleForTesting
    internal var eventJob: Job? = null

    override fun setup(amplitude: Amplitude) {
        super.setup(amplitude)
        androidAmplitude = amplitude as AndroidAmplitude
        val androidConfiguration = amplitude.configuration as Configuration
        autocapture = androidConfiguration.autocapture

        val application = androidConfiguration.context as Application

        if (APP_LIFECYCLES in autocapture) {
            packageInfo =
                try {
                    application.packageManager.getPackageInfo(application.packageName, 0)
                } catch (e: PackageManager.NameNotFoundException) {
                    // This shouldn't happen, but in case it happens, fallback to empty package info.
                    amplitude.logger.error("Cannot find package with application.packageName: " + application.packageName)
                    PackageInfo()
                }

            DefaultEventUtils(androidAmplitude).trackAppUpdatedInstalledEvent(packageInfo)
        }

        eventJob =
            amplitude.amplitudeScope.launch(Dispatchers.Main) {
                for (event in activityLifecycleObserver.eventChannel) {
                    event.activity.get()?.let { activity ->
                        when (event.type) {
                            ActivityCallbackType.Created ->
                                onActivityCreated(
                                    activity,
                                    activity.intent?.extras,
                                )
                            ActivityCallbackType.Started -> onActivityStarted(activity)
                            ActivityCallbackType.Resumed -> onActivityResumed(activity)
                            ActivityCallbackType.Paused -> onActivityPaused(activity)
                            ActivityCallbackType.Stopped -> onActivityStopped(activity)
                            ActivityCallbackType.Destroyed -> onActivityDestroyed(activity)
                        }
                    }
                }
            }
    }

    override fun onActivityCreated(
        activity: Activity,
        bundle: Bundle?,
    ) {
        created.add(activity.hashCode())

        if (SCREEN_VIEWS in autocapture) {
            DefaultEventUtils(androidAmplitude).startFragmentViewedEventTracking(activity)
        }
    }

    override fun onActivityStarted(activity: Activity) {
        if (!created.contains(activity.hashCode())) {
            // We check for On Create in case if sdk was initialised in Main Activity
            onActivityCreated(activity, activity.intent.extras)
        }
        started.add(activity.hashCode())

        if (APP_LIFECYCLES in autocapture && started.size == 1) {
            DefaultEventUtils(androidAmplitude).trackAppOpenedEvent(
                packageInfo = packageInfo,
                isFromBackground = appInBackground,
            )
            appInBackground = false
        }

        if (DEEP_LINKS in autocapture) {
            DefaultEventUtils(androidAmplitude).trackDeepLinkOpenedEvent(activity)
        }

        if (SCREEN_VIEWS in autocapture) {
            DefaultEventUtils(androidAmplitude).trackScreenViewedEvent(activity)
        }
    }

    override fun onActivityResumed(activity: Activity) {
        androidAmplitude.onEnterForeground(System.currentTimeMillis())

        if (ELEMENT_INTERACTIONS in autocapture) {
            DefaultEventUtils(androidAmplitude).startUserInteractionEventTracking(activity)
        }
    }

    override fun onActivityPaused(activity: Activity) {
        with(androidAmplitude) {
            onExitForeground(System.currentTimeMillis())

            if ((configuration as Configuration).flushEventsOnClose) {
                flush()
            }
        }

        if (ELEMENT_INTERACTIONS in autocapture) {
            DefaultEventUtils(androidAmplitude).stopUserInteractionEventTracking(activity)
        }
    }

    override fun onActivityStopped(activity: Activity) {
        started.remove(activity.hashCode())

        if (APP_LIFECYCLES in autocapture && started.isEmpty()) {
            DefaultEventUtils(androidAmplitude).trackAppBackgroundedEvent()
            appInBackground = true
        }
    }

    override fun onActivitySaveInstanceState(
        activity: Activity,
        bundle: Bundle,
    ) {
    }

    override fun onActivityDestroyed(activity: Activity) {
        created.remove(activity.hashCode())

        if (SCREEN_VIEWS in autocapture) {
            DefaultEventUtils(androidAmplitude).stopFragmentViewedEventTracking(activity)
        }
    }

    override fun teardown() {
        super.teardown()
        eventJob?.cancel()
    }
}
