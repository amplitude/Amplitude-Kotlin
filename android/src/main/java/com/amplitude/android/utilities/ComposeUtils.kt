package com.amplitude.android.utilities

import com.amplitude.common.Logger

internal object ComposeUtils {
    private const val COMPOSE_CLASS_NAME = "androidx.compose.ui.node.Owner"
    private const val COMPOSE_GESTURE_LOCATOR_CLASS_NAME =
        "com.amplitude.android.internal.locators.ComposeViewTargetLocator"
    private const val COMPOSE_RUNTIME_VERSION_RESOURCE = "META-INF/androidx.compose.runtime_runtime.version"
    private const val COMPOSE_UI_VERSION_RESOURCE = "META-INF/androidx.compose.ui_ui.version"
    private const val COMPOSE_VERSION_CLASS = "androidx.compose.runtime.ComposeVersion"

    fun isComposeAvailable(logger: Logger? = null): Boolean {
        return LoadClass.isClassAvailable(COMPOSE_CLASS_NAME, logger) &&
            LoadClass.isClassAvailable(COMPOSE_GESTURE_LOCATOR_CLASS_NAME, logger)
    }

    fun getComposeRuntimeVersion(logger: Logger? = null): String? {
        return readVersionFromMetaInf(COMPOSE_RUNTIME_VERSION_RESOURCE, logger)
    }

    fun getComposeUiVersion(logger: Logger? = null): String? {
        return readVersionFromMetaInf(COMPOSE_UI_VERSION_RESOURCE, logger)
    }

    /**
     * Reads the Compose runtime compatibility version code from
     * `androidx.compose.runtime.ComposeVersion.version` via reflection.
     * This is an integer (e.g. 14100) that the Compose compiler checks
     * against and may still be available even if META-INF version files
     * have been stripped.
     */
    fun getComposeRuntimeCompatibilityVersionCode(logger: Logger? = null): Int? {
        return try {
            val clazz = Class.forName(COMPOSE_VERSION_CLASS)
            val field = clazz.getDeclaredField("version")
            field.getInt(null)
        } catch (e: Exception) {
            logger?.debug("Failed to read ComposeVersion.version via reflection: $e")
            null
        }
    }

    private fun readVersionFromMetaInf(
        resourcePath: String,
        logger: Logger? = null,
    ): String? {
        return try {
            val stream =
                ComposeUtils::class.java.classLoader
                    ?.getResourceAsStream(resourcePath)
                    ?: return null
            stream.bufferedReader().use { it.readLine()?.trim() }
        } catch (e: Exception) {
            logger?.debug("Failed to read version from $resourcePath: $e")
            null
        }
    }
}
