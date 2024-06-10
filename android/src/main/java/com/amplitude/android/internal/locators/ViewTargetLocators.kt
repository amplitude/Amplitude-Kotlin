package com.amplitude.android.internal.locators

import com.amplitude.android.utilities.LoadClass
import com.amplitude.common.Logger

internal object ViewTargetLocators {
    private const val COMPOSE_CLASS_NAME = "androidx.compose.ui.node.Owner"
    private const val SCROLLING_VIEW_CLASS_NAME = "androidx.core.view.ScrollingView"
    private const val COMPOSE_GESTURE_LOCATOR_CLASS_NAME =
        "com.amplitude.android.internal.locators.ComposeViewTargetLocator"

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
                val loadClass = LoadClass()
                val isComposeUpstreamAvailable = loadClass.isClassAvailable(COMPOSE_CLASS_NAME, logger)
                val isComposeAvailable =
                    isComposeUpstreamAvailable &&
                        loadClass.isClassAvailable(COMPOSE_GESTURE_LOCATOR_CLASS_NAME, logger)
                val isAndroidXScrollViewAvailable =
                    loadClass.isClassAvailable(SCROLLING_VIEW_CLASS_NAME, logger)

                if (isComposeAvailable) {
                    add(ComposeViewTargetLocator(logger))
                }
                add(AndroidViewTargetLocator(isAndroidXScrollViewAvailable))
            }
        }
    }
}
