package com.amplitude.id.utilities

import com.amplitude.common.Logger
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.Properties

class PropertiesFile(directory: File, key: String, prefix: String, logger: Logger?) : KeyValueStore {
    internal var underlyingProperties: Properties = Properties()
    private val propertiesFileName = "$prefix-$key.properties"
    private val propertiesFile = File(directory, propertiesFileName)
    private val logger = logger

    /**
     * Check if underlying file exists, and load properties if true
     */
    fun load() {
        if (propertiesFile.exists()) {
            try {
                FileInputStream(propertiesFile).use {
                    underlyingProperties.load(it)
                }
                return
            } catch (e: Throwable) {
                propertiesFile.delete()
                logger?.error("Failed to load property file with path ${propertiesFile.absolutePath}, error stacktrace: ${e.stackTraceToString()}")
            }
        }
        propertiesFile.parentFile.mkdirs()
        propertiesFile.createNewFile()
    }

    private fun save() {
        try {
            FileOutputStream(propertiesFile).use {
                underlyingProperties.store(it, null)
            }
        } catch (e: Throwable) {
            // Note: we need to catch Throwable to handle both Exceptions and Errors
            // Properties.store has an error in Android 8 that throws a AssertionError (vs Exception)
            logger?.error("Failed to save property file with path ${propertiesFile.absolutePath}, error stacktrace: ${e.stackTraceToString()}")
        }
    }

    override fun getLong(key: String, defaultVal: Long): Long =
        underlyingProperties.getProperty(key, "").toLongOrNull() ?: defaultVal

    override fun putLong(key: String, value: Long): Boolean {
        underlyingProperties.setProperty(key, value.toString())
        save()
        return true
    }

    fun putString(key: String, value: String): Boolean {
        underlyingProperties.setProperty(key, value)
        save()
        return true
    }

    fun getString(key: String, defaultVal: String?): String? =
        underlyingProperties.getProperty(key, defaultVal)

    fun remove(key: String): Boolean {
        underlyingProperties.remove(key)
        save()
        return true
    }

    fun remove(keys: List<String>): Boolean {
        keys.forEach {
            underlyingProperties.remove(it)
        }
        save()
        return true
    }
}

/**
 * Key-value store interface
 */
interface KeyValueStore {
    fun getLong(key: String, defaultVal: Long): Long
    fun putLong(key: String, value: Long): Boolean
}
