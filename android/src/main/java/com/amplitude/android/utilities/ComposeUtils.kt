package com.amplitude.android.utilities

import com.amplitude.common.Logger

internal object ComposeUtils {
    private const val COMPOSE_CLASS_NAME = "androidx.compose.ui.node.Owner"
    private const val COMPOSE_GESTURE_LOCATOR_CLASS_NAME =
        "com.amplitude.android.internal.locators.ComposeViewTargetLocator"

    fun isComposeAvailable(logger: Logger? = null): Boolean {
        return LoadClass.isClassAvailable(COMPOSE_CLASS_NAME, logger) &&
            LoadClass.isClassAvailable(COMPOSE_GESTURE_LOCATOR_CLASS_NAME, logger)
    }
}
