@file:OptIn(RestrictedAmplitudeFeature::class)

package com.amplitude.core.remoteconfig

import com.amplitude.common.Logger
import com.amplitude.core.Constants.SDK_LIBRARY
import com.amplitude.core.Constants.SDK_VERSION
import com.amplitude.core.RestrictedAmplitudeFeature
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
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.lang.ref.WeakReference
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Type alias for remote configuration map data.
 * Represents config key-value pairs where keys are strings and values can be any type.
 */
typealias ConfigMap = Map<String, Any>

/**
 * Interface for remote configuration client.
 */
@RestrictedAmplitudeFeature
interface RemoteConfigClient {
    @RestrictedAmplitudeFeature
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
    @RestrictedAmplitudeFeature
    sealed class DeliveryMode {
        /** Deliver cached config immediately if available, then remote when it arrives. */
        data object All : DeliveryMode()

        /** Wait for remote up to [timeoutMs], then fall back to cache (or empty). */
        class WaitForRemote(val timeoutMs: Long) : DeliveryMode() {
            override fun equals(other: Any?): Boolean = other is WaitForRemote && other.timeoutMs == timeoutMs

            override fun hashCode(): Int = timeoutMs.hashCode()

            override fun toString(): String = "WaitForRemote(timeoutMs=$timeoutMs)"
        }
    }

    /**
     * Dot-path key for subscribing to a section of the remote config blob.
     * Segments are split by "." and used to walk nested maps. For example,
     * "sessionReplay.sr_android_privacy_config" resolves the nested map at
     * configs.sessionReplay.sr_android_privacy_config.
     */
    @RestrictedAmplitudeFeature
    sealed class Key(val value: String) {
        data object AnalyticsSdk : Key("analyticsSDK.androidSDK")

        data object Diagnostics : Key("diagnostics.androidSDK")

        data object SessionReplayPrivacyConfig : Key("sessionReplay.sr_android_privacy_config")

        data object SessionReplaySamplingConfig : Key("sessionReplay.sr_android_sampling_config")

        class Custom(value: String) : Key(value) {
            override fun equals(other: Any?): Boolean = other is Custom && other.value == value

            override fun hashCode(): Int = value.hashCode()

            override fun toString(): String = "Custom(value=$value)"
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

