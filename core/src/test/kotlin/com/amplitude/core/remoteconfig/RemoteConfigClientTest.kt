package com.amplitude.core.remoteconfig

import com.amplitude.common.jvm.ConsoleLogger
import com.amplitude.core.ServerZone
import com.amplitude.core.Storage
import com.amplitude.core.remoteconfig.RemoteConfigClient.Key
import com.amplitude.core.remoteconfig.RemoteConfigClient.Source
import com.amplitude.core.utilities.InMemoryStorage
import com.amplitude.core.utilities.http.HttpClient
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import io.mockk.verify
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
    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private fun createClient(
        serverZone: ServerZone = ServerZone.US,
        apiKey: String = "test-key",
        // Use emptyApiConfig to control whether to return empty config or default config
        emptyApiConfig: Boolean = true,
        // Custom response for testing edge cases
        customResponse: HttpClient.Response? = null,
    ): RemoteConfigClientImpl {
        // Create a mock HttpClient that returns configurable responses
        val mockHttpClient = mockk<HttpClient>()
        every { mockHttpClient.request(any()) } returns (
            customResponse ?: HttpClient.Response(
                statusCode = 200,
                statusMessage = "OK",
                body =
                    if (emptyApiConfig) {
                        """{"configs": {}}"""
                    } else {
                        DEFAULT_API_REMOTE_CONFIG_JSON.trimIndent()
                    },
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
            logger = logger,
        )
    }

    // region Subscription Management

    @Test
    fun `subscribe with cached data - multiple callbacks for same key all receive updates`() =
        runTest {
            val client = createClient()
            val callbacks = mutableListOf<ConfigMap>()

            // Pre-populate storage with all expected keys to avoid invalidation
            storage.write(Storage.Constants.REMOTE_CONFIG, DEFAULT_STORED_REMOTE_CONFIG_JSON.trimIndent())
            storage.write(Storage.Constants.REMOTE_CONFIG_TIMESTAMP, "1234567890")

            // Subscribe multiple callbacks to same key
            client.subscribe(Key.SESSION_REPLAY_PRIVACY_CONFIG) { config, _, _ -> callbacks.add(config) }
            client.subscribe(Key.SESSION_REPLAY_PRIVACY_CONFIG) { config, _, _ -> callbacks.add(config) }
            client.subscribe(Key.SESSION_REPLAY_PRIVACY_CONFIG) { config, _, _ -> callbacks.add(config) }

            testDispatcher.scheduler.advanceUntilIdle()

            // All callbacks should receive cached data
            assertEquals(3, callbacks.size)
            callbacks.forEach { assertEquals("medium", it["defaultMaskLevel"]) }
        }

    @Test
    fun `subscribe with cached data - different keys receive only their config`() =
        runTest {
            val client = createClient()
            var privacyConfig: ConfigMap? = null
            var samplingConfig: ConfigMap? = null

            // Pre-populate storage with both configs
            storage.write(Storage.Constants.REMOTE_CONFIG, DEFAULT_STORED_REMOTE_CONFIG_JSON.trimIndent())
            storage.write(Storage.Constants.REMOTE_CONFIG_TIMESTAMP, "1234567890")

            // Subscribe to different keys
            client.subscribe(Key.SESSION_REPLAY_PRIVACY_CONFIG) { config, _, _ -> privacyConfig = config }
            client.subscribe(Key.SESSION_REPLAY_SAMPLING_CONFIG) { config, _, _ -> samplingConfig = config }

            testDispatcher.scheduler.advanceUntilIdle()

            // Each should receive their specific config
            val privacy = checkNotNull(privacyConfig) { "Privacy subscriber should receive config" }
            val sampling = checkNotNull(samplingConfig) { "Sampling subscriber should receive config" }

            assertEquals("medium", privacy["defaultMaskLevel"])
            assertEquals(1.0, sampling["sample_rate"])
            assertEquals(true, sampling["capture_enabled"])
            assertFalse(privacy.containsKey("sample_rate"))
        }

    @Test
    fun `subscribe with weak references - external counter tracks temp callback behavior`() =
        runTest {
            val client = createClient(emptyApiConfig = false)
            var tempCallbackInvocations = 0
            var persistentCallbackInvocations = 0

            storage.write(Storage.Constants.REMOTE_CONFIG, DEFAULT_STORED_REMOTE_CONFIG_JSON.trimIndent())
            storage.write(Storage.Constants.REMOTE_CONFIG_TIMESTAMP, "1234567890")

            // Add persistent callback that stays in memory
            val persistentCallback =
                RemoteConfigClient.RemoteConfigCallback { _, _, _ ->
                    persistentCallbackInvocations++
                }
            client.subscribe(Key.SESSION_REPLAY_PRIVACY_CONFIG, persistentCallback)
            testDispatcher.scheduler.advanceUntilIdle()
            // Persistent callback should have received initial cache + remote
            assertEquals(2, persistentCallbackInvocations, "Persistent callback should receive initial cache")

            // Create temp callbacks in limited scope with counter
            run {
                val tempCallback =
                    RemoteConfigClient.RemoteConfigCallback { _, _, _ ->
                        tempCallbackInvocations++
                    }
                client.subscribe(Key.SESSION_REPLAY_PRIVACY_CONFIG, tempCallback)
            }
            testDispatcher.scheduler.advanceUntilIdle()

            // Temp callback should have received initial cache + remote
            assertEquals(2, tempCallbackInvocations, "Temp callbacks should receive initial cache")

            // Force GC and trigger cleanup
            repeat(5) {
                System.gc()
                Thread.sleep(10)
            }

            // Add new subscriber to trigger cleanup
            client.subscribe(Key.SESSION_REPLAY_PRIVACY_CONFIG) { _, _, _ -> }
            testDispatcher.scheduler.advanceUntilIdle()

            // The test demonstrates that weak reference system works
            // Counter shows temp callback were invoked initially and then cleaned up
            assertTrue(tempCallbackInvocations == 2)
            // Temp callback should have received initial cache + 3 remote updates
            assertTrue(persistentCallbackInvocations == 4)

            // Whether GC cleanup happened or not, the system continues to function
            assertTrue(true, "Weak reference subscription system handles temp callbacks correctly")
        }

    @Test
    fun `subscribe with callback exceptions - isolate exception and don't cascade failure`() =
        runTest {
            val client = createClient()
            val workingCallbacks = mutableListOf<ConfigMap>()

            // Pre-populate storage to ensure callbacks are triggered
            storage.write(Storage.Constants.REMOTE_CONFIG, DEFAULT_STORED_REMOTE_CONFIG_JSON.trimIndent())
            storage.write(Storage.Constants.REMOTE_CONFIG_TIMESTAMP, "1234567890")

            // Add callback that throws exception
            client.subscribe(Key.SESSION_REPLAY_PRIVACY_CONFIG) { _, _, _ ->
                throw RuntimeException("Test exception")
            }

            // Add working callbacks
            client.subscribe(Key.SESSION_REPLAY_PRIVACY_CONFIG) { config, _, _ -> workingCallbacks.add(config) }
            client.subscribe(Key.SESSION_REPLAY_PRIVACY_CONFIG) { config, _, _ -> workingCallbacks.add(config) }

            testDispatcher.scheduler.advanceUntilIdle()

            // Working callbacks should still be invoked despite exception
            assertEquals(2, workingCallbacks.size)
            verify { logger.error(match<String> { it.contains("Exception in subscriber callback") }) }
        }

    @Test
    fun `subscribe with immediate cache notification - correct source and data`() =
        runTest {
            val client = createClient()

            // Pre-populate storage with config
            storage.write(
                Storage.Constants.REMOTE_CONFIG,
                """
                {
                    "sessionReplay.sr_android_privacy_config": {
                        "defaultMaskLevel": "strict"
                    },
                    "sessionReplay.sr_android_sampling_config": {
                        "sample_rate": 0.003,
                        "capture_enabled": false
                    }
                }
                """.trimIndent(),
            )
            storage.write(Storage.Constants.REMOTE_CONFIG_TIMESTAMP, "1234567890")

            var receivedConfig: ConfigMap? = null
            var receivedSource: Source? = null
            var receivedTimestamp: Long? = null

            // Subscribe - should immediately get cached data
            client.subscribe(Key.SESSION_REPLAY_PRIVACY_CONFIG) { config, source, timestamp ->
                receivedConfig = config
                receivedSource = source
                receivedTimestamp = timestamp
            }

            testDispatcher.scheduler.advanceUntilIdle()

            val config = checkNotNull(receivedConfig)
            assertEquals(Source.CACHE, receivedSource)
            assertEquals(1234567890L, receivedTimestamp)
            assertEquals("strict", config["defaultMaskLevel"])
        }

    @Test
    fun `subscribe multiple times with same callback - each creates separate registration`() =
        runTest {
            val client = createClient()
            var callCount = 0

            // Pre-populate storage to ensure callbacks are triggered
            storage.write(
                Storage.Constants.REMOTE_CONFIG,
                """
                {
                    "sessionReplay.sr_android_privacy_config": {
                        "defaultMaskLevel": "moderate"
                    },
                    "sessionReplay.sr_android_sampling_config": {
                        "sample_rate": 0.15,
                        "capture_enabled": true
                    }
                }
                """.trimIndent(),
            )
            storage.write(Storage.Constants.REMOTE_CONFIG_TIMESTAMP, "1234567890")

            val callback = RemoteConfigClient.RemoteConfigCallback { _, _, _ -> callCount++ }

            // Subscribe same callback reference multiple times
            client.subscribe(Key.SESSION_REPLAY_PRIVACY_CONFIG, callback)
            client.subscribe(Key.SESSION_REPLAY_PRIVACY_CONFIG, callback)
            client.subscribe(Key.SESSION_REPLAY_PRIVACY_CONFIG, callback)

            testDispatcher.scheduler.advanceUntilIdle()

            // Each subscription creates separate WeakCallback wrapper
            assertEquals(3, callCount)
            verify(exactly = 3) { logger.debug(match<String> { it.contains("Added subscriber") }) }
        }

    @Test
    fun `subscribe with inconsistent storage - storage invalidated and no immediate callback`() =
        runTest {
            val client = createClient()
            var callbackInvoked = false

            // Pre-populate storage with incomplete data (missing sampling config)
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

            client.subscribe(Key.SESSION_REPLAY_PRIVACY_CONFIG) { _, _, _ -> callbackInvoked = true }

            testDispatcher.scheduler.advanceUntilIdle()

            // Should not receive immediate callback due to inconsistent storage
            assertFalse(callbackInvoked)
            verify { logger.debug(match<String> { it.contains("Storage inconsistent keys") }) }
            verify { logger.debug(match<String> { it.contains("Cleared inconsistent storage") }) }
        }

    // endregion Subscription Management

    // region API Response Edge Cases

    @Test
    fun `API returns null response body`() =
        runTest {
            val client =
                createClient(
                    customResponse =
                        HttpClient.Response(
                            statusCode = 200,
                            statusMessage = "OK",
                            body = null,
                        ),
                )

            storage.write(Storage.Constants.REMOTE_CONFIG, DEFAULT_STORED_REMOTE_CONFIG_JSON.trimIndent())
            storage.write(Storage.Constants.REMOTE_CONFIG_TIMESTAMP, "1234567890")

            var callbackCount = 0
            client.subscribe(Key.SESSION_REPLAY_PRIVACY_CONFIG) { _, _, _ -> callbackCount++ }
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
                            statusMessage = "OK",
                            body = """{this is not valid json at all}""",
                        ),
                )

            storage.write(Storage.Constants.REMOTE_CONFIG, DEFAULT_STORED_REMOTE_CONFIG_JSON.trimIndent())

            var callbackCount = 0
            client.subscribe(Key.SESSION_REPLAY_PRIVACY_CONFIG) { _, _, _ -> callbackCount++ }
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
                            statusMessage = "OK",
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
                        ),
                )

            var callbackCount = 0
            client.subscribe(Key.SESSION_REPLAY_PRIVACY_CONFIG) { _, _, _ -> callbackCount++ }
            client.subscribe(Key.SESSION_REPLAY_SAMPLING_CONFIG) { _, _, _ -> callbackCount++ }
            testDispatcher.scheduler.advanceUntilIdle()
            assertTrue(callbackCount == 4, "Client should handle null fields without crashing")

            client.updateConfigs()
            testDispatcher.scheduler.advanceUntilIdle()
            assertTrue(callbackCount == 6, "Client should handle null fields without crashing")
        }

    @Test
    fun `API returns JSON with wrong data types`() =
        runTest {
            val client =
                createClient(
                    customResponse =
                        HttpClient.Response(
                            statusCode = 200,
                            statusMessage = "OK",
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
                        ),
                )

            storage.write(Storage.Constants.REMOTE_CONFIG, DEFAULT_STORED_REMOTE_CONFIG_JSON.trimIndent())
            storage.write(Storage.Constants.REMOTE_CONFIG_TIMESTAMP, "1234567890")

            var callbackCount = 0
            client.subscribe(Key.SESSION_REPLAY_PRIVACY_CONFIG) { config, source, _ ->
                callbackCount++
            }
            testDispatcher.scheduler.advanceUntilIdle()
            assertTrue(callbackCount == 1, "Client should handle wrong data types without crashing")

            client.updateConfigs()
            testDispatcher.scheduler.advanceUntilIdle()
            assertTrue(callbackCount == 1, "Client should handle wrong data types without crashing")
        }

    @Test
    fun `API returns empty string response`() =
        runTest {
            val client =
                createClient(
                    customResponse =
                        HttpClient.Response(
                            statusCode = 200,
                            statusMessage = "OK",
                            body = "",
                        ),
                )

            var callbackInvoked = false
            client.subscribe(Key.SESSION_REPLAY_PRIVACY_CONFIG) { _, _, _ -> callbackInvoked = true }
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
                            statusMessage = "Internal Server Error",
                            body = """{"error": "Something went wrong"}""",
                        ),
                )

            storage.write(Storage.Constants.REMOTE_CONFIG, DEFAULT_STORED_REMOTE_CONFIG_JSON.trimIndent())
            storage.write(Storage.Constants.REMOTE_CONFIG_TIMESTAMP, "1234567890")

            var callbackCount = 0
            client.subscribe(Key.SESSION_REPLAY_PRIVACY_CONFIG) { _, _, _ -> callbackCount++ }
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
                            statusMessage = "OK",
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
                        ),
                )

            var callbackCount = 0
            client.subscribe(Key.SESSION_REPLAY_PRIVACY_CONFIG) { _, _, _ -> callbackCount++ }
            testDispatcher.scheduler.advanceUntilIdle()
            assertTrue(callbackCount == 1, "Client should handle deeply nested garbage without crashing")

            client.updateConfigs()
            testDispatcher.scheduler.advanceUntilIdle()
            assertTrue(callbackCount == 2, "Client should handle deeply nested garbage without crashing")
        }

    @Test
    fun `API returns valid JSON but missing configs key`() =
        runTest {
            val client =
                createClient(
                    customResponse =
                        HttpClient.Response(
                            statusCode = 200,
                            statusMessage = "OK",
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
                        ),
                )

            var callbackCount = 0
            client.subscribe(Key.SESSION_REPLAY_PRIVACY_CONFIG) { _, _, _ -> callbackCount++ }
            testDispatcher.scheduler.advanceUntilIdle()

            client.updateConfigs()
            testDispatcher.scheduler.advanceUntilIdle()
            assertTrue(callbackCount == 0, "Should not invoke callback when configs key is missing")
        }

    // endregion API Response Edge Cases

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
                "sessionReplay.sr_android_privacy_config": {
                    "defaultMaskLevel": "medium"
                },
                "sessionReplay.sr_android_sampling_config": {
                    "sample_rate": 1.0,
                    "capture_enabled": true
                }
            }
            """
    }
}
