package com.amplitude.android.anr

import android.content.Context
import android.os.Build
import kotlinx.coroutines.CoroutineDispatcher

internal interface AnrCatcher {
    suspend fun consumePreviousAnrs(): List<String>
}

internal fun createAnrCatcher(
    context: Context,
    ioDispatcher: CoroutineDispatcher,
): AnrCatcher {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        AndroidRAnrCatcher(
            context = context,
            ioDispatcher = ioDispatcher,
        )
    } else {
        LegacyAnrCatcher(
            context = context,
            ioDispatcher = ioDispatcher,
        )
    }
}
