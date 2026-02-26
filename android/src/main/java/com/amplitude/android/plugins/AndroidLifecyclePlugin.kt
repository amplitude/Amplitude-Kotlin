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
import com.amplitude.android.utilities.ActivityCallbackType
import com.amplitude.android.utilities.ActivityLifecycleObserver
import com.amplitude.android.utilities.DefaultEventUtils
import com.amplitude.core.Amplitude
import com.amplitude.core.RestrictedAmplitudeFeature
import com.amplitude.core.platform.Plugin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference
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
    private val autocaptureState: AutocaptureState
        get() = androidAmplitude.autocaptureManager.state.value

    private var frustrationInteractionsDetector: FrustrationInteractionsDetector? = null
    private var windowCallbackManager: WindowCallbackManager? = null

    private val created: MutableMap<Int, WeakReference<Activity>> = mutableMapOf()
    private val fragmentTrackingActivities: MutableSet<Int> = mutableSetOf()
    private val started: MutableSet<Int> = mutableSetOf()
    private val processedDeepLinkIntents: MutableMap<Int, Int> = mutableMapOf()

    private var appInBackground = false
    private var trackedAppLifecycleEvent = false

    private var stateObserverJob: Job? = null

    @VisibleForTesting
    internal var eventJob: Job? = null

    override fun setup(amplitude: Amplitude) {
        super.setup(amplitude)
        androidAmplitude = amplitude as AndroidAmplitude
        val androidConfiguration = amplitude.configuration as Configuration

        val application = androidConfiguration.context as Application

        // Always initialize packageInfo — remote config may enable appLifecycles later.
        packageInfo =
            try {
                application.packageManager.getPackageInfo(application.packageName, 0)
            } catch (e: PackageManager.NameNotFoundException) {
                // This shouldn't happen, but in case it happens, fallback to empty package info.
                amplitude.logger.error("Cannot find package with application.packageName: " + application.packageName)
                PackageInfo()
            }

        // Observe autocapture state changes (including the initial value) to
        // enable features at runtime via remote config. Collected on main thread
        // because fragment registration and created-map iteration require it.
        stateObserverJob =
            amplitude.amplitudeScope.launch(Dispatchers.Main) {
                androidAmplitude.autocaptureManager.state.collect {
                    trackAppLifecycleEventIfNeeded()
                    startInteractionTrackingIfNeeded(application)
                    startFragmentTrackingIfNeeded()
                }
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
        created[activity.hashCode()] = WeakReference(activity)

        if (autocaptureState.screenViews) {
            registerFragmentTracking(activity)
        }
    }

    override fun onActivityStarted(activity: Activity) {
        if (!created.containsKey(activity.hashCode())) {
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
        }
        if (started.size == 1) {
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
        }

        if (started.isEmpty()) {
            appInBackground = true
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
        val hash = activity.hashCode()
        created.remove(hash)
        fragmentTrackingActivities.remove(hash)
        processedDeepLinkIntents.remove(hash)

        // Always unregister — screenViews may have been disabled by remote config since
        // callbacks were registered, so we cannot gate this on current state.
        DefaultEventUtils(androidAmplitude).stopFragmentViewedEventTracking(activity)
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

    /**
     * Registers fragment lifecycle callbacks for a single activity, guarded against
     * double-registration.
     */
    private fun registerFragmentTracking(activity: Activity) {
        val hash = activity.hashCode()
        if (hash in fragmentTrackingActivities) return
        fragmentTrackingActivities.add(hash)
        DefaultEventUtils(androidAmplitude).startFragmentViewedEventTracking(
            activity,
            screenViewsEnabled = { autocaptureState.screenViews },
        )
    }

    /**
     * Fires the one-time app installed/updated event when appLifecycles becomes enabled,
     * either initially or via remote config. Guarded to fire at most once per SDK lifetime.
     */
    private fun trackAppLifecycleEventIfNeeded() {
        if (trackedAppLifecycleEvent || !autocaptureState.appLifecycles) return
        trackedAppLifecycleEvent = true
        DefaultEventUtils(androidAmplitude).trackAppUpdatedInstalledEvent(packageInfo)
    }

    /**
     * When screen views are enabled at runtime via remote config, registers fragment
     * lifecycle callbacks for all currently alive activities that don't already have them.
     */
    private fun startFragmentTrackingIfNeeded() {
        if (!autocaptureState.screenViews) return
        for ((_, ref) in created) {
            val activity = ref.get() ?: continue
            registerFragmentTracking(activity)
        }
    }

    /**
     * Lazily creates and starts interaction tracking components when any interaction type
     * is enabled (either initially or via remote config). Once created, the components
     * persist for the lifetime of the plugin — they check autocaptureState dynamically
     * to decide whether to process individual events.
     */
    private fun startInteractionTrackingIfNeeded(application: Application) {
        if (autocaptureState.interactions.isEmpty()) return

        // Create frustration interactions detector if not yet created
        val hadFrustrationDetector = frustrationInteractionsDetector != null
        if (!hadFrustrationDetector &&
            (RageClick in autocaptureState.interactions || DeadClick in autocaptureState.interactions)
        ) {
            val density = application.resources.displayMetrics.density
            frustrationInteractionsDetector =
                FrustrationInteractionsDetector(
                    amplitude = amplitude,
                    logger = amplitude.logger,
                    density = density,
                    autocaptureStateProvider = { autocaptureState },
                )
            frustrationInteractionsDetector?.start()
        }

        // If frustration detector was just created but a window callback manager already
        // exists (created without the detector), recreate it so existing windows get
        // rewrapped with FrustrationAwareWindowCallback.
        if (!hadFrustrationDetector && frustrationInteractionsDetector != null && windowCallbackManager != null) {
            windowCallbackManager?.stop()
            windowCallbackManager = null
        }

        // Create window callback manager if not yet created
        if (windowCallbackManager == null) {
            windowCallbackManager =
                WindowCallbackManager(
                    track = androidAmplitude::track,
                    frustrationDetector = frustrationInteractionsDetector,
                    autocaptureStateProvider = { autocaptureState },
                    logger = androidAmplitude.logger,
                )
            windowCallbackManager?.start()
        }
    }

    override fun teardown() {
        super.teardown()
        stateObserverJob?.cancel()
        eventJob?.cancel()
        windowCallbackManager?.stop()
        frustrationInteractionsDetector?.stop()
    }
}
