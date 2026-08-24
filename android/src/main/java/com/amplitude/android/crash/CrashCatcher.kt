package com.amplitude.android.crash

import android.content.Context
import android.os.Process
import com.amplitude.core.RestrictedAmplitudeFeature
import kotlinx.coroutines.CoroutineDispatcher
import kotlin.system.exitProcess

private const val AMPLITUDE_PACKAGE_PREFIX = "com.amplitude."

@OptIn(RestrictedAmplitudeFeature::class)
internal class CrashCatcher(
    context: Context,
    ioDispatcher: CoroutineDispatcher,
    private val crashTrackingRemoteConfigProvider: () -> CrashTrackingRemoteConfig?,
) {
    private val context = context.applicationContext
    private val defaultCrashHandler = Thread.getDefaultUncaughtExceptionHandler()
    private val crashStorage by lazy {
        CrashStorage(
            appContext = context,
            ioDispatcher = ioDispatcher,
        )
    }

    init {
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                if (
                    throwable.isAmplitudeSdkCrash() &&
                    crashTrackingRemoteConfigProvider()?.isCrashTrackingEnabled == true
                ) {
                    saveCrashReport(throwable)
                }
            } catch (_: Throwable) {
                // Best-effort: never throw from the uncaught exception handler
            } finally {
                defaultCrashHandler?.uncaughtException(thread, throwable)
                    ?: run {
                        // If no handler to chain to, kill the process
                        Process.killProcess(Process.myPid())
                        exitProcess(10)
                    }
            }
        }
    }

    suspend fun consumePreviousCrash(): String? {
        return crashStorage.consumePreviousCrash()
    }

    private fun saveCrashReport(throwable: Throwable) {
        synchronized(storageLock) {
            if (lastPersistedThrowable === throwable) {
                // Already written; skip if another handler in the chain persists the same crash
                return
            }
            lastPersistedThrowable = throwable
            crashStorage.saveCrashReport(throwable)
        }
    }

    private fun Throwable.isAmplitudeSdkCrash(): Boolean {
        var current: Throwable? = this
        val seen = mutableSetOf<Throwable>()
        while (current != null && seen.add(current)) {
            if (current.stackTrace.any { it.className.startsWith(AMPLITUDE_PACKAGE_PREFIX) }) {
                return true
            }
            current = current.cause
        }
        return false
    }

    companion object {
        private val storageLock = Any()
        private var lastPersistedThrowable: Throwable? = null
    }
}
