package com.amplitude.android.crash

import com.amplitude.android.utilities.SemVer
import com.amplitude.core.RestrictedAmplitudeFeature
import com.amplitude.core.remoteconfig.ConfigMap
import com.amplitude.core.remoteconfig.RemoteConfigClient
import java.util.concurrent.atomic.AtomicBoolean

private const val AVAILABILITIES = "availabilities"
private const val CRASH_TRACKING = "CrashTracking"

@OptIn(RestrictedAmplitudeFeature::class)
internal class CrashTrackingRemoteConfig(
    private val remoteConfigClient: RemoteConfigClient,
    private val sdkVersion: String,
) {
    private val crashTrackingEnabled = AtomicBoolean(false)

    val isCrashTrackingEnabled: Boolean
        get() = crashTrackingEnabled.get()

    private val initialized = AtomicBoolean(false)

    // Strong reference to prevent GC since RemoteConfigClient uses WeakReference
    private lateinit var remoteConfigCallback: RemoteConfigClient.RemoteConfigCallback

    /** idempotent */
    fun initialize() {
        if (!initialized.compareAndSet(false, true)) return
        remoteConfigCallback =
            RemoteConfigClient.RemoteConfigCallback { config, _, _ ->
                handleRemoteConfig(config)
            }.also { callback ->
                remoteConfigClient.subscribe(RemoteConfigClient.Key.Diagnostics, callback = callback)
            }
    }

    private fun handleRemoteConfig(config: ConfigMap?) {
        if (config == null) return

        val availabilities = config[AVAILABILITIES] as? Map<*, *>
        val availableFrom = availabilities?.get(CRASH_TRACKING) as? String ?: return
        val current = SemVer.create(sdkVersion) ?: return
        val required = SemVer.create(availableFrom) ?: return
        crashTrackingEnabled.set(current >= required)
    }
}
