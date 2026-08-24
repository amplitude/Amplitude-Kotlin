package com.amplitude.android.crash

import android.content.Context
import android.os.Process
import com.amplitude.core.RestrictedAmplitudeFeature
import com.amplitude.core.diagnostics.DiagnosticsClient
import kotlinx.coroutines.CoroutineDispatcher
import kotlin.system.exitProcess

private const val AMPLITUDE_PACKAGE_PREFIX = "com.amplitude."

@OptIn(RestrictedAmplitudeFeature::class)
internal class CrashCatcher(
    context: Context,
    ioDispatcher: CoroutineDispatcher,
    diagnosticsClient: () -> DiagnosticsClient?,
    crashTrackingRemoteConfig: () -> CrashTrackingRemoteConfig?,
) {
    private val appContext = context.applicationContext
    private val crashStorage by lazy {
        CrashStorage(
            appContext = appContext,
            ioDispatcher = ioDispatcher,
        )
    }

    // The handler outlives this instance. Providers resolve through a weak reference so an
    // abandoned instance can be collected, and [detach] clears them once the instance retires.
    private var diagnosticsClientProvider: (() -> DiagnosticsClient?)? = diagnosticsClient
    private var crashTrackingRemoteConfigProvider: (() -> CrashTrackingRemoteConfig?)? =
        crashTrackingRemoteConfig

    init {
        synchronized(handlerInstallLock) {
            val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
            Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
                try {
                    if (
                        throwable.isAmplitudeSdkCrash() &&
                        crashTrackingRemoteConfigProvider?.invoke()?.isCrashTrackingEnabled == true &&
                        // Persist only when this session is already sampled in. That misses
                        // crashes before remote config arrives (default sample rate is 0), but
                        // avoids writing crash files in unsampled sessions.
                        diagnosticsClientProvider?.invoke()?.shouldTrack == true
                    ) {
                        saveCrashReport(throwable)
                    }
                } catch (_: Throwable) {
                    // Best-effort: never throw from the uncaught exception handler
                } finally {
                    previousHandler?.uncaughtException(thread, throwable)
                        ?: run {
                            // If no handler to chain to, kill the process
                            Process.killProcess(Process.myPid())
                            exitProcess(10)
                        }
                }
            }
        }
    }

    /**
     * Stops persisting crashes and releases the SDK instance this catcher reads from. The handler
     * stays in the chain, since it cannot be unregistered, but it holds nothing afterwards.
     */
    fun detach() {
        diagnosticsClientProvider = null
        crashTrackingRemoteConfigProvider = null
    }

    suspend fun readPreviousCrash(): String? {
        return crashStorage.readPreviousCrash()
    }

    fun deletePreviousCrash() {
        crashStorage.deletePreviousCrash()
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
        private val handlerInstallLock = Any()
        private val storageLock = Any()
        private var lastPersistedThrowable: Throwable? = null
    }
}
