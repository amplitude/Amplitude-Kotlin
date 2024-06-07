package com.amplitude.android.utilities

import com.amplitude.common.Logger

/** An Adapter for making Class.forName testable  */
class LoadClass {
    /**
     * Try to load a class via reflection
     *
     * @param clazz the full class name
     * @param logger an instance of ILogger
     * @return a Class if it's available, or null
     */
    private fun loadClass(
        clazz: String,
        logger: Logger?,
    ): Class<*>? {
        try {
            return Class.forName(clazz)
        } catch (e: ClassNotFoundException) {
            logger?.debug("Class not available:$clazz: $e")
        } catch (e: UnsatisfiedLinkError) {
            logger?.error("Failed to load (UnsatisfiedLinkError) $clazz: $e")
        } catch (e: Throwable) {
            logger?.error("Failed to initialize $clazz: $e")
        }
        return null
    }

    fun isClassAvailable(
        clazz: String,
        logger: Logger?,
    ): Boolean {
        return loadClass(clazz, logger) != null
    }
}
