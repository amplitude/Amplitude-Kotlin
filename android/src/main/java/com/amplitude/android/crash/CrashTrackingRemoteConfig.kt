package com.amplitude.android.crash

import com.amplitude.android.utilities.SemVer
import com.amplitude.core.RestrictedAmplitudeFeature
import com.amplitude.core.remoteconfig.ConfigMap
import com.amplitude.core.remoteconfig.RemoteConfigClient

private const val AVAILABILITIES = "availabilities"
private const val CRASH_TRACKING = "CrashTracking"
private const val ENABLED = "enabled"
private const val SAMPLE_RATE = "sampleRate"

/**
 * Whether crashes should be persisted: CrashTracking is available for this SDK version,
 * diagnostics is enabled, and the diagnostics sample rate is above zero. Until remote config
 * arrives, this is the previous run's result, so crashes during startup are still captured.
 */
@OptIn(RestrictedAmplitudeFeature::class)
internal class CrashTrackingRemoteConfig(
    remoteConfigClient: RemoteConfigClient,
    private val sdkVersion: String,
    private val crashTrackingEnabledStore: CrashTrackingEnabledStore,
) {
    private val previousRunEnabled = crashTrackingEnabledStore.isEnabled()

    @Volatile
    private var remoteEnabled: Boolean? = null

    val isCrashTrackingEnabled: Boolean
        get() = remoteEnabled ?: previousRunEnabled

    // Strong reference to prevent GC since RemoteConfigClient uses WeakReference
    private val remoteConfigCallback =
        RemoteConfigClient.RemoteConfigCallback { config, _, timestamp ->
            // A null timestamp is a failed fetch, so keep the last known state.
            if (timestamp != null) handleRemoteConfig(config)
        }

    init {
        remoteConfigClient.subscribe(RemoteConfigClient.Key.Diagnostics, callback = remoteConfigCallback)
    }

    private fun handleRemoteConfig(config: ConfigMap?) {
        val diagnosticsEnabled = config?.get(ENABLED) as? Boolean ?: true
        val sampleRate = (config?.get(SAMPLE_RATE) as? Number)?.toDouble() ?: 0.0
        val availabilities = config?.get(AVAILABILITIES) as? Map<*, *>
        val availableFrom = availabilities?.get(CRASH_TRACKING) as? String
        val required = availableFrom?.let { SemVer.create(it) }
        val current = SemVer.create(sdkVersion)
        val available = required != null && current != null && current >= required
        val enabled = available && diagnosticsEnabled && sampleRate > 0.0
        remoteEnabled = enabled
        crashTrackingEnabledStore.setEnabled(enabled)
    }
}
