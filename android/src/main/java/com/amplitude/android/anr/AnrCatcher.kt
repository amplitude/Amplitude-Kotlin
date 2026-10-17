package com.amplitude.android.anr

import android.content.Context
import android.os.Build
import com.amplitude.android.crash.CrashTrackingRemoteConfig
import kotlinx.coroutines.CoroutineDispatcher

internal interface AnrCatcher {
    /**
     * Supplies synchronous claims on the storage dispatcher. The caller must check ownership,
     * execute each accepted claim, and record its reports atomically. System lookups and trace
     * preparation may suspend outside that ownership guard.
     */
    suspend fun consumePreviousAnrs(consumeAndRecord: (() -> List<String>) -> Unit)

    fun detach() {}
}

internal fun createAnrCatcher(
    context: Context,
    instanceName: String,
    ioDispatcher: CoroutineDispatcher,
    crashTrackingRemoteConfig: CrashTrackingRemoteConfig,
): AnrCatcher {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        AndroidRAnrCatcher(
            context = context,
            instanceName = instanceName,
            ioDispatcher = ioDispatcher,
            crashTrackingRemoteConfig = crashTrackingRemoteConfig,
        )
    } else {
        LegacyAnrCatcher(
            context = context,
            instanceName = instanceName,
            ioDispatcher = ioDispatcher,
            crashTrackingRemoteConfig = crashTrackingRemoteConfig,
        )
    }
}
