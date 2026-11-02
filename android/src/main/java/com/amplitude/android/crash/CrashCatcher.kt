package com.amplitude.android.crash

import android.content.Context
import android.os.Process
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.system.exitProcess

internal class CrashCatcher(
    context: Context,
    private val instanceName: String,
    private val crashTrackingRemoteConfig: CrashTrackingRemoteConfig,
) {
    private val appContext = context.applicationContext
    private val crashStorage by lazy {
        CrashStorage(
            appContext = appContext,
            instanceName = instanceName,
        )
    }

    @Volatile
    private var detached = false

    private var installed = false

    /**
     * Chains a crash-saving handler when the previous run left crash tracking on. Reads the
     * persisted decision from disk, so call it off the main thread.
     */
    suspend fun install() {
        if (!crashTrackingRemoteConfig.isCrashTrackingEnabled) return
        handlerInstallMutex.withLock {
            if (installed || detached) return
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
        crashTrackingRemoteConfig.detach()
    }

    fun consumePreviousCrash(): String? {
        return crashStorage.consumePreviousCrash()
    }

    private fun saveCrashReport(throwable: Throwable) {
        synchronized(storageLock) {
            if (lastPersistedThrowables[instanceName] === throwable) {
                // Already written for this instance name by another handler in the chain.
                return
            }
            crashStorage.saveCrashReport(throwable)
            lastPersistedThrowables[instanceName] = throwable
        }
    }

    companion object {
        private val handlerInstallMutex = Mutex()
        private val storageLock = Any()

        /** instanceName -> Throwable */
        private val lastPersistedThrowables = mutableMapOf<String, Throwable>()
    }
}
