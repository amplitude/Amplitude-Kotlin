package com.amplitude.android.plugins

import android.app.Activity
import android.app.Application
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.annotation.VisibleForTesting
import com.amplitude.android.AutocaptureState
import com.amplitude.android.Configuration
import com.amplitude.android.FrustrationInteractionsDetector
import com.amplitude.android.GuardedAmplitudeFeature
import com.amplitude.android.InteractionType.DeadClick
import com.amplitude.android.InteractionType.RageClick
import com.amplitude.android.internal.gestures.WindowCallbackManager
import com.amplitude.android.stringRepresentation
import com.amplitude.android.utilities.ActivityCallbackType
import com.amplitude.android.utilities.ActivityLifecycleObserver
import com.amplitude.android.utilities.DefaultEventUtils
import com.amplitude.core.Amplitude
import com.amplitude.core.RestrictedAmplitudeFeature
import com.amplitude.core.platform.Plugin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import com.amplitude.android.Amplitude as AndroidAmplitude

@OptIn(GuardedAmplitudeFeature::class, RestrictedAmplitudeFeature::class)
class AndroidLifecyclePlugin(
    private val activityLifecycleObserver: ActivityLifecycleObserver,
) : Application.ActivityLifecycleCallbacks,
    Plugin {
    override val type: Plugin.Type = Plugin.Type.Utility
    override lateinit var amplitude: Amplitude
    private lateinit var packageInfo: PackageInfo
    private lateinit var androidAmplitude: AndroidAmplitude
    private lateinit var autocaptureState: AutocaptureState

    private var frustrationInteractionsDetector: FrustrationInteractionsDetector? = null
    private var windowCallbackManager: WindowCallbackManager? = null

    private val created: MutableSet<Int> = mutableSetOf()
    private val started: MutableSet<Int> = mutableSetOf()
    private val processedDeepLinkIntents: MutableMap<Int, Int> = mutableMapOf()

    private var appInBackground = false

    @VisibleForTesting
    internal var eventJob: Job? = null

    override fun setup(amplitude: Amplitude) {
        super.setup(amplitude)
        androidAmplitude = amplitude as AndroidAmplitude
        val androidConfiguration = amplitude.configuration as Configuration

        // Build autocapture state once from configuration
        autocaptureState =
            AutocaptureState.from(
                androidConfiguration.autocapture,
                androidConfiguration.interactionsOptions,
            )

        // Set autocapture state to diagnostics client
        amplitude.diagnosticsClient.setTag(
            name = "autocapture.enabled",
            value = androidConfiguration.autocapture.stringRepresentation(),
        )

        val application = androidConfiguration.context as Application

        // Initialize frustration interactions detector if rage or dead click is enabled
        if (RageClick in autocaptureState.interactions ||
            DeadClick in autocaptureState.interactions
        ) {
            val density = application.resources.displayMetrics.density

            frustrationInteractionsDetector =
                FrustrationInteractionsDetector(
                    amplitude = amplitude,
                    logger = amplitude.logger,
                    density = density,
                    autocaptureState = autocaptureState,
                )
            frustrationInteractionsDetector?.start()
        }

        // Initialize window callback manager for tracking element interactions across all windows
        // This enables tracking interactions in dialogs (including NavGraph dialogs)
        if (autocaptureState.interactions.isNotEmpty()) {
            windowCallbackManager =
                WindowCallbackManager(
                    track = androidAmplitude::track,
                    frustrationDetector = frustrationInteractionsDetector,
                    autocaptureState = autocaptureState,
                    logger = androidAmplitude.logger,
                )
            windowCallbackManager?.start()
        }

        if (autocaptureState.appLifecycles) {
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

        if (autocaptureState.screenViews) {
            DefaultEventUtils(androidAmplitude).startFragmentViewedEventTracking(activity)
        }
    }

    override fun onActivityStarted(activity: Activity) {
        if (!created.contains(activity.hashCode())) {
            // We check for On Create in case if sdk was initialised in Main Activity
            onActivityCreated(activity, activity.intent.extras)
        }

        if (started.isEmpty()) {
            androidAmplitude.onEnterForeground(System.currentTimeMillis())
        }

        started.add(activity.hashCode())

        if (autocaptureState.appLifecycles && started.size == 1) {
            DefaultEventUtils(androidAmplitude).trackAppOpenedEvent(
                packageInfo = packageInfo,
                isFromBackground = appInBackground,
            )
            appInBackground = false
        }

        if (autocaptureState.deepLinks) {
            trackDeepLinkIfNew(activity)
        }

        if (autocaptureState.screenViews) {
            DefaultEventUtils(androidAmplitude).trackScreenViewedEvent(activity)
        }
    }

    override fun onActivityResumed(activity: Activity) {
        // Handle onNewIntent() deep links for singleTop/singleTask activities.
        // Requires developers to call setIntent(newIntent) in their onNewIntent().
        if (autocaptureState.deepLinks) {
            trackDeepLinkIfNew(activity)
        }
    }

    override fun onActivityPaused(activity: Activity) = Unit

    override fun onActivityStopped(activity: Activity) {
        started.remove(activity.hashCode())

        if (autocaptureState.appLifecycles && started.isEmpty()) {
            DefaultEventUtils(androidAmplitude).trackAppBackgroundedEvent()
            appInBackground = true
        }

        if (started.isEmpty()) {
            with(androidAmplitude) {
                onExitForeground(System.currentTimeMillis())
                if ((configuration as Configuration).flushEventsOnClose) {
                    flush()
                }
            }
        }
    }

    override fun onActivitySaveInstanceState(
        activity: Activity,
        bundle: Bundle,
    ) {
    }

    override fun onActivityDestroyed(activity: Activity) {
        created.remove(activity.hashCode())
        processedDeepLinkIntents.remove(activity.hashCode())

        if (autocaptureState.screenViews) {
            DefaultEventUtils(androidAmplitude).stopFragmentViewedEventTracking(activity)
        }
    }

    /**
     * Tracks a deep link event only if this intent has not been processed before.
     * Uses the intent's identity to allow the same URL to be tracked from different intents,
     * while preventing duplicate tracking when returning from background to foreground.
     */
    private fun trackDeepLinkIfNew(activity: Activity) {
        val intent = activity.intent ?: return
        val intentIdentity = System.identityHashCode(intent)
        val activityHash = activity.hashCode()

        if (processedDeepLinkIntents[activityHash] == intentIdentity) {
            return
        }

        processedDeepLinkIntents[activityHash] = intentIdentity
        DefaultEventUtils(androidAmplitude).trackDeepLinkOpenedEvent(activity)
    }

    override fun teardown() {
        super.teardown()
        eventJob?.cancel()
        windowCallbackManager?.stop()
        frustrationInteractionsDetector?.stop()
    }
}
