package com.amplitude.core.remoteconfig

import com.amplitude.common.Logger
import com.amplitude.core.Constants.SDK_LIBRARY
import com.amplitude.core.Constants.SDK_VERSION
import com.amplitude.core.ServerZone
import com.amplitude.core.Storage
import com.amplitude.core.Storage.Constants.REMOTE_CONFIG
import com.amplitude.core.Storage.Constants.REMOTE_CONFIG_TIMESTAMP
import com.amplitude.core.remoteconfig.RemoteConfigClient.Key
import com.amplitude.core.remoteconfig.RemoteConfigClient.Source.CACHE
import com.amplitude.core.remoteconfig.RemoteConfigClient.Source.REMOTE
import com.amplitude.core.utilities.http.HttpClient
import com.amplitude.core.utilities.toJSONObject
import com.amplitude.core.utilities.toMapObj
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.lang.ref.WeakReference
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Type alias for remote configuration map data.
 * Represents flattened config key-value pairs where keys are strings and values can be any type.
 */
typealias ConfigMap = Map<String, Any>

/**
 * Interface for remote configuration client.
 */
interface RemoteConfigClient {
    enum class Source {
        CACHE,
        REMOTE,
    }

    enum class Key(val value: String) {
        DIAGNOSTICS("diagnostics.androidSDK"),
        SESSION_REPLAY_PRIVACY_CONFIG("sessionReplay.sr_android_privacy_config"),
        SESSION_REPLAY_SAMPLING_CONFIG("sessionReplay.sr_android_sampling_config"),
    }

    /**
     * Subscribe to remote configuration updates for a specific configuration key.
     * The callback will be invoked whenever the specific configuration is updated.
     *
     * @param key The configuration key to subscribe to (e.g., "sessionReplay.sr_android_sampling_config")
     * @param callback RemoteConfigCallback that handles config updates
     */
    fun subscribe(
        key: Key,
        callback: RemoteConfigCallback,
    )

    fun updateConfigs()

    fun interface RemoteConfigCallback {
        fun onUpdate(
            config: ConfigMap,
            source: Source,
            timestamp: Long,
        )
    }
}

/**
 * Implementation of remote configuration client that manages fetching and caching
 * of configuration from remote sources with subscription support.
 */
