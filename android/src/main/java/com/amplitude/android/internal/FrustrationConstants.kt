package com.amplitude.android.internal

/**
 * Constants used for frustration analytics functionality.
 */
internal object FrustrationConstants {
    /**
     * Tag value to mark Android Views as ignored for ALL frustration analytics.
     * Usage: view.tag = IGNORE_FRUSTRATION_TAG
     */
    const val IGNORE_FRUSTRATION_TAG = "amplitude_ignore_frustration"

    /**
     * Tag value to mark Android Views as ignored for RAGE CLICK detection only.
     * Usage: view.tag = IGNORE_RAGE_CLICK_TAG
     */
    const val IGNORE_RAGE_CLICK_TAG = "amplitude_ignore_rage_click"

    /**
     * Tag value to mark Android Views as ignored for DEAD CLICK detection only.
     * Usage: view.tag = IGNORE_DEAD_CLICK_TAG
     */
    const val IGNORE_DEAD_CLICK_TAG = "amplitude_ignore_dead_click"


}
