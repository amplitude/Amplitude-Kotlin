package com.amplitude.android

import com.amplitude.common.Logger
import com.amplitude.core.RestrictedAmplitudeFeature
import com.amplitude.core.diagnostics.DiagnosticsClient
import com.amplitude.core.remoteconfig.ConfigMap
import com.amplitude.core.remoteconfig.RemoteConfigClient
import com.amplitude.core.remoteconfig.RemoteConfigClient.Key
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Manages dynamic autocapture state, optionally subscribing to remote config
 * updates to override local configuration.
 *
 * Thread-safe: [state] is backed by a [StateFlow] whose value is an immutable
 * [AutocaptureState]. All readers see a consistent snapshot. Updates replace
 * the entire value atomically. Consumers can collect from [state] to observe changes.
 */
@OptIn(RestrictedAmplitudeFeature::class)
internal class AutocaptureManager(
    initialAutocapture: Set<AutocaptureOption>,
    initialInteractionsOptions: InteractionsOptions,
    remoteConfigClient: RemoteConfigClient?,
    private val logger: Logger,
    private val diagnosticsClient: DiagnosticsClient? = null,
) {
    private val _state = MutableStateFlow(AutocaptureState.from(initialAutocapture, initialInteractionsOptions))
    val state: StateFlow<AutocaptureState> = _state.asStateFlow()

    // Strong reference to prevent GC since RemoteConfigClient uses WeakReference
    private val remoteConfigCallback: RemoteConfigClient.RemoteConfigCallback?

    init {
        updateDiagnosticsTag(_state.value)

        if (remoteConfigClient != null) {
            remoteConfigCallback =
                RemoteConfigClient.RemoteConfigCallback { config, _, _ ->
                    handleRemoteConfig(config)
                }
            remoteConfigClient.subscribe(Key.ANALYTICS_SDK, remoteConfigCallback)
        } else {
            remoteConfigCallback = null
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun handleRemoteConfig(config: ConfigMap) {
        val currentState = _state.value

        val sessions = (config["sessions"] as? Boolean) ?: currentState.sessions
        val appLifecycles = (config["appLifecycles"] as? Boolean) ?: currentState.appLifecycles
        // Remote config uses "pageViews" which maps to screenViews
        val screenViews = (config["pageViews"] as? Boolean) ?: currentState.screenViews
        val deepLinks = (config["deepLinks"] as? Boolean) ?: currentState.deepLinks

        val interactions =
            buildList {
                // elementInteractions
                val elementInteractionsEnabled = config["elementInteractions"] as? Boolean
                if (elementInteractionsEnabled == true) {
                    add(InteractionType.ElementInteraction)
                } else if (elementInteractionsEnabled == null &&
                    InteractionType.ElementInteraction in currentState.interactions
                ) {
                    add(InteractionType.ElementInteraction)
                }

                // frustrationInteractions
                val frustrationConfig = config["frustrationInteractions"] as? Map<String, Any>
                if (frustrationConfig != null) {
                    val rageClickEnabled = InteractionType.RageClick in currentState.interactions
                    val deadClickEnabled = InteractionType.DeadClick in currentState.interactions
                    val frustrationEnabled =
                        frustrationConfig["enabled"] as? Boolean
                            ?: (rageClickEnabled || deadClickEnabled)
                    if (frustrationEnabled) {
                        val rageClickConfig = frustrationConfig["rageClick"] as? Map<String, Any>
                        // Fall back to current state when rageClick key is absent
                        val rageClickEnabled =
                            rageClickConfig?.get("enabled") as? Boolean ?: rageClickEnabled
                        if (rageClickEnabled) {
                            add(InteractionType.RageClick)
                        }

                        val deadClickConfig = frustrationConfig["deadClick"] as? Map<String, Any>
                        // Fall back to current state when deadClick key is absent
                        val deadClickEnabled =
                            deadClickConfig?.get("enabled") as? Boolean ?: deadClickEnabled
                        if (deadClickEnabled) {
                            add(InteractionType.DeadClick)
                        }
                    }
                } else {
                    // Keep existing frustration state if not in remote config
                    if (InteractionType.RageClick in currentState.interactions) add(InteractionType.RageClick)
                    if (InteractionType.DeadClick in currentState.interactions) add(InteractionType.DeadClick)
                }
            }

        val newState =
            AutocaptureState(
                sessions = sessions,
                appLifecycles = appLifecycles,
                screenViews = screenViews,
                deepLinks = deepLinks,
                interactions = interactions,
            )

        if (newState == currentState) {
            logger.debug("AutocaptureManager: Remote config unchanged, skipping update")
            return
        }

        _state.value = newState
        updateDiagnosticsTag(newState)

        logger.debug("AutocaptureManager: Updated state from remote config: $newState")
    }

    private fun updateDiagnosticsTag(state: AutocaptureState) {
        diagnosticsClient?.setTag("autocapture.enabled", state.toString())
    }
}