    @RestrictedAmplitudeFeature
    fun interface RemoteConfigCallback {
        /**
         * @param config resolved config slice; null when the key is absent from a
         *   successful fetch, or when the fetch failed.
         * @param source whether [config] came from cache or remote.
         * @param timestamp time of the successful fetch that produced [config];
         *   null signals a failed fetch. This is the discriminator between an
         *   absent-but-valid key (non-null timestamp, null config) and an error
         *   (null timestamp, null config).
         */
        fun onUpdate(
            config: ConfigMap?,
            source: Source,
            timestamp: Long?,
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
        val config: ConfigMap?,
        val timestamp: Long,
    )

    companion object {
        private const val US_REMOTE_CONFIG_URL = "https://sr-client-cfg.amplitude.com"
        private const val EU_REMOTE_CONFIG_URL = "https://sr-client-cfg.eu.amplitude.com"
        private const val CONFIG_GROUP = "android"
        private const val MIN_FETCH_INTERVAL_MS: Long = 5 * 60 * 1_000L
    }

    /**
     * Weak-reference wrapper around a subscriber callback to avoid leaks.
     *
     * Deliveries for a subscriber are serialized by [deliveryLock]. Remote deliveries
     * are de-duplicated by [lastFetchId] so the same fetch is never delivered twice —
     * e.g. a subscribe's own self-delivery and a concurrent refresh broadcast carry the
     * same id, so only the first one fires. For [RemoteConfigClient.DeliveryMode.WaitForRemote],
     * [deliverFirst] enforces a single initial delivery (one-and-done).
     */
    private inner class WeakCallback(
        callback: RemoteConfigClient.RemoteConfigCallback,
        val deliveryMode: RemoteConfigClient.DeliveryMode,
    ) {
        private val weakRef = WeakReference(callback)
        private val deliveryLock = Any()
        private val delivered = AtomicBoolean(false)
        private val lastFetchId = AtomicLong(Long.MIN_VALUE)

        fun isAlive(): Boolean = weakRef.get() != null

        fun hasDelivered(): Boolean = delivered.get()

        /**
         * Immediate cache delivery (used by [RemoteConfigClient.DeliveryMode.All]).
         * Skipped if a remote has already been delivered — a concurrent refresh can
         * race the cache read, and a stale cache must never land after a fresh remote.
         */
        fun deliverCache(
            config: ConfigMap?,
            timestamp: Long,
        ) {
            val callback = weakRef.get() ?: return
            synchronized(deliveryLock) {
                if (lastFetchId.get() != Long.MIN_VALUE) return
                delivered.set(true)
                invoke(callback, config, CACHE, timestamp)
            }
        }

        /**
         * Remote delivery, de-duplicated by [fetchId]: a fetch already delivered to
         * this subscriber (same or older id) is skipped. Used for the initial remote
         * and ongoing [RemoteConfigClient.DeliveryMode.All] refreshes.
         */
        fun deliverRemote(
            config: ConfigMap?,
            timestamp: Long,
            fetchId: Long,
        ) {
            val callback = weakRef.get() ?: return
            synchronized(deliveryLock) {
                if (fetchId <= lastFetchId.get()) return
                lastFetchId.set(fetchId)
                delivered.set(true)
                invoke(callback, config, REMOTE, timestamp)
            }
        }

        /**
         * Single initial delivery for [RemoteConfigClient.DeliveryMode.WaitForRemote]:
         * the first caller to claim the slot delivers; all others (timeout fallback,
         * a late remote, a refresh) are no-ops. Returns whether it fired.
         */
        fun deliverFirst(
            config: ConfigMap?,
            source: RemoteConfigClient.Source,
            timestamp: Long?,
        ): Boolean {
            val callback = weakRef.get() ?: return false
            synchronized(deliveryLock) {
                if (!delivered.compareAndSet(false, true)) return false
                invoke(callback, config, source, timestamp)
                return true
            }
        }

        private fun invoke(
            callback: RemoteConfigClient.RemoteConfigCallback,
            config: ConfigMap?,
            source: RemoteConfigClient.Source,
            timestamp: Long?,
        ) {
            try {
                callback.onUpdate(config, source, timestamp)
            } catch (e: Exception) {
                logger.error("Exception in subscriber callback: ${e.message}")
            }
        }
    }

    /** Result of a remote fetch: a successful blob (with fetch time and id), or a failure. */
    private sealed interface FetchOutcome {
        data class Success(val blob: ConfigMap, val timestamp: Long, val fetchId: Long) : FetchOutcome

        data object Failure : FetchOutcome
    }

    // Subscribers for specific config keys using weak references to prevent memory leaks
    private val subscriberLock = Any()
    private val keySpecificSubscribers = mutableMapOf<String, MutableList<WeakCallback>>()

    // Shared in-flight remote fetch so concurrent subscribers share a single network call.
    private val fetchLock = Any()
    private var inFlightFetch: Deferred<FetchOutcome>? = null
    private var lastSuccess: FetchOutcome.Success? = null

    // Monotonic id per fetch; subscribers use it to de-dup remote deliveries.
    private val fetchSeq = AtomicLong(0L)

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

    /**
     * Deliver cached config immediately if present, then the remote result when it
     * arrives. A failure (null config, null timestamp) is delivered only when nothing
     * else was — no cache and the fetch failed — guaranteeing exactly one initial
     * callback. Mirrors iOS `.all`.
     */
    private fun subscribeAll(
        key: Key,
        callback: RemoteConfigClient.RemoteConfigCallback,
    ) {
        val weakCallback = WeakCallback(callback, RemoteConfigClient.DeliveryMode.All)
        registerSubscriber(key, weakCallback)

        coroutineScope.launch {
            // A cached blob counts even when the key is absent within it: iOS delivers
            // (null, CACHE, lastFetch) in that case, distinct from a true cache miss.
            val cached = withContext(storageIODispatcher) { getStoredConfigData(key.value) }
            if (cached != null) {
                weakCallback.deliverCache(cached.config, cached.timestamp)
            }

            // Deliver to this subscriber from its own awaited outcome — never relying on
            // a broadcast snapshot. deliverRemote de-dups by fetchId, so a concurrent
            // refresh broadcast of the same fetch won't double-deliver.
            when (val handle = obtainSubscribeFetch()) {
                is FetchHandle.Reuse ->
                    weakCallback.deliverRemote(
                        resolveDotPath(handle.success.blob, key.value),
                        handle.success.timestamp,
                        handle.success.fetchId,
                    )
                is FetchHandle.Pending ->
                    when (val outcome = handle.deferred.await()) {
                        is FetchOutcome.Success ->
                            weakCallback.deliverRemote(
                                resolveDotPath(outcome.blob, key.value),
                                outcome.timestamp,
                                outcome.fetchId,
                            )
                        // Failure delivers only if nothing (cache) was delivered first.
                        FetchOutcome.Failure -> weakCallback.deliverFirst(null, REMOTE, null)
                    }
            }
        }
    }

    /**
     * Wait for the remote fetch up to [timeoutMs]. On success deliver remote; on
     * failure or timeout fall back to cache, or a failure signal (null config, null
     * timestamp) when no cache exists. Exactly one callback is delivered. Mirrors
     * iOS `.waitForRemote`.
     */
    private fun subscribeWaitForRemote(
        key: Key,
        timeoutMs: Long,
        callback: RemoteConfigClient.RemoteConfigCallback,
    ) {
        val weakCallback =
            WeakCallback(callback, RemoteConfigClient.DeliveryMode.WaitForRemote(timeoutMs))
        registerSubscriber(key, weakCallback)

        coroutineScope.launch {
            when (val handle = obtainSubscribeFetch()) {
                is FetchHandle.Reuse ->
                    weakCallback.deliverFirst(
                        resolveDotPath(handle.success.blob, key.value),
                        REMOTE,
                        handle.success.timestamp,
                    )
                is FetchHandle.Pending -> {
                    // Wait up to the timeout for the remote; on success deliver it
                    // (REMOTE), not a cache fallback.
                    val outcome = withTimeoutOrNull(timeoutMs) { handle.deferred.await() }
                    if (outcome is FetchOutcome.Success) {
                        weakCallback.deliverFirst(
                            resolveDotPath(outcome.blob, key.value),
                            REMOTE,
                            outcome.timestamp,
                        )
                    }
                }
            }
            // Timed out or the fetch failed: fall back to cache, or a failure signal
            // when there is none. The hasDelivered() check only skips the storage read
            // in the common case — deliverFirst's CAS is the actual delivery gate, so a
            // concurrent broadcast that already delivered makes these calls no-ops.
            if (!weakCallback.hasDelivered()) {
                val cached = withContext(storageIODispatcher) { getStoredConfigData(key.value) }
                if (cached != null) {
                    // A cached blob is a valid answer even when the key is absent within
                    // it: deliver (null, CACHE, lastFetch), mirroring iOS. Only a true
                    // cache miss yields the (null, REMOTE, null) failure signal.
                    weakCallback.deliverFirst(cached.config, CACHE, cached.timestamp)
                } else {
                    weakCallback.deliverFirst(null, REMOTE, null)
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
     * Trigger a remote refresh. Rate-limited; on success, delivers the new config to
     * existing subscribers ([RemoteConfigClient.DeliveryMode.All] always,
     * [RemoteConfigClient.DeliveryMode.WaitForRemote] only if it hasn't received its
     * first callback). Failures are never delivered from a refresh — subscribers keep
     * their last valid config. Mirrors iOS `updateConfigs`.
     */
    override fun updateConfigs() {
        coroutineScope.launch(networkIODispatcher) {
            if (shouldRateLimit()) {
                logger.debug("RemoteConfig update skipped: within 5-minute window")
                return@launch
            }
            val outcome = startOrJoinFetch().await()
            if (outcome is FetchOutcome.Success) {
                broadcastUpdate(outcome.blob, outcome.timestamp, outcome.fetchId)
            }
        }
    }

    /**
     * Delivers a freshly fetched config to all current subscribers as an update.
     * [DeliveryMode.All] receives every fetch (de-duplicated by [fetchId]);
     * [DeliveryMode.WaitForRemote] only if it hasn't yet received its first callback.
     */
    private fun broadcastUpdate(
        blob: ConfigMap,
        timestamp: Long,
        fetchId: Long,
    ) {
        val subscriberSnapshot =
            synchronized(subscriberLock) {
                keySpecificSubscribers.map { (dotPath, subs) -> dotPath to subs.toList() }
            }
        for ((dotPath, subscribers) in subscriberSnapshot) {
            val config = resolveDotPath(blob, dotPath)
            subscribers.forEach { weakCallback ->
                when (weakCallback.deliveryMode) {
                    is RemoteConfigClient.DeliveryMode.All ->
                        weakCallback.deliverRemote(config, timestamp, fetchId)
                    is RemoteConfigClient.DeliveryMode.WaitForRemote ->
                        weakCallback.deliverFirst(config, REMOTE, timestamp)
                }
            }
        }
    }

    /** How a subscribe obtains its config: a reusable recent success, or a live fetch. */
    private sealed interface FetchHandle {
        data class Reuse(val success: FetchOutcome.Success) : FetchHandle

        data class Pending(val deferred: Deferred<FetchOutcome>) : FetchHandle
    }

    /**
     * Obtains config for an initial subscribe: joins an in-flight fetch, reuses a
     * recent successful result to avoid hammering the server, or starts a new fetch.
     * Never rate-limited — a subscribe must always resolve to a delivery.
     */
    private fun obtainSubscribeFetch(): FetchHandle {
        synchronized(fetchLock) {
            inFlightFetch?.let { if (it.isActive) return FetchHandle.Pending(it) }
            lastSuccess?.let { if (isFresh(it.timestamp)) return FetchHandle.Reuse(it) }
            return FetchHandle.Pending(startFetchLocked())
        }
    }

    /** Joins an in-flight fetch or starts a new one. Used by the refresh path. */
    private fun startOrJoinFetch(): Deferred<FetchOutcome> {
        synchronized(fetchLock) {
            inFlightFetch?.let { if (it.isActive) return it }
            return startFetchLocked()
        }
    }

    private fun startFetchLocked(): Deferred<FetchOutcome> {
        val fetchId = fetchSeq.incrementAndGet()
        // The fetch only produces the outcome (and records lastSuccess); it does NOT
        // invoke callbacks. Delivery is driven by awaiters (subscribe) and the refresh
        // broadcast, so the deferred completes as soon as the network/parse is done —
        // a slow subscriber callback can't delay other awaiters.
        val deferred =
            coroutineScope.async(networkIODispatcher) {
                val outcome = performFetch(fetchId)
                if (outcome is FetchOutcome.Success) {
                    synchronized(fetchLock) { lastSuccess = outcome }
                }
                outcome
            }
        inFlightFetch = deferred
        return deferred
    }

    private suspend fun performFetch(fetchId: Long): FetchOutcome {
        val blob =
            try {
                fetchRemoteConfig() ?: return FetchOutcome.Failure
            } catch (e: Exception) {
                logger.error("Error fetching remote configs: ${e.message}")
                return FetchOutcome.Failure
            }
        val timestamp = System.currentTimeMillis()
        // Persisting the blob is best-effort: a storage write failure must not discard
        // a valid in-memory remote config (mirrors iOS `try? storage.setConfig`).
        try {
            withContext(storageIODispatcher) {
                storage.write(REMOTE_CONFIG, blob.toJSONObject().toString())
                storage.write(REMOTE_CONFIG_TIMESTAMP, timestamp.toString())
                logger.debug("Successfully stored remote configs to storage")
            }
        } catch (e: Exception) {
            logger.error("Failed to persist remote configs (delivering anyway): ${e.message}")
        }
        return FetchOutcome.Success(blob, timestamp, fetchId)
    }

    private fun isFresh(timestamp: Long): Boolean {
        return timestamp > 0L && (System.currentTimeMillis() - timestamp) < MIN_FETCH_INTERVAL_MS
    }

    private suspend fun shouldRateLimit(): Boolean {
        return try {
            val lastTsStr = withContext(storageIODispatcher) { storage.read(REMOTE_CONFIG_TIMESTAMP) }
            val lastTs = lastTsStr?.toLongOrNull() ?: 0L
            if (lastTs <= 0L) return false
            (System.currentTimeMillis() - lastTs) < MIN_FETCH_INTERVAL_MS
        } catch (e: Exception) {
            // Don't let a storage read failure leak out of the refresh coroutine or
            // wedge the rate limiter — fall through and let the fetch proceed.
            logger.error("Failed to read rate-limit timestamp: ${e.message}")
            false
        }
    }

    /**
     * Retrieve stored configuration data for a specific dot-path key.
     * Walks the stored nested blob using dot-path segments.
     *
     * Reads storage synchronously, so callers MUST invoke it from
     * [storageIODispatcher] (e.g. inside `withContext(storageIODispatcher)`).
     */
    private fun getStoredConfigData(dotPath: String): ConfigData? {
        return try {
            val blob = getStoredConfigBlob() ?: return null

            val resolved = resolveDotPath(blob, dotPath)
            val timestampStr = storage.read(REMOTE_CONFIG_TIMESTAMP)
            val timestamp = timestampStr?.toLongOrNull() ?: 0L

            logger.debug("Retrieved stored config for $dotPath with ${resolved?.size ?: 0} properties")
            ConfigData(resolved, timestamp)
        } catch (e: Exception) {
            logger.error("Failed to retrieve stored config data for $dotPath: ${e.message}")
            null
        }
    }

    /**
     * Reads the stored config blob from storage. Returns the nested map
     * structure or null if storage is empty/corrupt. Stale old pre-flattened
     * format blobs are ignored (treated as a cache miss) so the in-flight
     * remote fetch refills storage with the correct nested format. The stale
     * blob is left untouched here — clearing it from the read path could race
     * with the fetch's write and erase a freshly stored blob; a successful
     * fetch overwrites it, and until then it is simply never delivered.
     */
    private fun getStoredConfigBlob(): ConfigMap? {
        return try {
            val configJson = storage.read(REMOTE_CONFIG)
            if (configJson.isNullOrBlank()) {
                logger.debug("No stored config found in storage")
                return null
            }

            val allConfigs = JSONObject(configJson).toMapObj().filterNotNullValues()

            // An empty (but parseable) blob is a valid cached answer — a successful fetch
            // that returned no config. It must remain distinguishable from a true cache
            // miss (null), so subscribers get (null, CACHE, ts) rather than a failure.

            // Detect old pre-flattened format: keys contain dots (e.g. "sessionReplay.sr_android_privacy_config").
            // New format has top-level keys without dots (e.g. "sessionReplay", "diagnostics").
            val isOldFormat = allConfigs.keys.any { "." in it }
            if (isOldFormat) {
                logger.debug("Detected old pre-flattened cache format; ignoring stale blob")
                return null
            }

            logger.debug("Successfully loaded stored config blob with ${allConfigs.size} top-level keys")
            allConfigs
        } catch (e: Exception) {
            logger.error("Failed to parse all stored configs: ${e.message}")
            null
        }
    }

    /**
     * Walk a dot-path into a nested config blob and return the leaf as a [ConfigMap].
     * Returns null if any segment is missing or non-map. Null values inside the
     * resolved leaf (from `JSONObject.NULL` in nested objects) are filtered out so
     * the delivered map honors the non-null [ConfigMap] contract.
     */
    @Suppress("UNCHECKED_CAST")
    private fun resolveDotPath(
        blob: ConfigMap,
        dotPath: String,
    ): ConfigMap? {
        val segments = dotPath.split(".")
        var current: Any? = blob
        for (segment in segments) {
            val map = current as? Map<String, Any?> ?: return null
            current = map[segment] ?: return null
        }
        return (current as? Map<String, Any?>)?.filterNotNullValues()
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
        }
    }

    private fun buildRemoteConfigUrl(): String {
        val baseUrl =
            when (serverZone) {
                ServerZone.EU -> EU_REMOTE_CONFIG_URL
                else -> US_REMOTE_CONFIG_URL
            }
        return "$baseUrl/config?api_key=$apiKey&config_group=$CONFIG_GROUP"
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
@RestrictedAmplitudeFeature
fun ConfigMap.getString(
    key: String,
    default: String = "",
): String {
    return (this[key] as? String) ?: default
}

/**
 * Safely extracts a Boolean value from the config map with a default fallback.
 */
@RestrictedAmplitudeFeature
fun ConfigMap.getBoolean(
    key: String,
    default: Boolean = false,
): Boolean {
    return (this[key] as? Boolean) ?: default
}

/**
 * Safely extracts a Double value from the config map with a default fallback.
 */
@RestrictedAmplitudeFeature
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
@RestrictedAmplitudeFeature
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
@RestrictedAmplitudeFeature
fun ConfigMap.getStringList(
    key: String,
    default: List<String> = emptyList(),
): List<String> {
    return (this[key] as? List<*>)?.mapNotNull { it as? String } ?: default
}