internal class RemoteConfigClientImpl(
    private val apiKey: String,
    private val serverZone: ServerZone,
    private val coroutineScope: CoroutineScope,
    private val networkIODispatcher: CoroutineDispatcher,
    private val storageIODispatcher: CoroutineDispatcher,
    private val storage: Storage,
    private val httpClient: HttpClient,
    private val logger: Logger,
) : RemoteConfigClient {
    /**
     * Data class for storing config data with timestamp
     */
    private data class ConfigData(
        val config: ConfigMap,
        val timestamp: Long,
    )

    companion object {
        // Remote config endpoints based on server zone
        private const val US_REMOTE_CONFIG_URL = "https://sr-client-cfg.amplitude.com"
        private const val EU_REMOTE_CONFIG_URL = "https://sr-client-cfg.eu.amplitude.com"
        private const val MIN_FETCH_INTERVAL_MS: Long = 5 * 60 * 1_000L
    }

    /**
     * Wrapper class for weak reference callbacks to prevent memory leaks
     */
    private inner class WeakCallback(
        callback: RemoteConfigClient.RemoteConfigCallback,
    ) {
        private val weakRef = WeakReference(callback)

        fun isAlive(): Boolean = weakRef.get() != null

        fun runSafely(action: RemoteConfigClient.RemoteConfigCallback.() -> Unit) {
            val callback = weakRef.get() ?: return
            try {
                callback.action()
            } catch (e: Exception) {
                logger.error("Exception in subscriber callback: ${e.message}")
            }
        }
    }

    // Subscribers for specific config keys using weak references to prevent memory leaks
    private val keySpecificSubscribers =
        ConcurrentHashMap<String, CopyOnWriteArrayList<WeakCallback>>()

    // Simple in-flight fetch guard; safe because networkIODispatcher is single-threaded
    private var isFetching: Boolean = false

    /**
     * Subscribe to remote configuration updates for a specific configuration key.
     * The callback will be invoked whenever the specific configuration is updated.
     *
     * @param key The configuration key to subscribe to
     * @param callback RemoteConfigCallback that handles config updates
     */
    override fun subscribe(
        key: Key,
        callback: RemoteConfigClient.RemoteConfigCallback,
    ) {
        // Clean up dead weak references before adding new one
        cleanupDeadReferences()

        // Add new weak reference callback
        val subscriberList =
            keySpecificSubscribers.getOrPut(key.value) {
                CopyOnWriteArrayList<WeakCallback>()
            }
        val weakCallback = WeakCallback(callback)
        subscriberList.add(weakCallback)
        keySpecificSubscribers[key.value] = subscriberList

        logger.debug("Added subscriber for key: ${key.value}. Total subscribers: ${keySpecificSubscribers[key.value]?.size}")

        // Immediately provide stored config if available
        val storedData = getStoredConfigData(key.value)
        if (storedData != null) {
            // Notify only the newly added subscriber
            weakCallback.runSafely {
                onUpdate(storedData.config, CACHE, storedData.timestamp)
            }
        }

        // Trigger remote fetch to ensure latest config is available
        updateConfigs()
    }

    /**
     * Trigger a remote fetch for all config keys
     */
    override fun updateConfigs() {
        coroutineScope.launch(networkIODispatcher) {
            try {
                if (isFetching) {
                    logger.debug("RemoteConfig update skipped: fetch already in progress")
                    return@launch
                }

                if (shouldRateLimit()) {
                    logger.debug("RemoteConfig update skipped: within 5-minute window")
                    return@launch
                }

                isFetching = true
                val configs = fetchRemoteConfig() ?: return@launch

                val timestamp = System.currentTimeMillis()
                withContext(storageIODispatcher) {
                    storage.write(REMOTE_CONFIG, configs.toJSONObject().toString())
                    storage.write(REMOTE_CONFIG_TIMESTAMP, timestamp.toString())
                    logger.debug("Successfully stored remote configs to storage")
                }

                configs.forEach { (configKey, config) ->
                    // Notify all subscribers for this config key
                    val subscriberList = keySpecificSubscribers[configKey]
                    subscriberList?.forEach { weakCallback ->
                        weakCallback.runSafely {
                            onUpdate(config, REMOTE, timestamp)
                        }
                    }
                }
            } catch (e: Exception) {
                logger.error("Error updating remote configs: ${e.message}")
            } finally {
                isFetching = false
            }
        }
    }

    private suspend fun shouldRateLimit(): Boolean {
        val lastTsStr = withContext(storageIODispatcher) { storage.read(REMOTE_CONFIG_TIMESTAMP) }
        val lastTs = lastTsStr?.toLongOrNull() ?: 0L
        if (lastTs <= 0L) return false
        val now = System.currentTimeMillis()
        return (now - lastTs) < MIN_FETCH_INTERVAL_MS
    }

    /**
     * Retrieve stored configuration data for a specific key.
     * This method checks if the storage has consistent data for all expected keys.
     */
    private fun getStoredConfigData(configKey: String): ConfigData? {
        return try {
            val allStoredConfigs = getAllStoredConfigs()

            // If storage is completely empty, return empty config
            if (allStoredConfigs.isEmpty()) {
                return null
            }

            // Check if storage has consistent data for all expected keys
            val expectedKeys = Key.entries.map { it.value }.toSet()
            val storedKeys = allStoredConfigs.keys
            if (!storedKeys.containsAll(expectedKeys)) {
                logger.debug(
                    "Storage inconsistent keys. Expected: $expectedKeys, Found: $storedKeys",
                )
                // Middle ground: invalidate only if none of the expected keys exist (hard inconsistency)
                val hasAnyExpected = storedKeys.any { it in expectedKeys }
                if (!hasAnyExpected) {
                    coroutineScope.launch(storageIODispatcher) {
                        try {
                            storage.remove(REMOTE_CONFIG)
                            storage.remove(REMOTE_CONFIG_TIMESTAMP)
                            logger.debug("Cleared storage due to hard inconsistency (no expected keys present)")
                        } catch (e: Exception) {
                            logger.error("Failed to clear inconsistent storage: ${e.message}")
                        }
                    }
                    return null
                }
                // If at least one expected key exists, keep partial data and proceed.
            }

            val configData = allStoredConfigs[configKey] ?: emptyMap()
            val timestampStr = storage.read(REMOTE_CONFIG_TIMESTAMP)
            val timestamp = timestampStr?.toLongOrNull() ?: 0L

            logger.debug("Retrieved stored config for $configKey with ${configData.size} properties")
            ConfigData(configData, timestamp)
        } catch (e: Exception) {
            logger.error("Failed to retrieve stored config data for $configKey: ${e.message}")
            null
        }
    }

    private fun getAllStoredConfigs(): Map<String, ConfigMap> {
        return try {
            val configJson = storage.read(REMOTE_CONFIG)
            if (configJson.isNullOrBlank()) {
                logger.debug("No stored config found in storage")
                return emptyMap()
            }

            val allConfigs = JSONObject(configJson).toMapObj()

            // Parse each stored config and build the result map
            buildMap {
                for ((key, value) in allConfigs) {
                    when (value) {
                        is Map<*, *> -> {
                            @Suppress("UNCHECKED_CAST")
                            val configMap =
                                (value as? ConfigMap)?.filterNotNullValues() ?: emptyMap()
                            if (configMap.isNotEmpty()) {
                                put(key, configMap)
                                logger.debug("Successfully loaded stored config for key: $key")
                            }
                        }

                        else -> {
                            logger.debug(
                                "Skipping non-map value for key $key: ${value?.javaClass?.simpleName}",
                            )
                        }
                    }
                }
            }.also {
                logger.debug("Successfully loaded ${it.size} stored configs from storage")
            }
        } catch (e: Exception) {
            logger.error("Failed to parse all stored configs: ${e.message}")
            emptyMap()
        }
    }

    private fun fetchRemoteConfig(): Map<String, ConfigMap>? {
        // Periodic cleanup of dead references during network operations
        cleanupDeadReferences()

        val url = buildRemoteConfigUrl()
        val request =
            HttpClient.Request(
                url = url,
                method = HttpClient.Request.Method.GET,
                headers = buildRequestHeaders(),
            )

        val response = httpClient.request(request)

        return when {
            response.isSuccessful -> parseConfigsFromResponse(response.body)
            response.isClientError -> {
                logger.error(
                    "Client error on fetch remote config: ${response.statusCode}: ${response.statusMessage}",
                )
                null
            }
            else -> {
                logger.warn(
                    "Failed to fetch remote config: ${response.statusCode}: ${response.statusMessage}",
                )
                null
            }
        }
    }

    /**
     * Clean up dead weak references to prevent memory accumulation.
     */
    private fun cleanupDeadReferences() {
        var totalCleaned = 0
        val keysToRemove = mutableSetOf<String>()

        keySpecificSubscribers.forEach { (keyName, subscriberList) ->
            val initialSize = subscriberList.size
            subscriberList.removeAll { !it.isAlive() }
            val removedCount = initialSize - subscriberList.size
            totalCleaned += removedCount

            if (subscriberList.isEmpty()) {
                keysToRemove.add(keyName)
            }
        }

        // Remove empty subscriber lists
        keysToRemove.forEach { keySpecificSubscribers.remove(it) }

        if (totalCleaned > 0) {
            logger.debug(
                "Removed $totalCleaned dead references and ${keysToRemove.size} empty lists",
            )
        }
    }

    private fun buildRemoteConfigUrl(): String {
        val baseUrl =
            when (serverZone) {
                ServerZone.EU -> EU_REMOTE_CONFIG_URL
                else -> US_REMOTE_CONFIG_URL
            }

        // Request all config keys
        val configKeysParam = Key.entries.joinToString("&") { "config_keys=${it.value}" }
        return "$baseUrl/config?api_key=$apiKey&$configKeysParam"
    }

    private fun buildRequestHeaders(apiVersion: Int = 2): Map<String, String> {
        return mapOf(
            "Authorization" to "Bearer $apiKey",
            "X-Client-Platform" to "Android",
            "X-Client-Version" to "$apiVersion",
            "X-Client-Library" to "${SDK_LIBRARY}/${SDK_VERSION}",
        )
    }

    private fun parseConfigsFromResponse(responseBody: String?): Map<String, ConfigMap>? {
        return responseBody?.let { body ->
            try {
                val responseJson = JSONObject(body)
                val result =
                    buildMap {
                        // Handle configs-wrapped response format:
                        // Format: {"configs": {"sessionReplay": {...}}}
                        val configsRoot = responseJson.optJSONObject("configs")
                        if (configsRoot == null) {
                            logger.warn("No 'configs' key found in response")
                            return null
                        }

                        // Dynamically parse all top-level config objects
                        for (topLevelKey in configsRoot.keys()) {
                            val topLevelObj = configsRoot.optJSONObject(topLevelKey)
                            if (topLevelObj != null) {
                                // Parse each nested config within this top-level object
                                for (nestedKey in topLevelObj.keys()) {
                                    val flattenedKey = "$topLevelKey.$nestedKey"
                                    val nestedConfig = topLevelObj.optJSONObject(nestedKey)

                                    if (nestedConfig != null) {
                                        put(flattenedKey, nestedConfig.toMapObj().filterNotNullValues())
                                        logger.debug("Successfully parsed config: $flattenedKey")
                                    } else {
                                        logger.debug(
                                            "Config $flattenedKey has no nested object, using empty map",
                                        )
                                        put(flattenedKey, emptyMap())
                                    }
                                }
                            } else {
                                logger.debug("Skipping non-object top-level key: $topLevelKey")
                            }
                        }
                    }

                if (result.isEmpty() || result.values.all { it.isEmpty() }) {
                    logger.warn("No valid configs found in response")
                    return null
                }

                logger.debug("Successfully parsed ${result.size} config entries")
                result
            } catch (e: Exception) {
                logger.error("Failed to parse configs from response: ${e.message}")
                null
            }
        } ?: run {
            logger.warn("Response body is null, returning null config map")
            null
        }
    }

    /**
     * Extension function to filter out null values from a Map<String, Any?>
     * and return a ConfigMap
     */
    @Suppress("UNCHECKED_CAST")
    private fun Map<String, Any?>.filterNotNullValues(): ConfigMap {
        return this.filterValues { it != null } as ConfigMap
    }
}

