package com.amplitude.android.utilities

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.ParseException
import android.net.Uri
import android.os.Build
import com.amplitude.android.Amplitude
import com.amplitude.android.Constants
import com.amplitude.android.internal.fragments.FragmentActivityHandler.registerFragmentLifecycleCallbacks
import com.amplitude.android.internal.fragments.FragmentActivityHandler.unregisterFragmentLifecycleCallbacks
import com.amplitude.core.Storage
import com.amplitude.android.Constants.EventProperties as ConstantsEventProperties
import com.amplitude.android.Constants.EventTypes as ConstantsEventTypes

@Deprecated("This class is deprecated and will be removed in future releases.")
class DefaultEventUtils(private val amplitude: Amplitude) {
    /** Superseded by [com.amplitude.android.plugins.AndroidLifecyclePlugin]; no-op after SDK init. */
    fun trackAppUpdatedInstalledEvent(packageInfo: PackageInfo) {
        val storage = amplitude.storage
        trackAppUpdatedInstalledEvent(
            currentVersion = packageInfo.versionName ?: "Unknown",
            currentBuild = packageInfo.getVersionCode().toString(),
            previousVersion = storage.read(Storage.Constants.APP_VERSION),
            previousBuild = storage.read(Storage.Constants.APP_BUILD),
        )
    }

    internal fun trackAppUpdatedInstalledEvent(
        currentVersion: String,
        currentBuild: String,
        previousVersion: String?,
        previousBuild: String?,
    ) {
        if (previousBuild == null) {
            amplitude.track(
                ConstantsEventTypes.APPLICATION_INSTALLED,
                mapOf(
                    ConstantsEventProperties.VERSION to currentVersion,
                    ConstantsEventProperties.BUILD to currentBuild,
                ),
            )
        } else if (currentBuild != previousBuild) {
            amplitude.track(
                ConstantsEventTypes.APPLICATION_UPDATED,
                mapOf(
                    ConstantsEventProperties.PREVIOUS_VERSION to previousVersion,
                    ConstantsEventProperties.PREVIOUS_BUILD to previousBuild,
                    ConstantsEventProperties.VERSION to currentVersion,
                    ConstantsEventProperties.BUILD to currentBuild,
                ),
            )
        }
    }

    fun trackAppOpenedEvent(
        packageInfo: PackageInfo,
        isFromBackground: Boolean,
    ) {
        val currentVersion = packageInfo.versionName
        val currentBuild = packageInfo.getVersionCode().toString()

        amplitude.track(
            ConstantsEventTypes.APPLICATION_OPENED,
            mapOf(
                ConstantsEventProperties.FROM_BACKGROUND to isFromBackground,
                ConstantsEventProperties.VERSION to currentVersion,
                ConstantsEventProperties.BUILD to currentBuild,
            ),
        )
    }

    fun trackAppBackgroundedEvent() {
        amplitude.track(ConstantsEventTypes.APPLICATION_BACKGROUNDED)
    }

    fun trackDeepLinkOpenedEvent(activity: Activity) {
        val intent = activity.intent
        intent?.let {
            val referrer = getReferrer(activity)?.toString()
            it.data?.let { uri ->
                val url = uri.toString()
                amplitude.track(
                    ConstantsEventTypes.DEEP_LINK_OPENED,
                    mapOf(
                        ConstantsEventProperties.LINK_URL to url,
                        ConstantsEventProperties.LINK_REFERRER to referrer,
                    ),
                )
            }
        }
    }

    fun trackScreenViewedEvent(activity: Activity) {
        try {
            amplitude.track(
                ConstantsEventTypes.SCREEN_VIEWED,
                mapOf(
                    ConstantsEventProperties.SCREEN_NAME to activity.screenName,
                ),
            )
        } catch (e: Exception) {
            amplitude.logger.error("Failed to track screen viewed event: $e")
        }
    }

