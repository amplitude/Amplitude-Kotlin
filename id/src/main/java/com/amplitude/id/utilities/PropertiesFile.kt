package com.amplitude.id.utilities

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.Properties

class PropertiesFile(directory: File, key: String, prefix: String) : KeyValueStore {
    private val underlyingProperties: Properties = Properties()
    private val propertiesFileName = "$prefix-$key.properties"
    private val propertiesFile = File(directory, propertiesFileName)

    /**
     * Check if underlying file exists, and load properties if true
     */
    fun load() {
        if (propertiesFile.exists()) {
            FileInputStream(propertiesFile).use {
                underlyingProperties.load(it)
            }
        } else {
            propertiesFile.parentFile.mkdirs()
            propertiesFile.createNewFile()
        }
    }

    private fun save() {
        FileOutputStream(propertiesFile).use {
            underlyingProperties.store(it, null)
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
