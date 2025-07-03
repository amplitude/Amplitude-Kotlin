package com.amplitude.android.internal.fragments

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import com.amplitude.android.utilities.DefaultEventUtils.Companion.screenName
import com.amplitude.common.Logger
import com.amplitude.core.Constants.EventProperties
import com.amplitude.core.Constants.EventTypes

internal class AutocaptureFragmentLifecycleCallbacks(
    private val track: TrackEventCallback,
    private val logger: Logger,
) : FragmentManager.FragmentLifecycleCallbacks() {
    override fun onFragmentResumed(
        fm: FragmentManager,
        f: Fragment,
    ) {
        super.onFragmentResumed(fm, f)

        val className = f.javaClass.canonicalName ?: f.javaClass.simpleName ?: null
        val fragmentIdentifier =
            runCatching {
                // The id could be the `android:id` value supplied in a layout or the container view ID
                // supplied when adding the fragment.
                f.resources.getResourceEntryName(f.id)
            }.onFailure {
                logger.error("Failed to get resource entry name: $it")
            }.getOrNull()
        val screenName =
            runCatching {
                f.activity?.screenName
            }.onFailure {
                logger.error("Failed to get screen name: $it")
            }.getOrNull()
        val fragmentTag = f.tag

        track(
            EventTypes.FRAGMENT_VIEWED,
            mapOf(
                EventProperties.FRAGMENT_CLASS to className,
                EventProperties.FRAGMENT_IDENTIFIER to fragmentIdentifier,
                EventProperties.SCREEN_NAME to screenName,
                EventProperties.FRAGMENT_TAG to fragmentTag,
            ),
        )
    }
}