/**
 * Safely extracts a String value from the config map with a default fallback.
 * These functions provide type-safe access with defaults and never crash.
 */
fun ConfigMap.getString(
    key: String,
    default: String = "",
): String {
    return (this[key] as? String) ?: default
}

/**
 * Safely extracts a Boolean value from the config map with a default fallback.
 */
fun ConfigMap.getBoolean(
    key: String,
    default: Boolean = false,
): Boolean {
    return (this[key] as? Boolean) ?: default
}

/**
 * Safely extracts a Double value from the config map with a default fallback.
 */
fun ConfigMap.getDouble(
    key: String,
    default: Double = 0.0,
): Double {
    return when (val value = this[key]) {
        is Double -> value
        is Number -> value.toDouble()
        is String -> value.toDoubleOrNull() ?: default
        else -> default
    }
}

/**
 * Safely extracts an Int value from the config map with a default fallback.
 */
fun ConfigMap.getInt(
    key: String,
    default: Int = 0,
): Int {
    return when (val value = this[key]) {
        is Int -> value
        is Number -> value.toInt()
        is String -> value.toIntOrNull() ?: default
        else -> default
    }
}

/**
 * Safely extracts a List<String> value from the config map with a default fallback.
 */
fun ConfigMap.getStringList(
    key: String,
    default: List<String> = emptyList(),
): List<String> {
    return (this[key] as? List<*>)?.mapNotNull { it as? String } ?: default
}
