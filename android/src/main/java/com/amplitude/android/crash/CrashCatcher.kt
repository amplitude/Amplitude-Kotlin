package com.amplitude.android.crash

import android.content.Context
import android.os.Process
import com.amplitude.core.RestrictedAmplitudeFeature
import com.amplitude.core.diagnostics.DiagnosticsClient
import kotlinx.coroutines.CoroutineDispatcher
import kotlin.system.exitProcess

@OptIn(RestrictedAmplitudeFeature::class)
internal class CrashCatcher(
    private val context: Context,
    ioDispatcher: CoroutineDispatcher,
    private val diagnosticsClientLazy: Lazy<DiagnosticsClient>,
    private val crashTrackingRemoteConfigLazy: Lazy<CrashTrackingRemoteConfig>,
) {
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
                    crashTrackingRemoteConfigLazy.value.isCrashTrackingEnabled &&
                    diagnosticsClientLazy.value.shouldTrack
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

    companion object {
        private val storageLock = Any()
        private var lastPersistedThrowable: Throwable? = null
    }
}
