package com.amplitude.core.remoteconfig

import com.amplitude.common.jvm.ConsoleLogger
import com.amplitude.core.Configuration
import com.amplitude.core.ServerZone
import com.amplitude.core.Storage
import com.amplitude.core.events.BaseEvent
import com.amplitude.core.platform.EventPipeline
import com.amplitude.core.remoteconfig.RemoteConfigClient.Key
import com.amplitude.core.remoteconfig.RemoteConfigClient.Key.AnalyticsSdk
import com.amplitude.core.remoteconfig.RemoteConfigClient.Key.Diagnostics
import com.amplitude.core.remoteconfig.RemoteConfigClient.Key.SessionReplayPrivacyConfig
import com.amplitude.core.remoteconfig.RemoteConfigClient.Key.SessionReplaySamplingConfig
import com.amplitude.core.remoteconfig.RemoteConfigClient.Source
import com.amplitude.core.utilities.InMemoryStorage
import com.amplitude.core.utilities.http.HttpClient
import com.amplitude.core.utilities.http.ResponseHandler
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.spyk
import io.mockk.verify
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RemoteConfigClientTest {
    private val storage = InMemoryStorage()
    private val logger = spyk(ConsoleLogger())
    private val silentLogger =
        mockk<com.amplitude.common.Logger>(relaxed = true)
    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private fun createClient(
        serverZone: ServerZone = ServerZone.US,
        apiKey: String = "test-key",
        // Use emptyApiConfig to control whether to return empty config or default config
        emptyApiConfig: Boolean = true,
        // Custom response for testing edge cases
        customResponse: HttpClient.Response? = null,
        // Use logger that can be verified vs silent logger for clean output
        useVerifiableLogger: Boolean = false,
    ): RemoteConfigClientImpl {
        // Create a mock HttpClient that returns configurable responses
        val mockHttpClient = mockk<HttpClient>()
        every { mockHttpClient.request(any()) } returns (
            customResponse ?: HttpClient.Response(
                statusCode = 200,
                body =
                    if (emptyApiConfig) {
                        """{"configs": {}}"""
                    } else {
                        DEFAULT_API_REMOTE_CONFIG_JSON.trimIndent()
                    },
                headers = emptyMap(),
                statusMessage = "OK",
            )
        )

        return RemoteConfigClientImpl(
            apiKey = apiKey,
            serverZone = serverZone,
            coroutineScope = testScope,
            networkIODispatcher = testDispatcher,
            storageIODispatcher = testDispatcher,
            storage = storage,
            httpClient = mockHttpClient,
            logger = if (useVerifiableLogger) logger else silentLogger,
        )
    }

    // region Subscription Management

    @Test
    fun `subscribe with cached data - multiple callbacks for same key all receive updates`() =
        runTest {
            val client = createClient()
            val callbacks = mutableListOf<ConfigMap>()

            // Pre-populate storage with all expected keys to avoid invalidation
            storage.write(
                Storage.Constants.REMOTE_CONFIG,
                DEFAULT_STORED_REMOTE_CONFIG_JSON.trimIndent(),
            )
            storage.write(Storage.Constants.REMOTE_CONFIG_TIMESTAMP, "1234567890")

            // Subscribe multiple callbacks to same key
            client.subscribe(SessionReplayPrivacyConfig) { config, _, _ ->
                callbacks.add(
                    config,
                )
            }
            client.subscribe(SessionReplayPrivacyConfig) { config, _, _ ->
                callbacks.add(
                    config,
                )
            }
            client.subscribe(SessionReplayPrivacyConfig) { config, _, _ ->
                callbacks.add(
                    config,
                )
            }

            testDispatcher.scheduler.advanceUntilIdle()

            // 3 CACHE callbacks + 3 REMOTE callbacks (empty config from emptyApiConfig)
            assertEquals(6, callbacks.size)
            // First 3 are cache, last 3 are remote (empty)
            callbacks.take(3).forEach { assertEquals("medium", it["defaultMaskLevel"]) }
            callbacks.drop(3).forEach { assertTrue(it.isEmpty()) }
        }

    @Test
    fun `subscribe with cached data - different keys receive only their config`() =
        runTest {
            val client = createClient()
            val privacyCallbacks = mutableListOf<Pair<ConfigMap, Source>>()
            val samplingCallbacks = mutableListOf<Pair<ConfigMap, Source>>()

            // Pre-populate storage with both configs
            storage.write(
                Storage.Constants.REMOTE_CONFIG,
                DEFAULT_STORED_REMOTE_CONFIG_JSON.trimIndent(),
            )
            storage.write(Storage.Constants.REMOTE_CONFIG_TIMESTAMP, "1234567890")

            // Subscribe to different keys
            client.subscribe(SessionReplayPrivacyConfig) { config, source, _ ->
                privacyCallbacks.add(config to source)
            }
            client.subscribe(SessionReplaySamplingConfig) { config, source, _ ->
                samplingCallbacks.add(config to source)
            }

            testDispatcher.scheduler.advanceUntilIdle()

            // CACHE callback has specific config; REMOTE callback is empty (emptyApiConfig)
            assertTrue(privacyCallbacks.size >= 1)
            val privacyCache = privacyCallbacks.first { it.second == Source.CACHE }.first
            assertEquals("medium", privacyCache["defaultMaskLevel"])
            assertFalse(privacyCache.containsKey("sample_rate"))

            assertTrue(samplingCallbacks.size >= 1)
            val samplingCache = samplingCallbacks.first { it.second == Source.CACHE }.first
            assertEquals(1.0, samplingCache["sample_rate"])
            assertEquals(true, samplingCache["capture_enabled"])
        }

    @Test
    fun `subscribe with weak references - external counter tracks temp callback behavior`() =
        runTest {
            val client = createClient(emptyApiConfig = false)
            var tempCallbackInvocations = 0
            var persistentCallbackInvocations = 0

            storage.write(
                Storage.Constants.REMOTE_CONFIG,
                DEFAULT_STORED_REMOTE_CONFIG_JSON.trimIndent(),
            )
            storage.write(Storage.Constants.REMOTE_CONFIG_TIMESTAMP, "1234567890")

            // Add persistent callback that stays in memory
            val persistentCallback =
                RemoteConfigClient.RemoteConfigCallback { _, _, _ ->
                    persistentCallbackInvocations++
                }
            client.subscribe(SessionReplayPrivacyConfig, callback = persistentCallback)
            testDispatcher.scheduler.advanceUntilIdle()
            // Persistent callback should have received initial cache + remote
            assertEquals(
                2,
                persistentCallbackInvocations,
                "Persistent callback should receive initial cache",
            )

            // Reset timestamp to allow another remote fetch on next subscribe
            storage.write(Storage.Constants.REMOTE_CONFIG_TIMESTAMP, "0")

            // Create temp callbacks in limited scope with counter
            run {
                val tempCallback =
                    RemoteConfigClient.RemoteConfigCallback { _, _, _ ->
                        tempCallbackInvocations++
                    }
                client.subscribe(SessionReplayPrivacyConfig, callback = tempCallback)
            }
            testDispatcher.scheduler.advanceUntilIdle()

            // Temp callback should have received initial cache + remote
            assertEquals(2, tempCallbackInvocations, "Temp callbacks should receive initial cache")

            // Force GC and trigger cleanup
            repeat(5) {
                System.gc()
                Thread.sleep(10)
            }

            // Reset timestamp to allow another remote fetch on next subscribe
            storage.write(Storage.Constants.REMOTE_CONFIG_TIMESTAMP, "0")

            // Add new subscriber to trigger cleanup and another remote fetch
            client.subscribe(SessionReplayPrivacyConfig) { _, _, _ -> }
            testDispatcher.scheduler.advanceUntilIdle()

            // The test demonstrates that weak reference system works
            // Counter shows temp callback were invoked initially and then cleaned up
            assertTrue(tempCallbackInvocations >= 2)
            // Persistent callback should have received multiple remote updates across steps
            assertTrue(persistentCallbackInvocations >= 3)
        }

    @Test
    fun `subscribe with callback exceptions - isolate exception and don't cascade failure`() =
        runTest {
            val client = createClient(useVerifiableLogger = true)
            val workingCallbacks = mutableListOf<Pair<ConfigMap, Source>>()

            // Pre-populate storage to ensure callbacks are triggered
            storage.write(
                Storage.Constants.REMOTE_CONFIG,
                DEFAULT_STORED_REMOTE_CONFIG_JSON.trimIndent(),
            )
            storage.write(Storage.Constants.REMOTE_CONFIG_TIMESTAMP, "1234567890")

            // Add callback that throws exception
            client.subscribe(SessionReplayPrivacyConfig) { _, _, _ ->
                throw RuntimeException("Test exception")
            }

            // Add working callbacks
            client.subscribe(
                SessionReplayPrivacyConfig,
            ) { config, source, _ -> workingCallbacks.add(config to source) }
            client.subscribe(
                SessionReplayPrivacyConfig,
            ) { config, source, _ -> workingCallbacks.add(config to source) }

            testDispatcher.scheduler.advanceUntilIdle()

            // 2 CACHE + 2 REMOTE (empty from emptyApiConfig) = 4
            assertEquals(4, workingCallbacks.size)
            assertEquals(2, workingCallbacks.count { it.second == Source.CACHE })
            assertEquals(2, workingCallbacks.count { it.second == Source.REMOTE })
            verify {
                logger.error(
                    match<String> { it.contains("Exception in subscriber callback") },
                )
            }
        }

    @Test
    fun `subscribe with immediate cache notification - correct source and data`() =
        runTest {
            val client = createClient()

            // Pre-populate storage with nested config blob
            storage.write(
                Storage.Constants.REMOTE_CONFIG,
                """
                {
                    "sessionReplay": {
                        "sr_android_privacy_config": {
                            "defaultMaskLevel": "strict"
                        },
                        "sr_android_sampling_config": {
                            "sample_rate": 0.003,
                            "capture_enabled": false
                        }
                    }
                }
                """.trimIndent(),
            )
            storage.write(Storage.Constants.REMOTE_CONFIG_TIMESTAMP, "1234567890")

            val results = mutableListOf<Triple<ConfigMap, Source, Long>>()

            // Subscribe - should immediately get cached data, then REMOTE
            client.subscribe(SessionReplayPrivacyConfig) { config, source, timestamp ->
                results.add(Triple(config, source, timestamp))
            }

            testDispatcher.scheduler.advanceUntilIdle()

            // First callback is CACHE with original data
            assertTrue(results.size >= 1)
            val (cacheConfig, cacheSource, cacheTimestamp) = results.first { it.second == Source.CACHE }
            assertEquals(Source.CACHE, cacheSource)
            assertEquals(1234567890L, cacheTimestamp)
            assertEquals("strict", cacheConfig["defaultMaskLevel"])
        }

    @Test
    fun `subscribe multiple times with same callback - each creates separate registration`() =
        runTest {
            val client = createClient(useVerifiableLogger = true)
            var callCount = 0

            // Pre-populate storage to ensure callbacks are triggered
            storage.write(
                Storage.Constants.REMOTE_CONFIG,
                """
                {
                    "sessionReplay": {
                        "sr_android_privacy_config": {
                            "defaultMaskLevel": "moderate"
                        },
                        "sr_android_sampling_config": {
                            "sample_rate": 0.15,
                            "capture_enabled": true
                        }
                    }
                }
                """.trimIndent(),
            )
            storage.write(Storage.Constants.REMOTE_CONFIG_TIMESTAMP, "1234567890")

            val callback = RemoteConfigClient.RemoteConfigCallback { _, _, _ -> callCount++ }

            // Subscribe same callback reference multiple times
            client.subscribe(SessionReplayPrivacyConfig, callback = callback)
            client.subscribe(SessionReplayPrivacyConfig, callback = callback)
            client.subscribe(SessionReplayPrivacyConfig, callback = callback)

            testDispatcher.scheduler.advanceUntilIdle()

            // 3 CACHE + 3 REMOTE (empty from emptyApiConfig) = 6
            assertEquals(6, callCount)
            verify(exactly = 3) { logger.debug(match<String> { it.contains("Added subscriber") }) }
        }

    @Test
    fun `subscribe with old-format cache - migrated and delivered immediately`() =
        runTest {
            val client = createClient(useVerifiableLogger = true)
            val results = mutableListOf<Pair<ConfigMap, Source>>()

            // Pre-populate storage with old pre-flattened format (keys contain dots)
            storage.write(
                Storage.Constants.REMOTE_CONFIG,
                """
                {
                    "sessionReplay.sr_android_privacy_config": {
                        "defaultMaskLevel": "light"
                    }
                }
                """.trimIndent(),
            )
            storage.write(Storage.Constants.REMOTE_CONFIG_TIMESTAMP, "1234567890")

            client.subscribe(SessionReplayPrivacyConfig) { config, source, _ ->
                results.add(config to source)
            }

            testDispatcher.scheduler.advanceUntilIdle()

            // First delivery is migrated CACHE
            assertTrue(results.isNotEmpty())
            val (cacheConfig, cacheSource) = results.first { it.second == Source.CACHE }
            assertEquals(Source.CACHE, cacheSource)
            assertEquals("light", cacheConfig["defaultMaskLevel"])
            verify { logger.debug(match<String> { it.contains("old pre-flattened cache format") }) }
        }

    // endregion Subscription Management

    // region Rate Limiting

    @Test
    fun `consecutive subscribe calls during in-flight fetch - both should be notified`() =
        runTest {
            // Create a mock HttpClient that we can control when it returns
            val mockHttpClient = mockk<HttpClient>()
            val requestSlot = slot<HttpClient.Request>()

            every { mockHttpClient.request(capture(requestSlot)) } returns
                HttpClient.Response(
                    statusCode = 200,
                    body = DEFAULT_API_REMOTE_CONFIG_JSON.trimIndent(),
                    headers = emptyMap(),
                    statusMessage = "OK",
                )

            val client =
                RemoteConfigClientImpl(
                    apiKey = "test-key",
                    serverZone = ServerZone.US,
                    coroutineScope = testScope,
                    networkIODispatcher = testDispatcher,
                    storageIODispatcher = testDispatcher,
                    storage = storage,
                    httpClient = mockHttpClient,
                    logger = silentLogger,
                )

            val firstCallbackResults = mutableListOf<Pair<ConfigMap, Source>>()
            val secondCallbackResults = mutableListOf<Pair<ConfigMap, Source>>()

            // Ensure no rate limiting by setting timestamp to 0 (old enough)
            storage.write(Storage.Constants.REMOTE_CONFIG_TIMESTAMP, "0")

            // First subscribe call - should trigger HTTP request
            client.subscribe(SessionReplayPrivacyConfig) { config, source, _ ->
                firstCallbackResults.add(config to source)
            }

            // Second subscribe call immediately after - should NOT trigger another HTTP request
            // but should still get notified when first request completes
            client.subscribe(SessionReplayPrivacyConfig) { config, source, _ ->
                secondCallbackResults.add(config to source)
            }

            // Advance the dispatcher to complete both subscribe calls and the HTTP request
            testDispatcher.scheduler.advanceUntilIdle()

            // Verify HTTP request was made only once
            assertTrue(requestSlot.isCaptured, "Should have made exactly one HTTP request")

            // Verify both callbacks received the remote config
            assertTrue(
                firstCallbackResults.any { it.second == Source.REMOTE },
                "First callback should receive REMOTE config. Got: $firstCallbackResults",
            )
            assertTrue(
                secondCallbackResults.any { it.second == Source.REMOTE },
                "Second callback should receive REMOTE config. Got: $secondCallbackResults",
            )

            // Verify both got the same config data
            val firstRemoteConfig = firstCallbackResults.first { it.second == Source.REMOTE }.first
            val secondRemoteConfig = secondCallbackResults.first { it.second == Source.REMOTE }.first
            assertEquals(firstRemoteConfig, secondRemoteConfig, "Both callbacks should get the same config")
        }

    @Test
    fun `in-flight fetch protection - multiple consecutive calls result in single request with proper skipping`() =
        runTest {
            val requestSlot = slot<HttpClient.Request>()
            val requestSlots = mutableListOf<HttpClient.Request>()
            val mockHttpClient = mockk<HttpClient>()

            // Capture all requests to verify only one is made
            every { mockHttpClient.request(capture(requestSlot)) } answers {
                requestSlots.add(requestSlot.captured)
                HttpClient.Response(
                    statusCode = 200,
                    body = DEFAULT_API_REMOTE_CONFIG_JSON.trimIndent(),
                    headers = emptyMap(),
                    statusMessage = "OK",
                )
            }

            val client =
                RemoteConfigClientImpl(
                    apiKey = "test-key",
                    serverZone = ServerZone.US,
                    coroutineScope = testScope,
                    networkIODispatcher = testDispatcher,
                    storageIODispatcher = testDispatcher,
                    storage = storage,
                    httpClient = mockHttpClient,
                    logger = logger,
                )

            val callbackResults = mutableListOf<Pair<ConfigMap, Source>>()
            client.subscribe(SessionReplayPrivacyConfig) { config, source, _ ->
                callbackResults.add(config to source)
            }

            // Make multiple consecutive updateConfigs calls
            client.updateConfigs()
            client.updateConfigs()
            client.updateConfigs()

            testDispatcher.scheduler.advanceUntilIdle()

            // Verify only one HTTP request was made despite multiple calls
            assertEquals(1, requestSlots.size, "Should make only one HTTP request despite multiple updateConfigs calls")

            // Verify callback received remote config
            assertTrue(
                callbackResults.size == 1 &&
                    callbackResults.first().second == Source.REMOTE,
                "Should receive one REMOTE config from successful fetch",
            )

            // Reset timestamp and verify that after fetch completes, new calls can proceed
            storage.write(Storage.Constants.REMOTE_CONFIG_TIMESTAMP, "0")
            requestSlots.clear()

            client.updateConfigs()
            testDispatcher.scheduler.advanceUntilIdle()

            // Should allow new request after previous fetch completed
            assertEquals(1, requestSlots.size, "Should allow new request after previous fetch completed")
        }

    @Test
    fun `rate limiting - blocks calls within interval and allows calls after interval with proper timing`() =
        runTest {
            val requestSlots = mutableListOf<HttpClient.Request>()
            val mockHttpClient = mockk<HttpClient>()

            every { mockHttpClient.request(any()) } answers {
                val request = firstArg<HttpClient.Request>()
                requestSlots.add(request)
                HttpClient.Response(
                    statusCode = 200,
                    body = DEFAULT_API_REMOTE_CONFIG_JSON.trimIndent(),
                    headers = emptyMap(),
                    statusMessage = "OK",
                )
            }

            val client =
                RemoteConfigClientImpl(
                    apiKey = "test-key",
                    serverZone = ServerZone.US,
                    coroutineScope = testScope,
                    networkIODispatcher = testDispatcher,
                    storageIODispatcher = testDispatcher,
                    storage = storage,
                    httpClient = mockHttpClient,
                    logger = logger,
                )

            val callbackResults = mutableListOf<Pair<ConfigMap, Source>>()
            client.subscribe(SessionReplayPrivacyConfig) { config, source, _ ->
                callbackResults.add(config to source)
            }

            // Test 1: No previous timestamp - should allow request
            client.updateConfigs()
            testDispatcher.scheduler.advanceUntilIdle()
            assertEquals(1, requestSlots.size, "Should allow request when no previous timestamp exists")

            // Test 2: Set recent timestamp (within 5 minutes) - should block requests
            val recentTimestamp = System.currentTimeMillis() - (2 * 60 * 1_000L) // 2 minutes ago
            storage.write(Storage.Constants.REMOTE_CONFIG_TIMESTAMP, recentTimestamp.toString())
            requestSlots.clear()

            client.updateConfigs()
            client.updateConfigs() // Multiple calls should all be blocked
            testDispatcher.scheduler.advanceUntilIdle()

            assertTrue(requestSlots.isEmpty(), "Should block requests within 5-minute rate limit window")

            // Test 3: Set old timestamp (older than 5 minutes) - should allow request
            val oldTimestamp = System.currentTimeMillis() - (6 * 60 * 1_000L) // 6 minutes ago
            storage.write(Storage.Constants.REMOTE_CONFIG_TIMESTAMP, oldTimestamp.toString())
            requestSlots.clear()

            client.updateConfigs()
            testDispatcher.scheduler.advanceUntilIdle()

            assertEquals(1, requestSlots.size, "Should allow request after 5-minute rate limit window expires")

            // Test 4: Verify timestamp gets updated after successful fetch
            val storedTimestamp = storage.read(Storage.Constants.REMOTE_CONFIG_TIMESTAMP)?.toLongOrNull()
            val currentTime = System.currentTimeMillis()

            assertTrue(
                storedTimestamp != null && storedTimestamp > oldTimestamp,
                "Should update timestamp after successful fetch",
            )
            assertTrue(
                storedTimestamp != null && (currentTime - storedTimestamp) < 1000L,
                "Updated timestamp should be very recent (within 1 second)",
            )

            // Verify callback received remote configs from allowed requests
            assertTrue(
                callbackResults.any { it.second == Source.REMOTE },
                "Should receive REMOTE configs from successful fetches",
            )
        }

    // endregion Rate Limiting

    // region API Response Edge Cases

    @Test
    fun `API returns null response body`() =
        runTest {
            val client =
                createClient(
                    customResponse =
                        HttpClient.Response(
                            statusCode = 200,
                            body = null,
                            headers = emptyMap(),
                            statusMessage = "OK",
                        ),
                )

            storage.write(
                Storage.Constants.REMOTE_CONFIG,
                DEFAULT_STORED_REMOTE_CONFIG_JSON.trimIndent(),
            )
            storage.write(Storage.Constants.REMOTE_CONFIG_TIMESTAMP, "1234567890")

            var callbackCount = 0
            client.subscribe(SessionReplayPrivacyConfig) { _, _, _ -> callbackCount++ }
            testDispatcher.scheduler.advanceUntilIdle()

            // Should still get cached config, no crash
            assertTrue(callbackCount == 1, "Should receive cached config despite null API response")
            client.updateConfigs()
            testDispatcher.scheduler.advanceUntilIdle()
            // No change, no crash
            assertTrue(callbackCount == 1, "Should receive cached config despite null API response")
        }

    @Test
    fun `API returns malformed JSON`() =
        runTest {
            val client =
                createClient(
                    customResponse =
                        HttpClient.Response(
                            statusCode = 200,
                            body = """{this is not valid json at all}""",
                            headers = emptyMap(),
                            statusMessage = "OK",
                        ),
                )

            storage.write(
                Storage.Constants.REMOTE_CONFIG,
                DEFAULT_STORED_REMOTE_CONFIG_JSON.trimIndent(),
            )

            var callbackCount = 0
            client.subscribe(SessionReplayPrivacyConfig) { _, _, _ -> callbackCount++ }
            testDispatcher.scheduler.advanceUntilIdle()
            assertTrue(callbackCount == 1, "Should receive cached config despite malformed JSON")

            client.updateConfigs()
            testDispatcher.scheduler.advanceUntilIdle()
            assertTrue(callbackCount == 1, "Should receive cached config despite malformed JSON")
        }

    @Test
    fun `API returns JSON with null config fields`() =
        runTest {
            val client =
                createClient(
                    customResponse =
                        HttpClient.Response(
                            statusCode = 200,
                            body =
                                """
                                {
                                    "configs": {
                                        "sessionReplay": {
                                            "sr_android_privacy_config": null,
                                            "sr_android_sampling_config": {
                                                "sample_rate": null,
                                                "capture_enabled": null
                                            }
                                        }
                                    }
                                }
                                """.trimIndent(),
                            headers = emptyMap(),
                            statusMessage = "OK",
                        ),
                )

            var callbackCount = 0
            client.subscribe(SessionReplayPrivacyConfig) { _, _, _ -> callbackCount++ }
            client.subscribe(SessionReplaySamplingConfig) { _, _, _ -> callbackCount++ }
            testDispatcher.scheduler.advanceUntilIdle()

            client.updateConfigs() // Should handle null fields gracefully
            testDispatcher.scheduler.advanceUntilIdle()

            // CACHE from pre-populated storage + REMOTE with resolved (possibly empty) config
            assertTrue(callbackCount >= 2, "Subscribers should receive CACHE + REMOTE callbacks")
        }

    @Test
    fun `API returns JSON with wrong data types`() =
        runTest {
            val client =
                createClient(
                    customResponse =
                        HttpClient.Response(
                            statusCode = 200,
                            body =
                                """
                                {
                                    "configs": {
                                        "sessionReplay": {
                                            "sr_android_privacy_config": "this should be an object",
                                            "sr_android_sampling_config": [1, 2, 3, "array instead of object"]
                                        }
                                    }
                                }
                                """.trimIndent(),
                            headers = emptyMap(),
                            statusMessage = "OK",
                        ),
                )

            storage.write(
                Storage.Constants.REMOTE_CONFIG,
                DEFAULT_STORED_REMOTE_CONFIG_JSON.trimIndent(),
            )
            storage.write(Storage.Constants.REMOTE_CONFIG_TIMESTAMP, "1234567890")

            val results = mutableListOf<Source>()
            client.subscribe(SessionReplayPrivacyConfig) { _, source, _ ->
                results.add(source)
            }
            testDispatcher.scheduler.advanceUntilIdle()
            // CACHE from stored data + REMOTE (dot-path fails on wrong types, delivers empty)
            assertTrue(results.contains(Source.CACHE), "Should receive cached config")
            assertTrue(results.size >= 1, "Client should handle wrong data types without crashing")
        }

    @Test
    fun `API returns empty string response`() =
        runTest {
            val client =
                createClient(
                    customResponse =
                        HttpClient.Response(
                            statusCode = 200,
                            body = "",
                            headers = emptyMap(),
                            statusMessage = "OK",
                        ),
                )

            var callbackInvoked = false
            client.subscribe(SessionReplayPrivacyConfig) { _, _, _ ->
                callbackInvoked = true
            }
            testDispatcher.scheduler.advanceUntilIdle()

            client.updateConfigs()
            testDispatcher.scheduler.advanceUntilIdle()
            assertFalse(callbackInvoked, "Should not invoke callback for empty response")
        }

    @Test
    fun `API returns 500 error`() =
        runTest {
            val client =
                createClient(
                    customResponse =
                        HttpClient.Response(
                            statusCode = 500,
                            body = """{"error": "Something went wrong"}""",
                            headers = emptyMap(),
                            statusMessage = "Internal Server Error",
                        ),
                )

            storage.write(
                Storage.Constants.REMOTE_CONFIG,
                DEFAULT_STORED_REMOTE_CONFIG_JSON.trimIndent(),
            )
            storage.write(Storage.Constants.REMOTE_CONFIG_TIMESTAMP, "1234567890")

            var callbackCount = 0
            client.subscribe(SessionReplayPrivacyConfig) { _, _, _ -> callbackCount++ }
            testDispatcher.scheduler.advanceUntilIdle()
            assertTrue(callbackCount == 1, "Should receive cached config despite server error")

            client.updateConfigs()
            testDispatcher.scheduler.advanceUntilIdle()
            assertTrue(callbackCount == 1, "Should receive cached config despite server error")
        }

    @Test
    fun `API returns deeply nested garbage JSON`() =
        runTest {
            val client =
                createClient(
                    customResponse =
                        HttpClient.Response(
                            statusCode = 200,
                            body =
                                """
                                {
                                    "configs": {
                                        "sessionReplay": {
                                            "sr_android_privacy_config": {
                                                "nested": {
                                                    "deeply": {
                                                        "very": {
                                                            "much": {
                                                                "so": null,
                                                                "random": [{"key": "value"}, null, 42, true]
                                                            }
                                                        }
                                                    }
                                                },
                                                "defaultMaskLevel": 12345
                                            }
                                        }
                                    }
                                }
                                """.trimIndent(),
                            headers = emptyMap(),
                            statusMessage = "OK",
                        ),
                )

            var callbackCount = 0
            client.subscribe(SessionReplayPrivacyConfig) { _, _, _ -> callbackCount++ }
            testDispatcher.scheduler.advanceUntilIdle()
            assertTrue(
                callbackCount == 1,
                "Client should handle deeply nested garbage without crashing",
            )

            // Reset timestamp to allow another remote fetch for updateConfigs()
            storage.write(Storage.Constants.REMOTE_CONFIG_TIMESTAMP, "0")

            client.updateConfigs()
            testDispatcher.scheduler.advanceUntilIdle()
            assertTrue(
                callbackCount == 2,
                "Client should handle deeply nested garbage without crashing",
            )
        }

    @Test
    fun `API returns valid JSON but missing configs key`() =
        runTest {
            val client =
                createClient(
                    customResponse =
                        HttpClient.Response(
                            statusCode = 200,
                            body =
                                """
                                {
                                    "status": "success",
                                    "message": "Everything is fine",
                                    "data": {
                                        "someOtherField": "value"
                                    }
                                }
                                """.trimIndent(),
                            headers = emptyMap(),
                            statusMessage = "OK",
                        ),
                )

            var callbackCount = 0
            client.subscribe(SessionReplayPrivacyConfig) { _, _, _ -> callbackCount++ }
            testDispatcher.scheduler.advanceUntilIdle()

            client.updateConfigs()
            testDispatcher.scheduler.advanceUntilIdle()
            assertTrue(callbackCount == 0, "Should not invoke callback when configs key is missing")
        }

    // endregion API Response Edge Cases

    // region Additional Coverage

    @Test
    fun `server zone EU - uses correct endpoint URL`() =
        runTest {
            val requestSlot = slot<HttpClient.Request>()
            val mockHttpClient = mockk<HttpClient>()
            every { mockHttpClient.request(capture(requestSlot)) } returns
                HttpClient.Response(
                    statusCode = 200,
                    body = """{"configs": {}}""",
                    headers = emptyMap(),
                    statusMessage = "OK",
                )

            val client =
                RemoteConfigClientImpl(
                    apiKey = "test-key-eu",
                    serverZone = ServerZone.EU,
                    coroutineScope = testScope,
                    networkIODispatcher = testDispatcher,
                    storageIODispatcher = testDispatcher,
                    storage = storage,
                    httpClient = mockHttpClient,
                    logger = logger,
                )

            client.updateConfigs()
            testDispatcher.scheduler.advanceUntilIdle()

            // Verify correct EU endpoint URL is used
            assertTrue(requestSlot.isCaptured, "Should have captured HTTP request")
            val capturedRequest = requestSlot.captured
            val url = capturedRequest.url

            assertTrue(url.contains("sr-client-cfg.eu.amplitude.com"), "Should use EU endpoint")
            assertTrue(url.contains("api_key=test-key-eu"), "Should include correct API key")
            assertTrue(
                url.contains("config_keys=analyticsSDK"),
                "Should include analyticsSDK fetch key",
            )
            assertTrue(
                url.contains("config_keys=sessionReplay"),
                "Should include sessionReplay fetch key",
            )
            assertEquals(
                HttpClient.Request.Method.GET,
                capturedRequest.method,
                "Should use GET method",
            )
        }

    @Test
    fun `server zone US - uses correct endpoint URL`() =
        runTest {
            val requestSlot = slot<HttpClient.Request>()
            val mockHttpClient = mockk<HttpClient>()
            every { mockHttpClient.request(capture(requestSlot)) } returns
                HttpClient.Response(
                    statusCode = 200,
                    body = """{"configs": {}}""",
                    headers = emptyMap(),
                    statusMessage = "OK",
                )

            val client =
                RemoteConfigClientImpl(
                    apiKey = "test-key-us",
                    serverZone = ServerZone.US,
                    coroutineScope = testScope,
                    networkIODispatcher = testDispatcher,
                    storageIODispatcher = testDispatcher,
                    storage = storage,
                    httpClient = mockHttpClient,
                    logger = logger,
                )

            client.updateConfigs()
            testDispatcher.scheduler.advanceUntilIdle()

            // Verify correct US endpoint URL is used
            assertTrue(requestSlot.isCaptured, "Should have captured HTTP request")
            val capturedRequest = requestSlot.captured
            val url = capturedRequest.url

            assertTrue(url.contains("sr-client-cfg.amplitude.com"), "Should use US endpoint")
            assertTrue(url.contains("api_key=test-key-us"), "Should include correct API key")
            assertTrue(
                url.contains("config_keys=analyticsSDK"),
                "Should include analyticsSDK fetch key",
            )
            assertTrue(
                url.contains("config_keys=sessionReplay"),
                "Should include sessionReplay fetch key",
            )
            assertEquals(
                HttpClient.Request.Method.GET,
                capturedRequest.method,
                "Should use GET method",
            )
        }

    @Test
    fun `ConfigMap extension functions - safe type extraction`() {
        val testConfig: ConfigMap =
            mapOf(
                "stringValue" to "test",
                "booleanValue" to true,
                "doubleValue" to 3.14,
                "intValue" to 42,
                "numberAsString" to "123",
                "stringList" to listOf("a", "b", "c"),
                "mixedList" to listOf("string", 123),
                "wrongType" to mapOf("nested" to "object"),
            )

        // Test getString
        assertEquals("test", testConfig.getString("stringValue"))
        assertEquals("default", testConfig.getString("nonExistent", "default"))
        assertEquals("", testConfig.getString("nonExistent"))

        // Test getBoolean
        assertEquals(true, testConfig.getBoolean("booleanValue"))
        assertEquals(false, testConfig.getBoolean("nonExistent"))
        assertEquals(false, testConfig.getBoolean("stringValue"))

        // Test getDouble with various input types
        assertEquals(3.14, testConfig.getDouble("doubleValue"))
        assertEquals(42.0, testConfig.getDouble("intValue")) // Number conversion
        assertEquals(123.0, testConfig.getDouble("numberAsString")) // String conversion
        assertEquals(0.0, testConfig.getDouble("nonExistent"))
        assertEquals(0.0, testConfig.getDouble("wrongType"))

        // Test getInt with various input types
        assertEquals(42, testConfig.getInt("intValue"))
        assertEquals(3, testConfig.getInt("doubleValue")) // Number conversion
        assertEquals(123, testConfig.getInt("numberAsString")) // String conversion
        assertEquals(0, testConfig.getInt("nonExistent"))
        assertEquals(0, testConfig.getInt("wrongType"))

        // Test getStringList
        assertEquals(listOf("a", "b", "c"), testConfig.getStringList("stringList"))
        assertEquals(listOf("string"), testConfig.getStringList("mixedList")) // Filters non-strings
        assertEquals(emptyList<String>(), testConfig.getStringList("nonExistent"))
        assertEquals(emptyList<String>(), testConfig.getStringList("wrongType"))
    }

    @Test
    fun `successful remote config storage write`() =
        runTest {
            val client =
                createClient(
                    emptyApiConfig = false,
                    useVerifiableLogger = true,
                    customResponse =
                        HttpClient.Response(
                            statusCode = 200,
                            body = DEFAULT_API_REMOTE_CONFIG_JSON.trimIndent(),
                            headers = emptyMap(),
                            statusMessage = "OK",
                        ),
                )

            var remoteCallbacks = mutableListOf<Pair<ConfigMap, Source>>()
            client.subscribe(SessionReplayPrivacyConfig) { config, source, _ ->
                remoteCallbacks.add(config to source)
            }
            testDispatcher.scheduler.advanceUntilIdle()

            client.updateConfigs()
            testDispatcher.scheduler.advanceUntilIdle()

            // Should have stored config and triggered REMOTE notification
            assertTrue(
                remoteCallbacks.any { it.second == Source.REMOTE },
                "Should receive REMOTE config",
            )
            verify {
                logger.debug(
                    match<String> { it.contains("Successfully stored remote configs to storage") },
                )
            }
        }

    @Test
    fun `storage read failure - handles exceptions gracefully`() =
        runTest {
            // Use a custom storage that throws exceptions
            val failingStorage =
                object : Storage {
                    override fun read(key: Storage.Constants): String? {
                        throw RuntimeException("Storage read failed")
                    }

                    override suspend fun write(
                        key: Storage.Constants,
                        value: String,
                    ) {
                    }

                    override suspend fun remove(key: Storage.Constants) {}

                    override suspend fun writeEvent(event: BaseEvent) {}

                    override suspend fun rollover() {}

                    override fun readEventsContent(): List<Any> = emptyList()

                    override suspend fun getEventsString(content: Any): String = ""

                    override fun getResponseHandler(
                        eventPipeline: EventPipeline,
                        configuration: Configuration,
                        scope: CoroutineScope,
                        storageDispatcher: CoroutineDispatcher,
                    ): ResponseHandler = mockk()
                }

            val client =
                RemoteConfigClientImpl(
                    apiKey = "test-key",
                    serverZone = ServerZone.US,
                    coroutineScope = testScope,
                    networkIODispatcher = testDispatcher,
                    storageIODispatcher = testDispatcher,
                    storage = failingStorage,
                    httpClient =
                        mockk<HttpClient>().apply {
                            every { request(any()) } returns
                                HttpClient.Response(
                                    statusCode = 200,
                                    body = """{"configs": {}}""",
                                    headers = emptyMap(),
                                    statusMessage = "OK",
                                )
                        },
                    logger = logger,
                )

            var callbackInvoked = false
            client.subscribe(SessionReplayPrivacyConfig) { _, _, _ ->
                callbackInvoked = true
            }
            testDispatcher.scheduler.advanceUntilIdle()

            // Should handle storage failure gracefully - no immediate callback
            assertFalse(callbackInvoked, "Should handle storage read failure gracefully")
            verify {
                logger.error(
                    match<String> { it.contains("Failed to parse all stored configs") },
                )
            }
        }

    @Test
    fun `corrupted storage data - handles parsing exceptions`() =
        runTest {
            val client = createClient(useVerifiableLogger = true)

            // Write corrupted JSON to storage
            storage.write(Storage.Constants.REMOTE_CONFIG, "{this is corrupted json")
            storage.write(Storage.Constants.REMOTE_CONFIG_TIMESTAMP, "not_a_number")

            val results = mutableListOf<Source>()
            client.subscribe(SessionReplayPrivacyConfig) { _, source, _ ->
                results.add(source)
            }
            testDispatcher.scheduler.advanceUntilIdle()

            // No CACHE callback (corrupted), but REMOTE callback fires (empty from emptyApiConfig)
            assertFalse(results.contains(Source.CACHE), "Should not deliver corrupted cache")
            assertTrue(results.contains(Source.REMOTE), "Should still receive REMOTE notification")
            verify {
                logger.error(
                    match<String> { it.contains("Failed to parse all stored configs") },
                )
            }
        }

    @Test
    fun `storage with non-map values at top level - still resolves valid nested paths`() =
        runTest {
            val client = createClient(useVerifiableLogger = true)

            // Write storage with mixed data types at top level
            storage.write(
                Storage.Constants.REMOTE_CONFIG,
                """
                {
                    "sessionReplay": {
                        "sr_android_privacy_config": {
                            "defaultMaskLevel": "medium"
                        },
                        "sr_android_sampling_config": {
                            "sample_rate": 1.0,
                            "capture_enabled": true
                        }
                    },
                    "invalidKey1": "string_instead_of_object",
                    "invalidKey2": [1, 2, 3]
                }
                """.trimIndent(),
            )
            storage.write(Storage.Constants.REMOTE_CONFIG_TIMESTAMP, "1234567890")

            var callbackInvoked = false
            client.subscribe(SessionReplayPrivacyConfig) { _, _, _ ->
                callbackInvoked = true
            }
            testDispatcher.scheduler.advanceUntilIdle()

            // Should resolve the valid nested path despite other top-level junk
            assertTrue(callbackInvoked, "Should resolve valid dot-path despite non-map top-level entries")
        }

    // endregion Additional Coverage

    // region DeliveryMode

    @org.junit.jupiter.api.Nested
    inner class WaitForRemoteDeliveryMode {
        @Test
        fun `WaitForRemote delivers remote when fetch returns within timeout`() =
            runTest {
                val client = createClient(emptyApiConfig = false)
                val results = mutableListOf<Triple<ConfigMap, Source, Long>>()
                storage.write(Storage.Constants.REMOTE_CONFIG_TIMESTAMP, "0")

                client.subscribe(
                    key = SessionReplayPrivacyConfig,
                    deliveryMode = RemoteConfigClient.DeliveryMode.WaitForRemote(timeoutMs = 5_000L),
                ) { config, source, timestamp ->
                    results.add(Triple(config, source, timestamp))
                }

                testDispatcher.scheduler.advanceUntilIdle()

                assertEquals(1, results.size, "Expected exactly one initial delivery, got: $results")
                assertEquals(Source.REMOTE, results.single().second)
                assertEquals("medium", results.single().first["defaultMaskLevel"])
            }

        @Test
        fun `WaitForRemote times out and falls back to cached config`() =
            runTest {
                // HTTP returns 500: fetchRemoteConfig returns null, regular path does not
                // deliver. WaitForRemote then times out and falls back to cache.
                val mockHttpClient = mockk<HttpClient>()
                every { mockHttpClient.request(any()) } returns
                    HttpClient.Response(
                        statusCode = 500,
                        body = "",
                        headers = emptyMap(),
                        statusMessage = "Internal Server Error",
                    )
                val client =
                    RemoteConfigClientImpl(
                        apiKey = "test-key",
                        serverZone = ServerZone.US,
                        coroutineScope = testScope,
                        networkIODispatcher = testDispatcher,
                        storageIODispatcher = testDispatcher,
                        storage = storage,
                        httpClient = mockHttpClient,
                        logger = silentLogger,
                    )

                // Pre-populate cache so timeout fallback has data to deliver.
                storage.write(
                    Storage.Constants.REMOTE_CONFIG,
                    DEFAULT_STORED_REMOTE_CONFIG_JSON.trimIndent(),
                )
                storage.write(Storage.Constants.REMOTE_CONFIG_TIMESTAMP, "1234567890")

                val results = mutableListOf<Triple<ConfigMap, Source, Long>>()
                client.subscribe(
                    key = SessionReplayPrivacyConfig,
                    deliveryMode = RemoteConfigClient.DeliveryMode.WaitForRemote(timeoutMs = 100L),
                ) { config, source, timestamp ->
                    results.add(Triple(config, source, timestamp))
                }

                testDispatcher.scheduler.advanceUntilIdle()

                assertEquals(1, results.size, "Expected one timeout-fallback delivery, got: $results")
                val (config, source, timestamp) = results.single()
                assertEquals(Source.CACHE, source)
                assertEquals("medium", config["defaultMaskLevel"])
                assertEquals(1234567890L, timestamp)
            }

        @Test
        fun `WaitForRemote falls back to empty config when cache is also empty`() =
            runTest {
                // HTTP returns 500: no cache, no remote. Fallback should be empty.
                val mockHttpClient = mockk<HttpClient>()
                every { mockHttpClient.request(any()) } returns
                    HttpClient.Response(
                        statusCode = 500,
                        body = "",
                        headers = emptyMap(),
                        statusMessage = "Internal Server Error",
                    )
                val client =
                    RemoteConfigClientImpl(
                        apiKey = "test-key",
                        serverZone = ServerZone.US,
                        coroutineScope = testScope,
                        networkIODispatcher = testDispatcher,
                        storageIODispatcher = testDispatcher,
                        storage = storage,
                        httpClient = mockHttpClient,
                        logger = silentLogger,
                    )

                val results = mutableListOf<Triple<ConfigMap, Source, Long>>()
                client.subscribe(
                    key = Diagnostics,
                    deliveryMode = RemoteConfigClient.DeliveryMode.WaitForRemote(timeoutMs = 50L),
                ) { config, source, timestamp ->
                    results.add(Triple(config, source, timestamp))
                }

                testDispatcher.scheduler.advanceUntilIdle()

                assertEquals(1, results.size, "Expected one timeout-fallback delivery, got: $results")
                assertTrue(results.single().first.isEmpty(), "Expected empty config fallback")
                assertEquals(Source.CACHE, results.single().second)
            }

        @Test
        fun `WaitForRemote does not deliver cache up front before remote arrives`() =
            runTest {
                val client = createClient(emptyApiConfig = false)

                // Pre-populate cache to ensure DeliveryMode.All would have delivered it.
                storage.write(
                    Storage.Constants.REMOTE_CONFIG,
                    DEFAULT_STORED_REMOTE_CONFIG_JSON.trimIndent(),
                )
                storage.write(Storage.Constants.REMOTE_CONFIG_TIMESTAMP, "0")

                val sources = mutableListOf<Source>()
                client.subscribe(
                    key = SessionReplayPrivacyConfig,
                    deliveryMode = RemoteConfigClient.DeliveryMode.WaitForRemote(timeoutMs = 5_000L),
                ) { _, source, _ ->
                    sources.add(source)
                }

                testDispatcher.scheduler.advanceUntilIdle()

                // WaitForRemote should NOT have delivered cache first;
                // only the remote callback should fire.
                assertEquals(listOf(Source.REMOTE), sources)
            }

        @Test
        fun `default subscribe overload preserves existing All semantics`() =
            runTest {
                val client = createClient(emptyApiConfig = false)
                val sources = mutableListOf<Source>()
                storage.write(
                    Storage.Constants.REMOTE_CONFIG,
                    DEFAULT_STORED_REMOTE_CONFIG_JSON.trimIndent(),
                )
                storage.write(Storage.Constants.REMOTE_CONFIG_TIMESTAMP, "0")

                // The pre-existing single-arg overload must still deliver cache then remote.
                client.subscribe(SessionReplayPrivacyConfig) { _, source, _ ->
                    sources.add(source)
                }

                testDispatcher.scheduler.advanceUntilIdle()

                // Cache delivered first, then remote.
                assertEquals(listOf(Source.CACHE, Source.REMOTE), sources)
            }

        @Test
        fun `WaitForRemote delivers fresh remote that arrives after timeout fallback fires`() =
            runTest {
                // Regression: when the timeout fallback wins the gate first and a
                // fresh remote arrives later, the subscriber must still receive
                // the remote update — not stay stuck on the stale fallback.
                //
                // Repro: first fetch fails (500), so the timeout fallback fires
                // and delivers a cached value. Then the rate-limit window is
                // reset and a successful fetch is triggered. The subscriber
                // should receive the fresh REMOTE delivery as a subsequent update.
                var responseProvider: () -> HttpClient.Response = {
                    HttpClient.Response(
                        statusCode = 500,
                        body = "",
                        headers = emptyMap(),
                        statusMessage = "Internal Server Error",
                    )
                }
                val mockHttpClient = mockk<HttpClient>()
                every { mockHttpClient.request(any()) } answers { responseProvider() }

                val client =
                    RemoteConfigClientImpl(
                        apiKey = "test-key",
                        serverZone = ServerZone.US,
                        coroutineScope = testScope,
                        networkIODispatcher = testDispatcher,
                        storageIODispatcher = testDispatcher,
                        storage = storage,
                        httpClient = mockHttpClient,
                        logger = silentLogger,
                    )

                // Pre-populate cache so the timeout fallback has data to deliver.
                storage.write(
                    Storage.Constants.REMOTE_CONFIG,
                    DEFAULT_STORED_REMOTE_CONFIG_JSON.trimIndent(),
                )
                storage.write(Storage.Constants.REMOTE_CONFIG_TIMESTAMP, "1234567890")

                val results = mutableListOf<Pair<ConfigMap, Source>>()
                client.subscribe(
                    key = SessionReplayPrivacyConfig,
                    deliveryMode = RemoteConfigClient.DeliveryMode.WaitForRemote(timeoutMs = 50L),
                ) { config, source, _ ->
                    results.add(config to source)
                }

                testDispatcher.scheduler.advanceUntilIdle()

                // Timeout fallback fired with cache.
                assertEquals(
                    listOf(Source.CACHE),
                    results.map { it.second },
                    "Expected only the cache fallback before remote arrives, got: $results",
                )

                // Now flip the response to a fresh successful payload and trigger
                // another fetch. The previously-gated subscriber must receive
                // the fresh remote as a subsequent update.
                responseProvider = {
                    HttpClient.Response(
                        statusCode = 200,
                        body = DEFAULT_API_REMOTE_CONFIG_JSON.trimIndent(),
                        headers = emptyMap(),
                        statusMessage = "OK",
                    )
                }
                storage.write(Storage.Constants.REMOTE_CONFIG_TIMESTAMP, "0")

                client.updateConfigs()
                testDispatcher.scheduler.advanceUntilIdle()

                assertEquals(
                    listOf(Source.CACHE, Source.REMOTE),
                    results.map { it.second },
                    "Subscriber must receive fresh remote after the timeout fallback, got: $results",
                )
            }

        @Test
        fun `WaitForRemote unblocks immediately when key is absent from successful fetch`() =
            runTest {
                // When the response is successful but does NOT include the
                // requested key, all subscribers still get a REMOTE notification
                // with emptyMap(). WaitForRemote is unblocked immediately.
                val mockHttpClient = mockk<HttpClient>()
                every { mockHttpClient.request(any()) } returns
                    HttpClient.Response(
                        statusCode = 200,
                        // sessionReplay only — ANALYTICS_SDK/DIAGNOSTICS absent.
                        body = DEFAULT_API_REMOTE_CONFIG_JSON.trimIndent(),
                        headers = emptyMap(),
                        statusMessage = "OK",
                    )
                val client =
                    RemoteConfigClientImpl(
                        apiKey = "test-key",
                        serverZone = ServerZone.US,
                        coroutineScope = testScope,
                        networkIODispatcher = testDispatcher,
                        storageIODispatcher = testDispatcher,
                        storage = storage,
                        httpClient = mockHttpClient,
                        logger = silentLogger,
                    )

                val results = mutableListOf<Triple<ConfigMap, Source, Long>>()
                client.subscribe(
                    key = AnalyticsSdk,
                    deliveryMode = RemoteConfigClient.DeliveryMode.WaitForRemote(timeoutMs = 60_000L),
                ) { config, source, timestamp ->
                    results.add(Triple(config, source, timestamp))
                }

                // Run pending tasks. NOT advancing near the 60_000ms timeout.
                testDispatcher.scheduler.runCurrent()
                testDispatcher.scheduler.advanceTimeBy(10L)
                testDispatcher.scheduler.runCurrent()

                assertEquals(
                    1,
                    results.size,
                    "Expected exactly one delivery before the timeout, got: $results",
                )
                val (config, source, _) = results.single()
                assertEquals(Source.REMOTE, source, "Absent-key delivery should report Source.REMOTE")
                assertTrue(
                    config.isEmpty(),
                    "Expected empty config for absent key, got: $config",
                )
            }

        @Test
        fun `WaitForRemote unblocks immediately on empty successful fetch`() =
            runTest {
                // When the server returns 200 with {"configs": {}}, all subscribers
                // are notified with REMOTE + emptyMap(). WaitForRemote is unblocked
                // by the REMOTE delivery, not the timeout fallback.
                val client = createClient(emptyApiConfig = true) // returns {"configs":{}}
                storage.write(Storage.Constants.REMOTE_CONFIG_TIMESTAMP, "0")

                val results = mutableListOf<Triple<ConfigMap, Source, Long>>()
                client.subscribe(
                    key = SessionReplayPrivacyConfig,
                    deliveryMode = RemoteConfigClient.DeliveryMode.WaitForRemote(timeoutMs = 60_000L),
                ) { config, source, timestamp ->
                    results.add(Triple(config, source, timestamp))
                }

                // Run only the currently-pending tasks and a tiny virtual-time
                // nudge — nowhere near the 60_000ms timeout.
                testDispatcher.scheduler.runCurrent()
                testDispatcher.scheduler.advanceTimeBy(10L)
                testDispatcher.scheduler.runCurrent()

                assertEquals(
                    1,
                    results.size,
                    "Expected one delivery from empty-config path, got: $results",
                )
                val (config, source, _) = results.single()
                assertEquals(Source.REMOTE, source, "Empty-config delivery should report Source.REMOTE")
                assertTrue(
                    config.isEmpty(),
                    "Expected empty config, got: $config",
                )
            }

        @Test
        fun `WaitForRemote does not interfere with All subscribers on the absent key`() =
            runTest {
                // All subscribers on a key absent from the response now receive a
                // REMOTE notification with emptyMap() (platform-aligned behavior).
                val mockHttpClient = mockk<HttpClient>()
                every { mockHttpClient.request(any()) } returns
                    HttpClient.Response(
                        statusCode = 200,
                        body = DEFAULT_API_REMOTE_CONFIG_JSON.trimIndent(),
                        headers = emptyMap(),
                        statusMessage = "OK",
                    )
                val client =
                    RemoteConfigClientImpl(
                        apiKey = "test-key",
                        serverZone = ServerZone.US,
                        coroutineScope = testScope,
                        networkIODispatcher = testDispatcher,
                        storageIODispatcher = testDispatcher,
                        storage = storage,
                        httpClient = mockHttpClient,
                        logger = silentLogger,
                    )

                val results = mutableListOf<Triple<ConfigMap, Source, Long>>()
                client.subscribe(Diagnostics) { config, source, timestamp ->
                    results.add(Triple(config, source, timestamp))
                }

                testDispatcher.scheduler.advanceUntilIdle()

                // No cache on DIAGNOSTICS. REMOTE fires with empty config (key absent).
                assertEquals(1, results.size, "All subscriber on absent key should receive REMOTE, got: $results")
                val (config, source, _) = results.single()
                assertEquals(Source.REMOTE, source)
                assertTrue(config.isEmpty(), "Absent key should deliver empty config")
            }

        @Test
        fun `WaitForRemote serializes callback delivery to prevent concurrent invocations`() {
            // Regression: the timeout fallback and the remote fetch can invoke
            // the callback concurrently from different dispatchers. This test
            // blocks inside the fallback callback and verifies that the remote
            // delivery waits until the fallback returns — no concurrent calls.
            val networkExecutor = java.util.concurrent.Executors.newSingleThreadExecutor()
            val storageExecutor = java.util.concurrent.Executors.newSingleThreadExecutor()
            val networkDispatcher = networkExecutor.asCoroutineDispatcher()
            val storageDispatcher = storageExecutor.asCoroutineDispatcher()
            val realScope =
                CoroutineScope(
                    kotlinx.coroutines.SupervisorJob() +
                        kotlinx.coroutines.Dispatchers.Default,
                )
            try {
                val mockHttpClient = mockk<HttpClient>()
                // HTTP takes ~150ms — well past the 30ms WaitForRemote timeout —
                // so the timeout fallback fires first.
                every { mockHttpClient.request(any()) } answers {
                    Thread.sleep(150)
                    HttpClient.Response(
                        statusCode = 200,
                        body = DEFAULT_API_REMOTE_CONFIG_JSON.trimIndent(),
                        headers = emptyMap(),
                        statusMessage = "OK",
                    )
                }

                val freshStorage = InMemoryStorage()
                kotlinx.coroutines.runBlocking {
                    freshStorage.write(
                        Storage.Constants.REMOTE_CONFIG,
                        """
                        {
                            "sessionReplay": {
                                "sr_android_privacy_config": {
                                    "defaultMaskLevel": "STALE"
                                },
                                "sr_android_sampling_config": {
                                    "sample_rate": 0.0,
                                    "capture_enabled": false
                                }
                            }
                        }
                        """.trimIndent(),
                    )
                    freshStorage.write(Storage.Constants.REMOTE_CONFIG_TIMESTAMP, "1")
                }

                val client =
                    RemoteConfigClientImpl(
                        apiKey = "race-key",
                        serverZone = ServerZone.US,
                        coroutineScope = realScope,
                        networkIODispatcher = networkDispatcher,
                        storageIODispatcher = storageDispatcher,
                        storage = freshStorage,
                        httpClient = mockHttpClient,
                        logger = silentLogger,
                    )

                // Track concurrency: increment on entry, decrement on exit.
                // Peak must never exceed 1.
                val concurrentCount = java.util.concurrent.atomic.AtomicInteger(0)
                val peakConcurrent = java.util.concurrent.atomic.AtomicInteger(0)
                val allDeliveries = java.util.Collections.synchronizedList(mutableListOf<Pair<ConfigMap, Source>>())
                val allDone = java.util.concurrent.CountDownLatch(2)

                client.subscribe(
                    key = SessionReplayPrivacyConfig,
                    deliveryMode = RemoteConfigClient.DeliveryMode.WaitForRemote(timeoutMs = 30L),
                ) { config, source, _ ->
                    val current = concurrentCount.incrementAndGet()
                    peakConcurrent.updateAndGet { peak -> maxOf(peak, current) }
                    // Simulate slow processing in the fallback callback.
                    Thread.sleep(100)
                    allDeliveries.add(config to source)
                    concurrentCount.decrementAndGet()
                    allDone.countDown()
                }

                val gotBoth = allDone.await(3_000L, java.util.concurrent.TimeUnit.MILLISECONDS)

                assertTrue(
                    gotBoth,
                    "Expected two deliveries (fallback + remote), got: $allDeliveries",
                )
                assertEquals(
                    1,
                    peakConcurrent.get(),
                    "Callback must never be invoked concurrently; peak was ${peakConcurrent.get()}",
                )
                // Final observed config must be the fresh remote, not the stale cache.
                val snapshot = synchronized(allDeliveries) { allDeliveries.toList() }
                val last = snapshot.last()
                assertEquals(
                    Source.REMOTE,
                    last.second,
                    "Last delivery must be the fresh remote: $snapshot",
                )
                assertEquals(
                    "medium",
                    last.first["defaultMaskLevel"],
                    "Last delivery must carry fresh remote data: $snapshot",
                )
            } finally {
                realScope.cancel()
                networkExecutor.shutdownNow()
                storageExecutor.shutdownNow()
            }
        }

        @Test
        fun `WaitForRemote suppresses timeout fallback when remote arrived during slow callback`() {
            // Race regression: the timeout fallback path must not overwrite a
            // fresh remote delivery that won the gate while the timeout
            // continuation was being scheduled. Uses real dispatchers and a
            // slow HTTP response so the race is observable end-to-end.
            val networkExecutor = java.util.concurrent.Executors.newSingleThreadExecutor()
            val storageExecutor = java.util.concurrent.Executors.newSingleThreadExecutor()
            val networkDispatcher = networkExecutor.asCoroutineDispatcher()
            val storageDispatcher = storageExecutor.asCoroutineDispatcher()
            val realScope =
                CoroutineScope(
                    kotlinx.coroutines.SupervisorJob() +
                        kotlinx.coroutines.Dispatchers.Default,
                )
            try {
                val mockHttpClient = mockk<HttpClient>()
                // HTTP takes ~80ms — well past the 30ms WaitForRemote timeout —
                // but eventually returns fresh config.
                every { mockHttpClient.request(any()) } answers {
                    Thread.sleep(80)
                    HttpClient.Response(
                        statusCode = 200,
                        body = DEFAULT_API_REMOTE_CONFIG_JSON.trimIndent(),
                        headers = emptyMap(),
                        statusMessage = "OK",
                    )
                }

                val freshStorage = InMemoryStorage()
                // Cache contains a marker that differs from the fresh remote so
                // we can tell which delivery the subscriber observed last.
                kotlinx.coroutines.runBlocking {
                    freshStorage.write(
                        Storage.Constants.REMOTE_CONFIG,
                        """
                        {
                            "sessionReplay": {
                                "sr_android_privacy_config": {
                                    "defaultMaskLevel": "STALE"
                                },
                                "sr_android_sampling_config": {
                                    "sample_rate": 0.0,
                                    "capture_enabled": false
                                }
                            }
                        }
                        """.trimIndent(),
                    )
                    freshStorage.write(Storage.Constants.REMOTE_CONFIG_TIMESTAMP, "1")
                }

                val client =
                    RemoteConfigClientImpl(
                        apiKey = "race-key",
                        serverZone = ServerZone.US,
                        coroutineScope = realScope,
                        networkIODispatcher = networkDispatcher,
                        storageIODispatcher = storageDispatcher,
                        storage = freshStorage,
                        httpClient = mockHttpClient,
                        logger = silentLogger,
                    )

                val results = java.util.Collections.synchronizedList(mutableListOf<Pair<ConfigMap, Source>>())
                val secondDelivery = java.util.concurrent.CountDownLatch(2)

                client.subscribe(
                    key = SessionReplayPrivacyConfig,
                    deliveryMode = RemoteConfigClient.DeliveryMode.WaitForRemote(timeoutMs = 30L),
                ) { config, source, _ ->
                    results.add(config to source)
                    secondDelivery.countDown()
                }

                // Wait until the fetch has had time to land and the subscriber
                // has received both the timeout fallback and the fresh remote.
                // If our fix is wrong, the fresh remote is silently dropped
                // and we never see a REMOTE delivery.
                val gotBoth = secondDelivery.await(2_000L, java.util.concurrent.TimeUnit.MILLISECONDS)

                assertTrue(
                    gotBoth,
                    "Expected timeout fallback + fresh remote, got: $results",
                )
                // Snapshot deliveries (synchronizedList iteration must hold the lock).
                val snapshot = synchronized(results) { results.toList() }
                assertTrue(
                    snapshot.any { it.second == Source.REMOTE && it.first["defaultMaskLevel"] == "medium" },
                    "Expected fresh remote delivery (defaultMaskLevel=medium), got: $snapshot",
                )
                // The fallback must not have overwritten the fresh remote in the
                // delivery order: the last delivery should be the fresh remote.
                val last = snapshot.last()
                assertEquals(
                    Source.REMOTE,
                    last.second,
                    "Last delivery must be the fresh remote, not a stale fallback: $snapshot",
                )
            } finally {
                realScope.cancel()
                networkExecutor.shutdownNow()
                storageExecutor.shutdownNow()
            }
        }
    }

    // endregion DeliveryMode

    // region Dot-Path and Key Alignment

    @Test
    fun `subscribe with top-level key returns entire subtree`() =
        runTest {
            val client = createClient(emptyApiConfig = false)
            storage.write(Storage.Constants.REMOTE_CONFIG_TIMESTAMP, "0")

            var receivedConfig: ConfigMap? = null
            client.subscribe(Key.Custom("sessionReplay")) { config, _, _ ->
                receivedConfig = config
            }
            testDispatcher.scheduler.advanceUntilIdle()

            // "sessionReplay" should return the entire subtree
            val config = checkNotNull(receivedConfig) { "Subscriber should receive subtree" }

            @Suppress("UNCHECKED_CAST")
            val privacyConfig = config["sr_android_privacy_config"] as? Map<String, Any>
            assertEquals("medium", privacyConfig?.get("defaultMaskLevel"))
            @Suppress("UNCHECKED_CAST")
            val samplingConfig = config["sr_android_sampling_config"] as? Map<String, Any>
            assertEquals(true, samplingConfig?.get("capture_enabled"))
        }

    @Test
    fun `subscribe with Custom key resolves dot-path correctly`() =
        runTest {
            val client = createClient(emptyApiConfig = false)
            storage.write(Storage.Constants.REMOTE_CONFIG_TIMESTAMP, "0")

            var receivedConfig: ConfigMap? = null
            client.subscribe(Key.Custom("sessionReplay.sr_android_privacy_config")) { config, _, _ ->
                receivedConfig = config
            }
            testDispatcher.scheduler.advanceUntilIdle()

            val config = checkNotNull(receivedConfig)
            assertEquals("medium", config["defaultMaskLevel"])
        }

    @Test
    fun `Custom key works with cached data`() =
        runTest {
            val client = createClient()

            storage.write(
                Storage.Constants.REMOTE_CONFIG,
                DEFAULT_STORED_REMOTE_CONFIG_JSON.trimIndent(),
            )
            storage.write(Storage.Constants.REMOTE_CONFIG_TIMESTAMP, "1234567890")

            val results = mutableListOf<Pair<ConfigMap, Source>>()
            client.subscribe(Key.Custom("sessionReplay.sr_android_sampling_config")) { config, source, _ ->
                results.add(config to source)
            }
            testDispatcher.scheduler.advanceUntilIdle()

            // First delivery is CACHE with specific config
            assertTrue(results.isNotEmpty())
            val (cacheConfig, cacheSource) = results.first { it.second == Source.CACHE }
            assertEquals(Source.CACHE, cacheSource)
            assertEquals(1.0, cacheConfig["sample_rate"])
            assertEquals(true, cacheConfig["capture_enabled"])
        }

    @Test
    fun `old pre-flattened cache format is migrated and delivered`() =
        runTest {
            val client = createClient(useVerifiableLogger = true)

            // Old format: keys are "topLevel.nested" (flat)
            storage.write(
                Storage.Constants.REMOTE_CONFIG,
                """
                {
                    "sessionReplay.sr_android_privacy_config": {
                        "defaultMaskLevel": "medium"
                    },
                    "sessionReplay.sr_android_sampling_config": {
                        "sample_rate": 1.0,
                        "capture_enabled": true
                    }
                }
                """.trimIndent(),
            )
            storage.write(Storage.Constants.REMOTE_CONFIG_TIMESTAMP, "1234567890")

            val results = mutableListOf<Pair<ConfigMap, Source>>()
            client.subscribe(SessionReplayPrivacyConfig) { config, source, _ ->
                results.add(config to source)
            }
            testDispatcher.scheduler.advanceUntilIdle()

            // First delivery is migrated CACHE
            assertTrue(results.isNotEmpty()) { "Migrated config should be delivered" }
            val (cacheConfig, cacheSource) = results.first { it.second == Source.CACHE }
            assertEquals(Source.CACHE, cacheSource)
            assertEquals("medium", cacheConfig["defaultMaskLevel"])
            verify { logger.debug(match<String> { it.contains("old pre-flattened cache format") }) }
        }

    @Test
    fun `dot-path to non-existent path returns empty config`() =
        runTest {
            val client = createClient()

            storage.write(
                Storage.Constants.REMOTE_CONFIG,
                DEFAULT_STORED_REMOTE_CONFIG_JSON.trimIndent(),
            )
            storage.write(Storage.Constants.REMOTE_CONFIG_TIMESTAMP, "1234567890")

            var receivedConfig: ConfigMap? = null
            client.subscribe(Key.Custom("nonExistent.path")) { config, _, _ ->
                receivedConfig = config
            }
            testDispatcher.scheduler.advanceUntilIdle()

            // Should receive empty config (dot-path doesn't resolve)
            val config = checkNotNull(receivedConfig)
            assertTrue(config.isEmpty())
        }

    @Test
    fun `Key sealed class instances have correct values`() {
        assertEquals("analyticsSDK.androidSDK", Key.AnalyticsSdk.value)
        assertEquals("diagnostics.androidSDK", Key.Diagnostics.value)
        assertEquals("sessionReplay.sr_android_privacy_config", Key.SessionReplayPrivacyConfig.value)
        assertEquals("sessionReplay.sr_android_sampling_config", Key.SessionReplaySamplingConfig.value)
        assertEquals("experiment.androidSDK", Key.Custom("experiment.androidSDK").value)
    }

    // endregion Dot-Path and Key Alignment

    // region Migration and Dynamic Fetch Keys

    @Test
    fun `old-format cache migrated and delivered when offline with WaitForRemote`() =
        runTest {
            // Simulates an upgrade scenario: old cache has flat dotted keys,
            // fetch fails (offline). Subscribers should still receive the
            // migrated cached config instead of empty.
            val mockHttpClient = mockk<HttpClient>()
            every { mockHttpClient.request(any()) } returns
                HttpClient.Response(
                    statusCode = 500,
                    body = "",
                    headers = emptyMap(),
                    statusMessage = "Internal Server Error",
                )
            val client =
                RemoteConfigClientImpl(
                    apiKey = "test-key",
                    serverZone = ServerZone.US,
                    coroutineScope = testScope,
                    networkIODispatcher = testDispatcher,
                    storageIODispatcher = testDispatcher,
                    storage = storage,
                    httpClient = mockHttpClient,
                    logger = silentLogger,
                )

            // Old flat format
            storage.write(
                Storage.Constants.REMOTE_CONFIG,
                """
                {
                    "sessionReplay.sr_android_privacy_config": {
                        "defaultMaskLevel": "strict"
                    },
                    "sessionReplay.sr_android_sampling_config": {
                        "sample_rate": 0.5,
                        "capture_enabled": true
                    },
                    "diagnostics.androidSDK": {
                        "enabled": true
                    }
                }
                """.trimIndent(),
            )
            storage.write(Storage.Constants.REMOTE_CONFIG_TIMESTAMP, "1234567890")

            val privacyResults = mutableListOf<Triple<ConfigMap, Source, Long>>()
            val diagnosticsResults = mutableListOf<Triple<ConfigMap, Source, Long>>()

            client.subscribe(
                key = SessionReplayPrivacyConfig,
                deliveryMode = RemoteConfigClient.DeliveryMode.WaitForRemote(timeoutMs = 100L),
            ) { config, source, timestamp ->
                privacyResults.add(Triple(config, source, timestamp))
            }

            client.subscribe(
                key = Diagnostics,
                deliveryMode = RemoteConfigClient.DeliveryMode.All,
            ) { config, source, timestamp ->
                diagnosticsResults.add(Triple(config, source, timestamp))
            }

            testDispatcher.scheduler.advanceUntilIdle()

            // Privacy: WaitForRemote should get migrated cache (fetch failed, timeout fires).
            assertTrue(
                privacyResults.isNotEmpty(),
                "WaitForRemote subscriber should receive migrated cache on offline upgrade",
            )
            val (privacyConfig, privacySource, _) = privacyResults.first()
            assertEquals("strict", privacyConfig["defaultMaskLevel"])
            assertEquals(Source.CACHE, privacySource)

            // Diagnostics: All should get migrated cache immediately.
            assertTrue(
                diagnosticsResults.isNotEmpty(),
                "All subscriber should receive migrated cache on offline upgrade",
            )
            val (diagConfig, diagSource, _) = diagnosticsResults.first()
            assertEquals(true, diagConfig["enabled"])
            assertEquals(Source.CACHE, diagSource)

            // Verify the migrated blob was persisted back to storage.
            val storedJson = storage.read(Storage.Constants.REMOTE_CONFIG)
            val storedBlob = org.json.JSONObject(checkNotNull(storedJson))
            assertTrue(
                storedBlob.has("sessionReplay"),
                "Migrated storage should have nested 'sessionReplay' key",
            )
            assertFalse(
                storedBlob.keys().asSequence().any { "." in it },
                "Migrated storage should have no dotted top-level keys",
            )
        }

    @Test
    fun `Custom key root is included in fetch URL`() =
        runTest {
            val requestSlot = slot<HttpClient.Request>()
            val mockHttpClient = mockk<HttpClient>()
            every { mockHttpClient.request(capture(requestSlot)) } returns
                HttpClient.Response(
                    statusCode = 200,
                    body = """{"configs": {}}""",
                    headers = emptyMap(),
                    statusMessage = "OK",
                )

            val client =
                RemoteConfigClientImpl(
                    apiKey = "test-key",
                    serverZone = ServerZone.US,
                    coroutineScope = testScope,
                    networkIODispatcher = testDispatcher,
                    storageIODispatcher = testDispatcher,
                    storage = storage,
                    httpClient = mockHttpClient,
                    logger = silentLogger,
                )

            // Subscribe with a Custom key whose root is NOT in the hardcoded list.
            client.subscribe(Key.Custom("newBlade.config")) { _, _, _ -> }
            testDispatcher.scheduler.advanceUntilIdle()

            assertTrue(requestSlot.isCaptured, "Should have made an HTTP request")
            val url = requestSlot.captured.url
            assertTrue(
                url.contains("config_keys=newBlade"),
                "Fetch URL should include the custom root 'newBlade'. Got: $url",
            )
            // Hardcoded keys should still be present.
            assertTrue(
                url.contains("config_keys=analyticsSDK"),
                "Fetch URL should still include hardcoded 'analyticsSDK'. Got: $url",
            )
            assertTrue(
                url.contains("config_keys=sessionReplay"),
                "Fetch URL should still include hardcoded 'sessionReplay'. Got: $url",
            )
        }

    @Test
    fun `Custom key with no dot uses entire value as root in fetch URL`() =
        runTest {
            val requestSlot = slot<HttpClient.Request>()
            val mockHttpClient = mockk<HttpClient>()
            every { mockHttpClient.request(capture(requestSlot)) } returns
                HttpClient.Response(
                    statusCode = 200,
                    body = """{"configs": {}}""",
                    headers = emptyMap(),
                    statusMessage = "OK",
                )

            val client =
                RemoteConfigClientImpl(
                    apiKey = "test-key",
                    serverZone = ServerZone.US,
                    coroutineScope = testScope,
                    networkIODispatcher = testDispatcher,
                    storageIODispatcher = testDispatcher,
                    storage = storage,
                    httpClient = mockHttpClient,
                    logger = silentLogger,
                )

            client.subscribe(Key.Custom("experiment")) { _, _, _ -> }
            testDispatcher.scheduler.advanceUntilIdle()

            assertTrue(requestSlot.isCaptured)
            val url = requestSlot.captured.url
            assertTrue(
                url.contains("config_keys=experiment"),
                "Fetch URL should include 'experiment' when Custom key has no dot. Got: $url",
            )
        }

    @Test
    fun `Custom key with hardcoded root does not duplicate fetch keys`() =
        runTest {
            val requestSlot = slot<HttpClient.Request>()
            val mockHttpClient = mockk<HttpClient>()
            every { mockHttpClient.request(capture(requestSlot)) } returns
                HttpClient.Response(
                    statusCode = 200,
                    body = """{"configs": {}}""",
                    headers = emptyMap(),
                    statusMessage = "OK",
                )

            val client =
                RemoteConfigClientImpl(
                    apiKey = "test-key",
                    serverZone = ServerZone.US,
                    coroutineScope = testScope,
                    networkIODispatcher = testDispatcher,
                    storageIODispatcher = testDispatcher,
                    storage = storage,
                    httpClient = mockHttpClient,
                    logger = silentLogger,
                )

            // "sessionReplay" is already hardcoded, so subscribing with it should not duplicate.
            client.subscribe(Key.Custom("sessionReplay.customSubConfig")) { _, _, _ -> }
            testDispatcher.scheduler.advanceUntilIdle()

            assertTrue(requestSlot.isCaptured)
            val url = requestSlot.captured.url
            val occurrences = "config_keys=sessionReplay".toRegex().findAll(url).count()
            assertEquals(
                1,
                occurrences,
                "sessionReplay should appear exactly once in fetch URL, not duplicated. Got: $url",
            )
        }

    @Test
    fun `Custom key bypasses rate limit when root is new`() =
        runTest {
            val requestSlot = slot<HttpClient.Request>()
            val mockHttpClient = mockk<HttpClient>()
            every { mockHttpClient.request(capture(requestSlot)) } returns
                HttpClient.Response(
                    statusCode = 200,
                    body = """{"configs": {}}""",
                    headers = emptyMap(),
                    statusMessage = "OK",
                )

            val client =
                RemoteConfigClientImpl(
                    apiKey = "test-key",
                    serverZone = ServerZone.US,
                    coroutineScope = testScope,
                    networkIODispatcher = testDispatcher,
                    storageIODispatcher = testDispatcher,
                    storage = storage,
                    httpClient = mockHttpClient,
                    logger = silentLogger,
                )

            // Seed a recent timestamp so normal fetches would be rate-limited.
            storage.write(Storage.Constants.REMOTE_CONFIG_TIMESTAMP, System.currentTimeMillis().toString())

            // Subscribe with a known key — should be rate-limited, no fetch.
            client.subscribe(AnalyticsSdk) { _, _, _ -> }
            testDispatcher.scheduler.advanceUntilIdle()
            assertFalse(requestSlot.isCaptured, "Known key should be rate-limited")

            // Subscribe with a Custom key whose root is new — should bypass rate limit.
            client.subscribe(Key.Custom("experiment.androidSDK")) { _, _, _ -> }
            testDispatcher.scheduler.advanceUntilIdle()
            assertTrue(requestSlot.isCaptured, "Custom key with new root should bypass rate limit")
            val url = requestSlot.captured.url
            assertTrue(url.contains("config_keys=experiment"), "URL should include new root. Got: $url")
        }

    // endregion Migration and Dynamic Fetch Keys

    companion object {
        private const val DEFAULT_API_REMOTE_CONFIG_JSON =
            """
            {  
                "configs": {  
                    "sessionReplay": {  
                        "sr_android_privacy_config": {  
                            "maskSelector": [],  
                            "blockSelector": [],  
                            "unmaskSelector": [],  
                            "defaultMaskLevel": "medium"  
                        },  
                        "sr_android_sampling_config": {  
                            "sample_rate": 1.00,  
                            "capture_enabled": true  
                        }  
                    }
                }  
            }
            """
        private const val DEFAULT_STORED_REMOTE_CONFIG_JSON =
            """
            {
                "sessionReplay": {
                    "sr_android_privacy_config": {
                        "defaultMaskLevel": "medium"
                    },
                    "sr_android_sampling_config": {
                        "sample_rate": 1.0,
                        "capture_enabled": true
                    }
                }
            }
            """
    }
}
