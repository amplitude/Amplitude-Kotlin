package com.amplitude.android.anr

import android.content.Context
import android.os.Build
import com.amplitude.android.crash.CrashTrackingRemoteConfig
import kotlinx.coroutines.CoroutineDispatcher

internal interface AnrCatcher {
    suspend fun consumePreviousAnrs(): List<String>

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
