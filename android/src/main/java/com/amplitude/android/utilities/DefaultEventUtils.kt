package com.amplitude.android.utilities

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.ParseException
import android.net.Uri
import android.os.Build
import com.amplitude.android.Amplitude
import com.amplitude.android.internal.fragments.FragmentActivityHandler.registerFragmentLifecycleCallbacks
import com.amplitude.android.internal.fragments.FragmentActivityHandler.unregisterFragmentLifecycleCallbacks
import com.amplitude.android.internal.gestures.AutocaptureWindowCallback
import com.amplitude.android.internal.gestures.NoCaptureWindowCallback
import com.amplitude.android.internal.locators.ViewTargetLocators.ALL
import com.amplitude.core.Constants.EventProperties
import com.amplitude.core.Constants.EventTypes
import com.amplitude.core.Storage
import kotlinx.coroutines.launch

@Deprecated("This class is deprecated and will be removed in future releases.")
class DefaultEventUtils(private val amplitude: Amplitude) {

    fun trackAppUpdatedInstalledEvent(packageInfo: PackageInfo) {
        // Get current version/build and previously stored version/build information
        val currentVersion = packageInfo.versionName
        val currentBuild = packageInfo.getVersionCode().toString()
        val storage = amplitude.storage
        val previousVersion = storage.read(Storage.Constants.APP_VERSION)
        val previousBuild = storage.read(Storage.Constants.APP_BUILD)

        if (previousBuild == null) {
            // No stored build, treat it as fresh installed
            amplitude.track(
                EventTypes.APPLICATION_INSTALLED,
                mapOf(
                    EventProperties.VERSION to currentVersion,
                    EventProperties.BUILD to currentBuild,
                ),
            )
        } else if (currentBuild != previousBuild) {
            // Has stored build, but different from current build
            amplitude.track(
                EventTypes.APPLICATION_UPDATED,
                mapOf(
                    EventProperties.PREVIOUS_VERSION to previousVersion,
                    EventProperties.PREVIOUS_BUILD to previousBuild,
                    EventProperties.VERSION to currentVersion,
                    EventProperties.BUILD to currentBuild,
                ),
            )
        }

        // Write the current version/build into persistent storage
        amplitude.amplitudeScope.launch(amplitude.storageIODispatcher) {
            storage.write(Storage.Constants.APP_VERSION, currentVersion)
            storage.write(Storage.Constants.APP_BUILD, currentBuild)
        }
    }

    fun trackAppOpenedEvent(
        packageInfo: PackageInfo,
        isFromBackground: Boolean,
    ) {
        val currentVersion = packageInfo.versionName
        val currentBuild = packageInfo.getVersionCode().toString()

        amplitude.track(
            EventTypes.APPLICATION_OPENED,
            mapOf(
                EventProperties.FROM_BACKGROUND to isFromBackground,
                EventProperties.VERSION to currentVersion,
                EventProperties.BUILD to currentBuild,
            ),
        )
    }

    fun trackAppBackgroundedEvent() {
        amplitude.track(EventTypes.APPLICATION_BACKGROUNDED)
    }

    fun trackDeepLinkOpenedEvent(activity: Activity) {
        val intent = activity.intent
        intent?.let {
            val referrer = getReferrer(activity)?.toString()
            it.data?.let { uri ->
                val url = uri.toString()
                amplitude.track(
                    EventTypes.DEEP_LINK_OPENED,
                    mapOf(
                        EventProperties.LINK_URL to url,
                        EventProperties.LINK_REFERRER to referrer,
                    ),
                )
            }
        }
    }

    fun trackScreenViewedEvent(activity: Activity) {
        try {
            amplitude.track(
                EventTypes.SCREEN_VIEWED,
                mapOf(
                    EventProperties.SCREEN_NAME to activity.screenName
                )
            )
        } catch (e: PackageManager.NameNotFoundException) {
            amplitude.logger.error("Failed to get activity info: $e")
        } catch (e: Exception) {
            amplitude.logger.error("Failed to track screen viewed event: $e")
        }
    }

    fun startUserInteractionEventTracking(activity: Activity) {
        activity.window?.let { window ->
            val delegate = window.callback ?: NoCaptureWindowCallback()
            window.callback =
                AutocaptureWindowCallback(
                    delegate,
                    activity,
                    amplitude::track,
                    ALL(amplitude.logger),
                    amplitude.logger,
                )
        } ?: amplitude.logger.error("Failed to track user interaction event: Activity window is null")
    }

    fun stopUserInteractionEventTracking(activity: Activity) {
        activity.window?.let { window ->
            (window.callback as? AutocaptureWindowCallback)?.let { windowCallback ->
                window.callback = windowCallback.delegate.takeUnless { it is NoCaptureWindowCallback }
            }
            true
        } ?: amplitude.logger.error("Failed to stop user interaction event tracking: Activity window is null")
    }

    private val isFragmentActivityAvailable by lazy {
        LoadClass.isClassAvailable(FRAGMENT_ACTIVITY_CLASS_NAME, amplitude.logger)
    }

    fun startFragmentViewedEventTracking(activity: Activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && isFragmentActivityAvailable) {
            activity.registerFragmentLifecycleCallbacks(amplitude::track, amplitude.logger)
        }
    }

    fun stopFragmentViewedEventTracking(activity: Activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && isFragmentActivityAvailable) {
            activity.unregisterFragmentLifecycleCallbacks(amplitude.logger)
        }
    }

    companion object {
        private const val FRAGMENT_ACTIVITY_CLASS_NAME = "androidx.fragment.app.FragmentActivity"
        internal val Activity.screenName: String?
            get() {
                val packageManager = packageManager
                val info =
                    packageManager?.getActivityInfo(
                        componentName,
                        PackageManager.GET_META_DATA,
                    )
                /* Get the label metadata in following order
                  1. activity label
                  2. if 1 is missing, fallback to parent application label
                  3. if 2 is missing, use the activity name
                 */
                return info?.loadLabel(packageManager)?.toString() ?: info?.name
            }
    }

    private fun getReferrer(activity: Activity): Uri? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
            return activity.referrer
        } else {
            var referrerUri: Uri? = null
            val intent = activity.intent
            referrerUri = intent.getParcelableExtra(Intent.EXTRA_REFERRER)
            if (referrerUri == null) {
                referrerUri =
                    intent.getStringExtra("android.intent.extra.REFERRER_NAME")?.let {
                        try {
                            Uri.parse(it)
                        } catch (e: ParseException) {
                            amplitude.logger.error("Failed to parse the referrer uri: $it")
                            null
                        }
                    }
            }
            return referrerUri
        }
    }
}

private fun PackageInfo.getVersionCode(): Number =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        this.longVersionCode
    } else {
        @Suppress("DEPRECATION")
        this.versionCode
    }
