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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.lang.ref.WeakReference
import java.util.concurrent.atomic.AtomicBoolean

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

    /**
     * Controls when the first config is delivered to a subscriber.
     *
     * Subsequent updates after the first delivery always flow through normally.
     * Only the *initial* delivery semantics differ between modes.
     */
    sealed class DeliveryMode {
        /** Deliver cached config immediately if available, then remote when it arrives. */
        data object All : DeliveryMode()

        /** Wait for remote up to [timeoutMs], then fall back to cache (or empty). */
        class WaitForRemote(val timeoutMs: Long) : DeliveryMode()
    }

    enum class Key(val value: String) {
        ANALYTICS_SDK("analyticsSDK.androidSDK"),
        DIAGNOSTICS("diagnostics.androidSDK"),
        SESSION_REPLAY_PRIVACY_CONFIG("sessionReplay.sr_android_privacy_config"),
        SESSION_REPLAY_SAMPLING_CONFIG("sessionReplay.sr_android_sampling_config"),
    }

    /**
     * Subscribe to remote configuration updates for a specific configuration key.
     * The callback will be invoked whenever the specific configuration is updated.
     */
    fun subscribe(
        key: Key,
        deliveryMode: DeliveryMode = DeliveryMode.All,
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
     * Wrapper class for weak reference callbacks to prevent memory leaks.
     *
     * For [RemoteConfigClient.DeliveryMode.WaitForRemote] subscribers, the first
     * delivery is gated by [firstDeliveryClaimed]: whichever path (remote arrival
     * via [updateConfigs] or timeout fallback) claims the gate first delivers the
     * initial callback; the other becomes a no-op for that first delivery.
     * Subsequent deliveries always pass through.
     */
    private inner class WeakCallback(
        callback: RemoteConfigClient.RemoteConfigCallback,
        private val firstDeliveryClaimed: AtomicBoolean? = null,
        private val firstDeliverySignal: CompletableDeferred<Unit>? = null,
    ) {
        private val weakRef = WeakReference(callback)

        /**
         * Serializes all [RemoteConfigClient.RemoteConfigCallback.onUpdate]
         * invocations for this subscriber. Without this lock, the timeout
         * fallback and the remote fetch can invoke the callback concurrently
         * from different dispatchers, causing unsynchronized state in the
         * subscriber.
         */
        private val deliveryLock = Any()

        fun isAlive(): Boolean = weakRef.get() != null

        /**
         * Invoke [action] on the wrapped callback if it is still alive and, when
         * gating is in effect, only if this invocation successfully claims the
         * first-delivery slot. Subsequent invocations after the first delivery
         * always pass through.
         */
        fun runSafely(action: RemoteConfigClient.RemoteConfigCallback.() -> Unit) {
            val callback = weakRef.get() ?: return

            if (firstDeliveryClaimed != null) {
                // Gated: claim the first-delivery slot if available.
                val claimedFirst = firstDeliveryClaimed.compareAndSet(false, true)
                if (claimedFirst) {
                    // Signal before callback so a concurrent arrival sees it complete
                    // and delivers as a subsequent update instead of being dropped.
                    firstDeliverySignal?.complete(Unit)
                } else {
                    // Another path claimed the gate. Wait for it to complete its
                    // delivery before proceeding with this subsequent update, ensuring
                    // correct ordering (gate-winner delivers first, then this).
                    kotlinx.coroutines.runBlocking {
                        firstDeliverySignal?.await()
                    }
                }
            }

            synchronized(deliveryLock) {
                try {
                    callback.action()
                } catch (e: Exception) {
                    logger.error("Exception in subscriber callback: ${e.message}")
                }
            }
        }

        /** Deliver only if this callback can still claim the first-delivery slot. */
        fun runSafelyIfFirst(action: RemoteConfigClient.RemoteConfigCallback.() -> Unit): Boolean {
            val callback = weakRef.get() ?: return false
            val claimed = firstDeliveryClaimed?.compareAndSet(false, true) ?: return false
            if (!claimed) return false

            firstDeliverySignal?.complete(Unit)
            synchronized(deliveryLock) {
                try {
                    callback.action()
                } catch (e: Exception) {
                    logger.error("Exception in subscriber callback: ${e.message}")
                }
            }
            return true
        }

        /** True if gated (WaitForRemote) and first delivery hasn't fired yet. */
        fun isAwaitingFirstDelivery(): Boolean {
            val claimed = firstDeliveryClaimed ?: return false
            return !claimed.get()
        }
    }

    // Subscribers for specific config keys using weak references to prevent memory leaks
    private val subscriberLock = Any()
    private val keySpecificSubscribers = mutableMapOf<String, MutableList<WeakCallback>>()

    // Simple in-flight fetch guard; safe because networkIODispatcher is single-threaded
    private var isFetching: Boolean = false

    override fun subscribe(
        key: Key,
        deliveryMode: RemoteConfigClient.DeliveryMode,
        callback: RemoteConfigClient.RemoteConfigCallback,
    ) {
        when (deliveryMode) {
            is RemoteConfigClient.DeliveryMode.All -> subscribeAll(key, callback)
            is RemoteConfigClient.DeliveryMode.WaitForRemote ->
                subscribeWaitForRemote(key, deliveryMode.timeoutMs, callback)
        }
    }

    private fun subscribeAll(
        key: Key,
        callback: RemoteConfigClient.RemoteConfigCallback,
    ) {
        val weakCallback = WeakCallback(callback)
        registerSubscriber(key, weakCallback)

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

    private fun subscribeWaitForRemote(
        key: Key,
        timeoutMs: Long,
        callback: RemoteConfigClient.RemoteConfigCallback,
    ) {
        // Gate first delivery — timeout and remote race; first to claim wins.
        val firstDeliveryClaimed = AtomicBoolean(false)
        val firstDeliverySignal = CompletableDeferred<Unit>()
        val weakCallback =
            WeakCallback(
                callback = callback,
                firstDeliveryClaimed = firstDeliveryClaimed,
                firstDeliverySignal = firstDeliverySignal,
            )
        registerSubscriber(key, weakCallback)

        updateConfigs()

        // Timeout runs on coroutineScope's dispatcher (not networkIODispatcher)
        // so it can fire even while the single-threaded network executor is busy.
        coroutineScope.launch {
            val signaled =
                withTimeoutOrNull(timeoutMs) {
                    firstDeliverySignal.await()
                    true
                }
            if (signaled == null) {
                // Timeout: fall back to cache (or empty). runSafelyIfFirst
                // suppresses this if a remote delivery already claimed the gate.
                val storedData = getStoredConfigData(key.value)
                val config = storedData?.config ?: emptyMap()
                val timestamp = storedData?.timestamp ?: System.currentTimeMillis()
                val delivered =
                    weakCallback.runSafelyIfFirst {
                        onUpdate(config, CACHE, timestamp)
                    }
                if (delivered) {
                    logger.debug(
                        "WaitForRemote timed out after ${timeoutMs}ms for ${key.value}; " +
                            "delivered ${if (storedData != null) "cache" else "empty"} fallback.",
                    )
                } else {
                    logger.debug(
                        "WaitForRemote timeout fired after ${timeoutMs}ms for ${key.value}, " +
                            "but a fresh remote delivery already won; fallback suppressed.",
                    )
                }
            }
        }
    }

    private fun registerSubscriber(
        key: Key,
        weakCallback: WeakCallback,
    ) {
        val subscriberCount =
            synchronized(subscriberLock) {
                cleanupDeadReferencesLocked()
                val subscriberList =
                    keySpecificSubscribers.getOrPut(key.value) {
                        mutableListOf()
                    }
                subscriberList.add(weakCallback)
                subscriberList.size
            }

        logger.debug("Added subscriber for key: ${key.value}. Total subscribers: $subscriberCount")
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

                // Snapshot old cache before overwriting — absent-key fallback
                // needs the pre-fetch values, not the partial new response.
                val preFetchCache = getAllStoredConfigs()

                val timestamp = System.currentTimeMillis()
                withContext(storageIODispatcher) {
                    storage.write(REMOTE_CONFIG, configs.toJSONObject().toString())
                    storage.write(REMOTE_CONFIG_TIMESTAMP, timestamp.toString())
                    logger.debug("Successfully stored remote configs to storage")
                }

                configs.forEach { (configKey, config) ->
                    val subscriberList =
                        synchronized(subscriberLock) {
                            keySpecificSubscribers[configKey]?.toList().orEmpty()
                        }
                    subscriberList.forEach { weakCallback ->
                        weakCallback.runSafely {
                            onUpdate(config, REMOTE, timestamp)
                        }
                    }
                }

                // Unblock WaitForRemote subscribers whose key is absent from
                // a successful fetch — no point waiting the full timeout.
                notifyAbsentKeySubscribers(presentKeys = configs.keys, preFetchCache = preFetchCache)
            } catch (e: Exception) {
                logger.error("Error updating remote configs: ${e.message}")
            } finally {
                isFetching = false
            }
        }
    }

    /**
     * For each registered subscriber whose key is *not* present in a fresh
     * successful fetch, deliver the fallback (cache or empty) via the
     * first-only delivery path so [RemoteConfigClient.DeliveryMode.WaitForRemote]
     * gates resolve immediately instead of waiting for the timeout.
     *
     * Uses [WeakCallback.runSafelyIfFirst]: if a subscriber has already
     * received its first delivery (e.g. timeout already fired), the call is a
     * no-op. Subscribers using [RemoteConfigClient.DeliveryMode.All] also
     * become no-ops because they aren't gated and cannot claim the gate.
     */
    private fun notifyAbsentKeySubscribers(
        presentKeys: Set<String>,
        preFetchCache: Map<String, ConfigMap>,
    ) {
        val absentSubscribers =
            synchronized(subscriberLock) {
                keySpecificSubscribers
                    .filterKeys { it !in presentKeys }
                    .mapValues { (_, subs) -> subs.toList() }
            }
        if (absentSubscribers.isEmpty()) return

        absentSubscribers.forEach { (configKey, subscribers) ->
            val cachedConfig = preFetchCache[configKey]
            subscribers.forEach subscriber@{ weakCallback ->
                if (!weakCallback.isAwaitingFirstDelivery()) return@subscriber
                val config = cachedConfig ?: emptyMap()
                val timestamp = System.currentTimeMillis()
                val delivered =
                    weakCallback.runSafelyIfFirst {
                        onUpdate(config, CACHE, timestamp)
                    }
                if (delivered) {
                    logger.debug(
                        "WaitForRemote unblocked for $configKey: key absent from fetch; " +
                            "delivered ${if (cachedConfig != null) "cache" else "empty"} fallback.",
                    )
                }
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
        synchronized(subscriberLock) {
            cleanupDeadReferencesLocked()
        }
    }

    private fun cleanupDeadReferencesLocked() {
        var totalCleaned = 0
        var emptyListCount = 0
        val iterator = keySpecificSubscribers.iterator()

        while (iterator.hasNext()) {
            val (_, subscriberList) = iterator.next()
            val initialSize = subscriberList.size
            subscriberList.removeAll { !it.isAlive() }
            val removedCount = initialSize - subscriberList.size
            totalCleaned += removedCount

            if (subscriberList.isEmpty()) {
                iterator.remove()
                emptyListCount++
            }
        }

        if (totalCleaned > 0) {
            logger.debug(
                "Removed $totalCleaned dead references and $emptyListCount empty lists",
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

                // Handle configs-wrapped response format:
                // Format: {"configs": {"sessionReplay": {...}}}
                val configsRoot = responseJson.optJSONObject("configs")
                if (configsRoot == null) {
                    logger.warn("No 'configs' key found in response")
                    return null
                }

                val result =
                    buildMap {
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

                if (result.isEmpty() && configsRoot.length() == 0) {
                    // The "configs" root is present but empty — project has no
                    // blades enabled. This is a valid server answer; return an
                    // empty map so that downstream logic (notifyAbsentKeySubscribers)
                    // can unblock WaitForRemote subscribers immediately.
                    logger.debug("Server returned empty configs — project may have no blades enabled")
                    return emptyMap()
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
