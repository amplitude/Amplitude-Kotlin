package com.amplitude.android.utilities

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.ParseException
import android.net.Uri
import android.os.Build
import com.amplitude.android.Amplitude
import com.amplitude.android.internal.gestures.AutocaptureWindowCallback
import com.amplitude.android.internal.gestures.NoCaptureWindowCallback
import com.amplitude.android.internal.locators.ViewTargetLocators.ALL
import com.amplitude.core.Storage
import kotlinx.coroutines.launch

class DefaultEventUtils(private val amplitude: Amplitude) {
    object EventTypes {
        const val APPLICATION_INSTALLED = "[Amplitude] Application Installed"
        const val APPLICATION_UPDATED = "[Amplitude] Application Updated"
        const val APPLICATION_OPENED = "[Amplitude] Application Opened"
        const val APPLICATION_BACKGROUNDED = "[Amplitude] Application Backgrounded"
        const val DEEP_LINK_OPENED = "[Amplitude] Deep Link Opened"
        const val SCREEN_VIEWED = "[Amplitude] Screen Viewed"
        const val ELEMENT_CLICKED = "[Amplitude] Element Clicked"
    }

    object EventProperties {
        const val VERSION = "[Amplitude] Version"
        const val BUILD = "[Amplitude] Build"
        const val PREVIOUS_VERSION = "[Amplitude] Previous Version"
        const val PREVIOUS_BUILD = "[Amplitude] Previous Build"
        const val FROM_BACKGROUND = "[Amplitude] From Background"
        const val LINK_URL = "[Amplitude] Link URL"
        const val LINK_REFERRER = "[Amplitude] Link Referrer"
        const val SCREEN_NAME = "[Amplitude] Screen Name"
        const val ELEMENT_CLASS = "[Amplitude] Element Class"
        const val ELEMENT_RESOURCE = "[Amplitude] Element Resource"
        const val ELEMENT_TAG = "[Amplitude] Element Tag"
        const val ELEMENT_SOURCE = "[Amplitude] Element Source"
    }

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
            val packageManager = activity.packageManager
            val info =
                packageManager?.getActivityInfo(
                    activity.componentName,
                    PackageManager.GET_META_DATA,
                )
            /* Get the label metadata in following order
              1. activity label
              2. if 1 is missing, fallback to parent application label
              3. if 2 is missing, use the activity name
             */
            val activityLabel = info?.loadLabel(packageManager)?.toString() ?: info?.name
            amplitude.track(EventTypes.SCREEN_VIEWED, mapOf(EventProperties.SCREEN_NAME to activityLabel))
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

    private fun getReferrer(activity: Activity): Uri? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
            return activity.referrer
        } else {
            var referrerUri: Uri? = null
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
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
