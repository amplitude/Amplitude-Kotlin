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
 * Represents config key-value pairs where keys are strings and values can be any type.
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

    /**
     * Dot-path key for subscribing to remote config sections.
     * Split by "." to walk nested config objects. For example,
     * "sessionReplay.sr_android_privacy_config" resolves configs.sessionReplay.sr_android_privacy_config.
     */
    sealed class Key(open val value: String) {
        data object AnalyticsSdk : Key("analyticsSDK.androidSDK")

        data object Diagnostics : Key("diagnostics.androidSDK")

        data object SessionReplayPrivacyConfig : Key("sessionReplay.sr_android_privacy_config")

        data object SessionReplaySamplingConfig : Key("sessionReplay.sr_android_sampling_config")

        class Custom(override val value: String) : Key(value) {
            override fun equals(other: Any?): Boolean = other is Custom && other.value == value

            override fun hashCode(): Int = value.hashCode()

            override fun toString(): String = "Custom(value=$value)"
        }

        companion object {
            internal val fetchKeys: List<String> by lazy {
                listOf(AnalyticsSdk, Diagnostics, SessionReplayPrivacyConfig, SessionReplaySamplingConfig)
                    .map { it.value.substringBefore(".") }
                    .distinct()
            }
        }
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
    }

    // Subscribers for specific config keys using weak references to prevent memory leaks
    private val subscriberLock = Any()
    private val keySpecificSubscribers = mutableMapOf<String, MutableList<WeakCallback>>()

    // Additional root keys registered by Custom key subscribers, unioned with fetchKeys for the URL.
    private val customFetchRoots = mutableSetOf<String>()

    @Volatile
    private var isFetching: Boolean = false

    // Set when customFetchRoots grows — bypasses rate limit on next fetch so
    // newly registered custom roots get fetched promptly, not after 5 minutes.
    @Volatile
    private var fetchRootsExpanded: Boolean = false

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

                if (key is Key.Custom) {
                    val root = key.value.substringBefore(".")
                    if (root.isNotEmpty() && root !in Key.fetchKeys && customFetchRoots.add(root)) {
                        fetchRootsExpanded = true
                    }
                }

                subscriberList.size
            }

        logger.debug("Added subscriber for key: ${key.value}. Total subscribers: $subscriberCount")
    }

    /**
     * Trigger a remote fetch for all config keys
     */
    override fun updateConfigs() {
        coroutineScope.launch(networkIODispatcher) {
            if (isFetching) {
                logger.debug("RemoteConfig update skipped: fetch already in progress")
                return@launch
            }

            val bypassRateLimit = fetchRootsExpanded
            if (bypassRateLimit) fetchRootsExpanded = false

            if (!bypassRateLimit && shouldRateLimit()) {
                logger.debug("RemoteConfig update skipped: within 5-minute window")
                return@launch
            }

            isFetching = true
            try {
                val blob = fetchRemoteConfig() ?: return@launch

                val timestamp = System.currentTimeMillis()
                withContext(storageIODispatcher) {
                    storage.write(REMOTE_CONFIG, blob.toJSONObject().toString())
                    storage.write(REMOTE_CONFIG_TIMESTAMP, timestamp.toString())
                    logger.debug("Successfully stored remote configs to storage")
                }

                val subscriberSnapshot =
                    synchronized(subscriberLock) {
                        keySpecificSubscribers.map { (dotPath, subs) -> dotPath to subs.toList() }
                    }
                for ((dotPath, subscribers) in subscriberSnapshot) {
                    val resolved = resolveDotPath(blob, dotPath)
                    val config = resolved ?: emptyMap()
                    subscribers.forEach { weakCallback ->
                        weakCallback.runSafely {
                            onUpdate(config, REMOTE, timestamp)
                        }
                    }
                }
            } catch (e: Exception) {
                logger.error("Error updating remote configs: ${e.message}")
            } finally {
                isFetching = false
                if (fetchRootsExpanded) {
                    updateConfigs()
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
     * Retrieve stored configuration data for a specific dot-path key.
     * Walks the stored nested blob using dot-path segments.
     */
    private fun getStoredConfigData(dotPath: String): ConfigData? {
        return try {
            val blob = getStoredConfigBlob() ?: return null

            val resolved = resolveDotPath(blob, dotPath) ?: emptyMap()
            val timestampStr = storage.read(REMOTE_CONFIG_TIMESTAMP)
            val timestamp = timestampStr?.toLongOrNull() ?: 0L

            logger.debug("Retrieved stored config for $dotPath with ${resolved.size} properties")
            ConfigData(resolved, timestamp)
        } catch (e: Exception) {
            logger.error("Failed to retrieve stored config data for $dotPath: ${e.message}")
            null
        }
    }

    /**
     * Reads the stored config blob from storage. Returns the nested map
     * structure or null if storage is empty/corrupt. Gracefully handles
     * old pre-flattened storage format by treating it as a cache miss.
     */
    private fun getStoredConfigBlob(): ConfigMap? {
        return try {
            val configJson = storage.read(REMOTE_CONFIG)
            if (configJson.isNullOrBlank()) {
                logger.debug("No stored config found in storage")
                return null
            }

            val allConfigs = JSONObject(configJson).toMapObj().filterNotNullValues()
            if (allConfigs.isEmpty()) return null

            // Detect old pre-flattened format: keys contain dots (e.g. "sessionReplay.sr_android_privacy_config").
            // New format has top-level keys without dots (e.g. "sessionReplay", "diagnostics").
            val isOldFormat = allConfigs.keys.any { "." in it }
            if (isOldFormat) {
                logger.debug("Detected old pre-flattened cache format; migrating in place")
                val migrated = migrateOldFormatToNested(allConfigs)
                coroutineScope.launch(storageIODispatcher) {
                    try {
                        storage.write(REMOTE_CONFIG, migrated.toJSONObject().toString())
                        logger.debug("Migrated old-format cache to nested format")
                    } catch (e: Exception) {
                        logger.error("Failed to write migrated cache: ${e.message}")
                    }
                }
                return migrated
            }

            logger.debug("Successfully loaded stored config blob with ${allConfigs.size} top-level keys")
            allConfigs
        } catch (e: Exception) {
            logger.error("Failed to parse all stored configs: ${e.message}")
            null
        }
    }

    // Temporary: old cache only stored single-dot keys (e.g. "sessionReplay.sr_android_privacy_config").
    @Suppress("UNCHECKED_CAST")
    private fun migrateOldFormatToNested(flat: ConfigMap): ConfigMap {
        val result = mutableMapOf<String, Any>()
        for ((key, value) in flat) {
            val dotIndex = key.indexOf('.')
            if (dotIndex < 0) {
                result[key] = value
                continue
            }
            val root = key.substring(0, dotIndex)
            val child = key.substring(dotIndex + 1)
            val existing = result[root] as? MutableMap<String, Any> ?: mutableMapOf()
            existing[child] = value
            result[root] = existing
        }
        return result
    }

    /**
     * Walk a dot-path into a nested config blob and return the leaf as a [ConfigMap].
     * Returns null if any segment is missing or non-map.
     */
    @Suppress("UNCHECKED_CAST")
    private fun resolveDotPath(
        blob: ConfigMap,
        dotPath: String,
    ): ConfigMap? {
        val segments = dotPath.split(".")
        var current: Any? = blob
        for (segment in segments) {
            val map = current as? Map<String, Any> ?: return null
            current = map[segment] ?: return null
        }
        return current as? Map<String, Any>
    }

    private fun fetchRemoteConfig(): ConfigMap? {
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
        val iterator = keySpecificSubscribers.iterator()

        while (iterator.hasNext()) {
            val (_, subscriberList) = iterator.next()
            val initialSize = subscriberList.size
            subscriberList.removeAll { !it.isAlive() }
            totalCleaned += initialSize - subscriberList.size

            if (subscriberList.isEmpty()) {
                iterator.remove()
            }
        }

        if (totalCleaned > 0) {
            logger.debug("Cleaned up $totalCleaned dead references")
            recomputeCustomFetchRootsLocked()
        }
    }

    private fun recomputeCustomFetchRootsLocked() {
        val liveRoots = mutableSetOf<String>()
        for (dotPath in keySpecificSubscribers.keys) {
            val root = dotPath.substringBefore(".")
            if (root.isNotEmpty() && root !in Key.fetchKeys) {
                liveRoots.add(root)
            }
        }
        customFetchRoots.retainAll(liveRoots)
    }

    private fun buildRemoteConfigUrl(): String {
        val baseUrl =
            when (serverZone) {
                ServerZone.EU -> EU_REMOTE_CONFIG_URL
                else -> US_REMOTE_CONFIG_URL
            }

        val customRoots = synchronized(subscriberLock) { customFetchRoots.toSet() }
        val allKeys = Key.fetchKeys.toSet() + customRoots
        val configKeysParam =
            allKeys.joinToString("&") { "config_keys=$it" }
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

    /**
     * Parses the API response and returns the full nested config blob (the
     * contents of the "configs" key). The blob is stored as-is — no flattening.
     */
    private fun parseConfigsFromResponse(responseBody: String?): ConfigMap? {
        return responseBody?.let { body ->
            try {
                val responseJson = JSONObject(body)

                val configsRoot = responseJson.optJSONObject("configs")
                if (configsRoot == null) {
                    logger.warn("No 'configs' key found in response")
                    return null
                }

                if (configsRoot.length() == 0) {
                    logger.debug("Server returned empty configs — project may have no blades enabled")
                    return emptyMap()
                }

                val blob = configsRoot.toMapObj().filterNotNullValues()
                if (blob.isEmpty()) {
                    logger.warn("No valid configs found in response")
                    return null
                }

                logger.debug("Successfully parsed config blob with ${blob.size} top-level keys")
                blob
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
