package com.amplitude.android.crash

import android.content.Context
import android.os.Process
import kotlinx.coroutines.CoroutineDispatcher
import kotlin.system.exitProcess

internal class CrashCatcher(
    context: Context,
    ioDispatcher: CoroutineDispatcher,
    private val crashTrackingRemoteConfig: CrashTrackingRemoteConfig,
) {
    private val appContext = context.applicationContext
    private val crashStorage by lazy {
        CrashStorage(
            appContext = appContext,
            ioDispatcher = ioDispatcher,
        )
    }

    @Volatile
    private var detached = false

    private var installed = false

    init {
        install()
    }

    fun install() {
        if (!crashTrackingRemoteConfig.isCrashTrackingEnabled) return
        synchronized(handlerInstallLock) {
            if (installed) return
            installed = true
            val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
            Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
                try {
                    if (!detached && crashTrackingRemoteConfig.isCrashTrackingEnabled) {
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
     * Stops persisting crashes once the owning SDK instance is retired or abandoned. The handler
     * stays in the chain, since it cannot be unregistered.
     */
    fun detach() {
        detached = true
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
        private val handlerInstallLock = Any()
        private val storageLock = Any()
        private var lastPersistedThrowable: Throwable? = null
    }
}
