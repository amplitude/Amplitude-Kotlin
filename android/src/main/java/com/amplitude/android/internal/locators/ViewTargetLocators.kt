package com.amplitude.android.internal.locators

import com.amplitude.android.compose.ComposeViewTargetLocator
import com.amplitude.android.utilities.LoadClass
import com.amplitude.common.Logger
import com.amplitude.common.internal.gesture.ViewTargetLocator

internal object ViewTargetLocators {
    private const val COMPOSE_CLASS_NAME = "androidx.compose.ui.node.Owner"
    private const val COMPOSE_GESTURE_LOCATOR_CLASS_NAME =
        "com.amplitude.android.compose.ComposeViewTargetLocator"

    /**
     * A list [ViewTargetLocator]s for classic Android [View][android.view.View]s and Jetpack
     * Compose.
     *
     * @param logger the logger to use for logging.
     * @return a list of [ViewTargetLocator]s.
     */
    @JvmField
    val ALL: (Logger) -> List<ViewTargetLocator> = { logger ->
        mutableListOf<ViewTargetLocator>().apply {
            val loadClass = LoadClass()
            val isComposeUpstreamAvailable = loadClass.isClassAvailable(COMPOSE_CLASS_NAME, logger)
            val isComposeAvailable =
                isComposeUpstreamAvailable &&
                    loadClass.isClassAvailable(COMPOSE_GESTURE_LOCATOR_CLASS_NAME, logger)

            if (isComposeAvailable) {
                add(ComposeViewTargetLocator(logger))
            }
            add(AndroidViewTargetLocator())
        }
    }
}
