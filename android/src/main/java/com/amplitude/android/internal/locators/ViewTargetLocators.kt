package com.amplitude.android.internal.locators

import com.amplitude.common.Logger

internal object ViewTargetLocators {
    /**
     * A list [ViewTargetLocator]s for classic Android [View][android.view.View]s and Jetpack
     * Compose.
     *
     * @param logger the logger to use for logging.
     * @return a list of [ViewTargetLocator]s.
     */
    @JvmField
    val ALL: (Logger) -> List<ViewTargetLocator> = { logger ->
        listOf(
            ComposeViewTargetLocator(logger),
            AndroidViewTargetLocator(),
        )
    }
}