    private val isFragmentActivityAvailable by lazy {
        LoadClass.isClassAvailable(FRAGMENT_ACTIVITY_CLASS_NAME, amplitude.logger)
    }

    fun startFragmentViewedEventTracking(
        activity: Activity,
        screenViewsEnabled: () -> Boolean = { true },
    ) {
        if (isFragmentActivityAvailable) {
            activity.registerFragmentLifecycleCallbacks(amplitude::track, amplitude.logger, screenViewsEnabled)
        }
    }

    fun stopFragmentViewedEventTracking(activity: Activity) {
        if (isFragmentActivityAvailable) {
            activity.unregisterFragmentLifecycleCallbacks(amplitude.logger)
        }
    }

    companion object {
        private const val FRAGMENT_ACTIVITY_CLASS_NAME = "androidx.fragment.app.FragmentActivity"
        internal val Activity.screenName: String
            get() {
                try {
                    val info =
                        packageManager.getActivityInfo(
                            componentName,
                            PackageManager.GET_META_DATA,
                        )

                    // 1. Activity title first
                    if (title.isNotBlank()) {
                        return title.toString()
                    }

                    // 2. Return label from activity info
                    if (info.labelRes != 0) {
                        return getString(info.labelRes)
                    } else if (info.nonLocalizedLabel.isNotBlank()) {
                        return info.nonLocalizedLabel.toString()
                    }

                    // 3. Fall back to activity name
                    if (info.name.isNotBlank()) {
                        return info.name
                    }

                    // 4. Fall back to activity class name
                    return localClassName
                } catch (e: Exception) {
                    // 3. Fall back to application name
                    return applicationInfo.loadLabel(packageManager).toString()
                }
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

    @Deprecated("This object is deprecated. Use Constants.EventTypes.* instead.")
    object EventTypes {
        @Deprecated(
            "Use Constants.EventTypes.APPLICATION_INSTALLED instead",
            ReplaceWith(
                "Constants.EventTypes.APPLICATION_INSTALLED",
                "com.amplitude.android.Constants.EventTypes.APPLICATION_INSTALLED",
            ),
        )
        const val APPLICATION_INSTALLED = Constants.EventTypes.APPLICATION_INSTALLED

        @Deprecated(
            "Use Constants.EventTypes.APPLICATION_UPDATED instead",
            ReplaceWith(
                "Constants.EventTypes.APPLICATION_UPDATED",
                "com.amplitude.android.Constants.EventTypes.APPLICATION_UPDATED",
            ),
        )
        const val APPLICATION_UPDATED = Constants.EventTypes.APPLICATION_UPDATED

        @Deprecated(
            "Use Constants.EventTypes.APPLICATION_OPENED instead",
            ReplaceWith(
                "Constants.EventTypes.APPLICATION_OPENED",
                "com.amplitude.android.Constants.EventTypes.APPLICATION_OPENED",
            ),
        )
        const val APPLICATION_OPENED = Constants.EventTypes.APPLICATION_OPENED

        @Deprecated(
            "Use Constants.EventTypes.APPLICATION_BACKGROUNDED instead",
            ReplaceWith(
                "Constants.EventTypes.APPLICATION_BACKGROUNDED",
                "com.amplitude.android.Constants.EventTypes.APPLICATION_BACKGROUNDED",
            ),
        )
        const val APPLICATION_BACKGROUNDED = Constants.EventTypes.APPLICATION_BACKGROUNDED

        @Deprecated(
            "Use Constants.EventTypes.DEEP_LINK_OPENED instead",
            ReplaceWith(
                "Constants.EventTypes.DEEP_LINK_OPENED",
                "com.amplitude.android.Constants.EventTypes.DEEP_LINK_OPENED",
            ),
        )
        const val DEEP_LINK_OPENED = Constants.EventTypes.DEEP_LINK_OPENED

        @Deprecated(
            "Use Constants.EventTypes.SCREEN_VIEWED instead",
            ReplaceWith(
                "Constants.EventTypes.SCREEN_VIEWED",
                "com.amplitude.android.Constants.EventTypes.SCREEN_VIEWED",
            ),
        )
        const val SCREEN_VIEWED = Constants.EventTypes.SCREEN_VIEWED

        @Deprecated(
            "Use Constants.EventTypes.FRAGMENT_VIEWED instead",
            ReplaceWith(
                "Constants.EventTypes.FRAGMENT_VIEWED",
                "com.amplitude.android.Constants.EventTypes.FRAGMENT_VIEWED",
            ),
        )
        const val FRAGMENT_VIEWED = Constants.EventTypes.FRAGMENT_VIEWED

        @Deprecated(
            "Use Constants.EventTypes.ELEMENT_INTERACTED instead",
            ReplaceWith(
                "Constants.EventTypes.ELEMENT_INTERACTED",
                "com.amplitude.android.Constants.EventTypes.ELEMENT_INTERACTED",
            ),
        )
        const val ELEMENT_INTERACTED = Constants.EventTypes.ELEMENT_INTERACTED
    }

    @Deprecated("This object is deprecated. Use Constants.EventProperties.* instead.")
    object EventProperties {
        @Deprecated(
            "Use Constants.EventProperties.VERSION instead",
            ReplaceWith(
                "Constants.EventProperties.VERSION",
                "com.amplitude.android.Constants.EventProperties.VERSION",
            ),
        )
        const val VERSION = Constants.EventProperties.VERSION

        @Deprecated(
            "Use Constants.EventProperties.BUILD instead",
            ReplaceWith(
                "Constants.EventProperties.BUILD",
                "com.amplitude.android.Constants.EventProperties.BUILD",
            ),
        )
        const val BUILD = Constants.EventProperties.BUILD

        @Deprecated(
            "Use Constants.EventProperties.PREVIOUS_VERSION instead",
            ReplaceWith(
                "Constants.EventProperties.PREVIOUS_VERSION",
                "com.amplitude.android.Constants.EventProperties.PREVIOUS_VERSION",
            ),
        )
        const val PREVIOUS_VERSION = Constants.EventProperties.PREVIOUS_VERSION

        @Deprecated(
            "Use Constants.EventProperties.PREVIOUS_BUILD instead",
            ReplaceWith(
                "Constants.EventProperties.PREVIOUS_BUILD",
                "com.amplitude.android.Constants.EventProperties.PREVIOUS_BUILD",
            ),
        )
        const val PREVIOUS_BUILD = Constants.EventProperties.PREVIOUS_BUILD

        @Deprecated(
            "Use Constants.EventProperties.FROM_BACKGROUND instead",
            ReplaceWith(
                "Constants.EventProperties.FROM_BACKGROUND",
                "com.amplitude.android.Constants.EventProperties.FROM_BACKGROUND",
            ),
        )
        const val FROM_BACKGROUND = Constants.EventProperties.FROM_BACKGROUND

        @Deprecated(
            "Use Constants.EventProperties.LINK_URL instead",
            ReplaceWith(
                "Constants.EventProperties.LINK_URL",
                "com.amplitude.android.Constants.EventProperties.LINK_URL",
            ),
        )
        const val LINK_URL = Constants.EventProperties.LINK_URL

        @Deprecated(
            "Use Constants.EventProperties.LINK_REFERRER instead",
            ReplaceWith(
                "Constants.EventProperties.LINK_REFERRER",
                "com.amplitude.android.Constants.EventProperties.LINK_REFERRER",
            ),
        )
        const val LINK_REFERRER = Constants.EventProperties.LINK_REFERRER

        @Deprecated(
            "Use Constants.EventProperties.SCREEN_NAME instead",
            ReplaceWith(
                "Constants.EventProperties.SCREEN_NAME",
                "com.amplitude.android.Constants.EventProperties.SCREEN_NAME",
            ),
        )
        const val SCREEN_NAME = Constants.EventProperties.SCREEN_NAME

        @Deprecated(
            "Use Constants.EventProperties.FRAGMENT_CLASS instead",
            ReplaceWith(
                "Constants.EventProperties.FRAGMENT_CLASS",
                "com.amplitude.android.Constants.EventProperties.FRAGMENT_CLASS",
            ),
        )
        const val FRAGMENT_CLASS = Constants.EventProperties.FRAGMENT_CLASS

        @Deprecated(
            "Use Constants.EventProperties.FRAGMENT_IDENTIFIER instead",
            ReplaceWith(
                "Constants.EventProperties.FRAGMENT_IDENTIFIER",
                "com.amplitude.android.Constants.EventProperties.FRAGMENT_IDENTIFIER",
            ),
        )
        const val FRAGMENT_IDENTIFIER = Constants.EventProperties.FRAGMENT_IDENTIFIER

        @Deprecated(
            "Use Constants.EventProperties.FRAGMENT_TAG instead",
            ReplaceWith(
                "Constants.EventProperties.FRAGMENT_TAG",
                "com.amplitude.android.Constants.EventProperties.FRAGMENT_TAG",
            ),
        )
        const val FRAGMENT_TAG = Constants.EventProperties.FRAGMENT_TAG

        @Deprecated(
            "Use Constants.EventProperties.ACTION instead",
            ReplaceWith(
                "Constants.EventProperties.ACTION",
                "com.amplitude.android.Constants.EventProperties.ACTION",
            ),
        )
        const val ACTION = Constants.EventProperties.ACTION

        @Deprecated(
            "Use Constants.EventProperties.TARGET_CLASS instead",
            ReplaceWith(
                "Constants.EventProperties.TARGET_CLASS",
                "com.amplitude.android.Constants.EventProperties.TARGET_CLASS",
            ),
        )
        const val TARGET_CLASS = Constants.EventProperties.TARGET_CLASS

        @Deprecated(
            "Use Constants.EventProperties.TARGET_RESOURCE instead",
            ReplaceWith(
                "Constants.EventProperties.TARGET_RESOURCE",
                "com.amplitude.android.Constants.EventProperties.TARGET_RESOURCE",
            ),
        )
        const val TARGET_RESOURCE = Constants.EventProperties.TARGET_RESOURCE

        @Deprecated(
            "Use Constants.EventProperties.TARGET_TAG instead",
            ReplaceWith(
                "Constants.EventProperties.TARGET_TAG",
                "com.amplitude.android.Constants.EventProperties.TARGET_TAG",
            ),
        )
        const val TARGET_TAG = Constants.EventProperties.TARGET_TAG

        @Deprecated(
            "Use Constants.EventProperties.TARGET_TEXT instead",
            ReplaceWith(
                "Constants.EventProperties.TARGET_TEXT",
                "com.amplitude.android.Constants.EventProperties.TARGET_TEXT",
            ),
        )
        const val TARGET_TEXT = Constants.EventProperties.TARGET_TEXT

        @Deprecated(
            "Use Constants.EventProperties.TARGET_SOURCE instead",
            ReplaceWith(
                "Constants.EventProperties.TARGET_SOURCE",
                "com.amplitude.android.Constants.EventProperties.TARGET_SOURCE",
            ),
        )
        const val TARGET_SOURCE = Constants.EventProperties.TARGET_SOURCE

        @Deprecated(
            "Use Constants.EventProperties.HIERARCHY instead",
            ReplaceWith(
                "Constants.EventProperties.HIERARCHY",
                "com.amplitude.android.Constants.EventProperties.HIERARCHY",
            ),
        )
        const val HIERARCHY = Constants.EventProperties.HIERARCHY
    }
}

internal fun PackageInfo.getVersionCode(): Number =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        this.longVersionCode
    } else {
        @Suppress("DEPRECATION")
        this.versionCode
    }
