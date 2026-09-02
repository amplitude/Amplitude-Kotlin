package com.amplitude.android.plugins

import com.amplitude.core.Amplitude
import com.amplitude.core.platform.Plugin

private const val STREAMING_ANALYTICS_PLUGIN =
    "com.amplitude.android.streaming.StreamingAnalyticsPlugin"

/**
 * Optional Amplitude plugins loaded by class name when their artifact is on the classpath.
 */
internal object OptionalClasspathPlugins {
    fun install(amplitude: Amplitude) {
        val plugin =
            try {
                Class.forName(STREAMING_ANALYTICS_PLUGIN)
                    .getDeclaredConstructor()
                    .newInstance() as? Plugin
            } catch (_: ClassNotFoundException) {
                return
            } catch (error: Throwable) {
                amplitude.logger.error(
                    "Failed to install StreamingAnalyticsPlugin: ${error.message}",
                )
                return
            } ?: return
        amplitude.add(plugin)
    }
}
