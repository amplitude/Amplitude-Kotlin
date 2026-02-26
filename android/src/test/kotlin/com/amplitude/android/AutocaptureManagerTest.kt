package com.amplitude.android

import com.amplitude.common.Logger
import com.amplitude.core.RestrictedAmplitudeFeature
import com.amplitude.core.remoteconfig.ConfigMap
import com.amplitude.core.remoteconfig.RemoteConfigClient
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AutocaptureManagerTest {
    private val logger = mockk<Logger>(relaxed = true)

    @Test
    fun `initial state matches configuration`() {
        val manager =
            createManager(
                autocapture =
                    setOf(
                        AutocaptureOption.SESSIONS,
                        AutocaptureOption.APP_LIFECYCLES,
                        AutocaptureOption.SCREEN_VIEWS,
                    ),
            )

        val state = manager.state.value
        assertTrue(state.sessions)
        assertTrue(state.appLifecycles)
        assertTrue(state.screenViews)
        assertFalse(state.deepLinks)
        assertTrue(state.interactions.isEmpty())
    }

    @Test
    fun `initial state with all options and interactions`() {
        val manager =
            createManager(
                autocapture = AutocaptureOption.ALL,
                interactionsOptions =
                    InteractionsOptions(
                        rageClick = RageClickOptions(enabled = true),
                        deadClick = DeadClickOptions(enabled = false),
                    ),
            )

        val state = manager.state.value
        assertTrue(state.sessions)
        assertTrue(state.appLifecycles)
        assertTrue(state.screenViews)
        assertTrue(state.deepLinks)
        assertTrue(InteractionType.ElementInteraction in state.interactions)
        assertTrue(InteractionType.RageClick in state.interactions)
        assertFalse(InteractionType.DeadClick in state.interactions)
    }

    @Test
    fun `remote config updates sessions`() {
        val remoteConfig = TestRemoteConfigClient()
        val manager =
            createManager(
                autocapture = setOf(AutocaptureOption.SESSIONS),
                remoteConfigClient = remoteConfig,
            )

        assertTrue(manager.state.value.sessions)

        remoteConfig.emit(mapOf("sessions" to false))
        assertFalse(manager.state.value.sessions)

        remoteConfig.emit(mapOf("sessions" to true))
        assertTrue(manager.state.value.sessions)
    }

    @Test
    fun `remote config updates appLifecycles`() {
        val remoteConfig = TestRemoteConfigClient()
        val manager =
            createManager(
                autocapture = setOf(AutocaptureOption.APP_LIFECYCLES),
                remoteConfigClient = remoteConfig,
            )

        assertTrue(manager.state.value.appLifecycles)

        remoteConfig.emit(mapOf("appLifecycles" to false))
        assertFalse(manager.state.value.appLifecycles)
    }

    @Test
    fun `remote config updates screenViews via pageViews key`() {
        val remoteConfig = TestRemoteConfigClient()
        val manager =
            createManager(
                autocapture = emptySet(),
                remoteConfigClient = remoteConfig,
            )

        assertFalse(manager.state.value.screenViews)

        remoteConfig.emit(mapOf("pageViews" to true))
        assertTrue(manager.state.value.screenViews)
    }

    @Test
    fun `remote config updates elementInteractions`() {
        val remoteConfig = TestRemoteConfigClient()
        val manager =
            createManager(
                autocapture = emptySet(),
                remoteConfigClient = remoteConfig,
            )

        assertTrue(manager.state.value.interactions.isEmpty())

        remoteConfig.emit(mapOf("elementInteractions" to true))
        assertTrue(InteractionType.ElementInteraction in manager.state.value.interactions)

        remoteConfig.emit(mapOf("elementInteractions" to false))
        assertFalse(InteractionType.ElementInteraction in manager.state.value.interactions)
    }

    @Test
    fun `remote config updates frustrationInteractions with nested structure`() {
        val remoteConfig = TestRemoteConfigClient()
        val manager =
            createManager(
                autocapture = emptySet(),
                remoteConfigClient = remoteConfig,
            )

        remoteConfig.emit(
            mapOf(
                "frustrationInteractions" to
                    mapOf(
                        "enabled" to true,
                        "rageClick" to mapOf("enabled" to true),
                        "deadClick" to mapOf("enabled" to false),
                    ),
            ),
        )

        assertTrue(InteractionType.RageClick in manager.state.value.interactions)
        assertFalse(InteractionType.DeadClick in manager.state.value.interactions)
    }

    @Test
    fun `remote config frustrationInteractions disabled removes all frustration types`() {
        val remoteConfig = TestRemoteConfigClient()
        val manager =
            createManager(
                autocapture = setOf(AutocaptureOption.FRUSTRATION_INTERACTIONS),
                interactionsOptions = InteractionsOptions(),
                remoteConfigClient = remoteConfig,
            )

        assertTrue(InteractionType.RageClick in manager.state.value.interactions)
        assertTrue(InteractionType.DeadClick in manager.state.value.interactions)

        remoteConfig.emit(
            mapOf(
                "frustrationInteractions" to mapOf("enabled" to false),
            ),
        )

        assertFalse(InteractionType.RageClick in manager.state.value.interactions)
        assertFalse(InteractionType.DeadClick in manager.state.value.interactions)
    }

    @Test
    fun `unspecified keys preserve existing state`() {
        val remoteConfig = TestRemoteConfigClient()
        val manager =
            createManager(
                autocapture =
                    setOf(
                        AutocaptureOption.SESSIONS,
                        AutocaptureOption.APP_LIFECYCLES,
                        AutocaptureOption.SCREEN_VIEWS,
                        AutocaptureOption.DEEP_LINKS,
                    ),
                remoteConfigClient = remoteConfig,
            )

        // Only update sessions, everything else should be preserved
        remoteConfig.emit(mapOf("sessions" to false))

        assertFalse(manager.state.value.sessions)
        assertTrue(manager.state.value.appLifecycles)
        assertTrue(manager.state.value.screenViews)
        assertTrue(manager.state.value.deepLinks)
    }

    @Test
    fun `remote config updates deepLinks`() {
        val remoteConfig = TestRemoteConfigClient()
        val manager =
            createManager(
                autocapture = setOf(AutocaptureOption.DEEP_LINKS),
                remoteConfigClient = remoteConfig,
            )

        assertTrue(manager.state.value.deepLinks)

        remoteConfig.emit(mapOf("deepLinks" to false))
        assertFalse(manager.state.value.deepLinks)

        remoteConfig.emit(mapOf("deepLinks" to true))
        assertTrue(manager.state.value.deepLinks)
    }

    @Test
    fun `null remoteConfigClient does not subscribe`() {
        val manager =
            createManager(
                autocapture = setOf(AutocaptureOption.SESSIONS),
                remoteConfigClient = null,
            )

        assertTrue(manager.state.value.sessions)
    }

    @Test
    fun `state flow emits updates on remote config change`() {
        val remoteConfig = TestRemoteConfigClient()
        val manager =
            createManager(
                autocapture = setOf(AutocaptureOption.SESSIONS),
                remoteConfigClient = remoteConfig,
            )

        assertTrue(manager.state.value.sessions)

        remoteConfig.emit(mapOf("sessions" to false))

        assertFalse(manager.state.value.sessions)
    }

    @Test
    fun `toString reflects current state`() {
        val state =
            AutocaptureState(
                sessions = true,
                appLifecycles = true,
                screenViews = false,
                deepLinks = false,
                interactions = listOf(InteractionType.ElementInteraction),
            )

        assertEquals("sessions,appLifecycles,elementInteractions", state.toString())
    }

    @Test
    fun `toString with no options returns none`() {
        val state = AutocaptureState()
        assertEquals("none", state.toString())
    }

    @Test
    fun `toString with frustration interactions`() {
        val state =
            AutocaptureState(
                interactions = listOf(InteractionType.RageClick, InteractionType.DeadClick),
            )

        assertEquals("frustrationInteractions", state.toString())
    }

    @OptIn(RestrictedAmplitudeFeature::class)
    private fun createManager(
        autocapture: Set<AutocaptureOption> = setOf(AutocaptureOption.SESSIONS),
        interactionsOptions: InteractionsOptions = InteractionsOptions(),
        remoteConfigClient: RemoteConfigClient? = null,
    ): AutocaptureManager {
        return AutocaptureManager(
            initialAutocapture = autocapture,
            initialInteractionsOptions = interactionsOptions,
            remoteConfigClient = remoteConfigClient,
            logger = logger,
        )
    }

    private class TestRemoteConfigClient : RemoteConfigClient {
        private val callbacks = mutableListOf<RemoteConfigClient.RemoteConfigCallback>()

        override fun subscribe(
            key: RemoteConfigClient.Key,
            callback: RemoteConfigClient.RemoteConfigCallback,
        ) {
            if (key == RemoteConfigClient.Key.ANALYTICS_SDK) {
                callbacks.add(callback)
            }
        }

        override fun updateConfigs() {}

        fun emit(
            config: ConfigMap,
            source: RemoteConfigClient.Source = RemoteConfigClient.Source.REMOTE,
            timestamp: Long = System.currentTimeMillis(),
        ) {
            callbacks.forEach { it.onUpdate(config, source, timestamp) }
        }
    }
}
