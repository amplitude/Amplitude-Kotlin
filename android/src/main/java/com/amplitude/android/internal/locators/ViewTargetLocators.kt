package com.amplitude.android.internal.locators

import com.amplitude.android.utilities.ComposeUtils
import com.amplitude.common.Logger

internal object ViewTargetLocators {
    /**
     * A list [ViewTargetLocator]s for classic Android [View][android.view.View]s and Jetpack
     * Compose.
     *
     * @param logger the logger to use for logging.
     * @return a list of [ViewTargetLocator]s.
     */
    val ALL: (Logger) -> List<ViewTargetLocator> by lazy {
        { logger ->
            mutableListOf<ViewTargetLocator>().apply {
                if (ComposeUtils.isComposeAvailable()) {
                    add(ComposeViewTargetLocator(logger))
                }
                add(AndroidViewTargetLocator())
            }
        }
    }
}
