package com.amplitude.android.utilities

import com.amplitude.common.Logger

object ComposeUtils {
    private val loadClass = LoadClass()
    private const val COMPOSE_CLASS_NAME = "androidx.compose.ui.node.Owner"
    private const val COMPOSE_GESTURE_LOCATOR_CLASS_NAME =
        "com.amplitude.android.internal.locators.ComposeViewTargetLocator"

    fun isComposeAvailable(logger: Logger? = null): Boolean {
        return loadClass.isClassAvailable(COMPOSE_CLASS_NAME, logger) &&
            loadClass.isClassAvailable(COMPOSE_GESTURE_LOCATOR_CLASS_NAME, logger)
    }
}
