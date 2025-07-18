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

    /**
     * Test tag value to mark Compose elements as ignored for ALL frustration analytics.
     * Usage: Modifier.testTag(IGNORE_FRUSTRATION_COMPOSE_TAG)
     */
    const val IGNORE_FRUSTRATION_COMPOSE_TAG = "amplitude_ignore_frustration"

    /**
     * Test tag value to mark Compose elements as ignored for RAGE CLICK detection only.
     * Usage: Modifier.testTag(IGNORE_RAGE_CLICK_COMPOSE_TAG)
     */
    const val IGNORE_RAGE_CLICK_COMPOSE_TAG = "amplitude_ignore_rage_click"

    /**
     * Test tag value to mark Compose elements as ignored for DEAD CLICK detection only.
     * Usage: Modifier.testTag(IGNORE_DEAD_CLICK_COMPOSE_TAG)
     */
    const val IGNORE_DEAD_CLICK_COMPOSE_TAG = "amplitude_ignore_dead_click"
}
