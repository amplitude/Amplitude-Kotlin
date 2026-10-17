package com.amplitude.android.anr

import android.app.ActivityManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.Process
import com.amplitude.android.crash.CrashTrackingRemoteConfig
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.min

private const val PING_INTERVAL_MS = 2_000L
private const val ANR_TIMEOUT_MS = 5_000L
private const val ANR_STATE_POLL_MS = 500L

/**
 * ANR capture via a main-thread watchdog (pre-API 30).
 *
 * A main-thread stall is recorded only when [ActivityManager] reports this process as
 * [ActivityManager.ProcessErrorStateInfo.NOT_RESPONDING]. The watchdog keeps checking until the
 * main thread answers, and writes one report for each enabled instance name.
 */
internal class LegacyAnrCatcher(
    context: Context,
    private val instanceName: String,
    private val ioDispatcher: CoroutineDispatcher,
    private val crashTrackingRemoteConfig: CrashTrackingRemoteConfig,
) : AnrCatcher {
    private val appContext = context.applicationContext
    private val anrStorage by lazy {
        AnrStorage(
            appContext = appContext,
            instanceName = instanceName,
        )
    }
    private val mainHandler: Handler by lazy { Handler(Looper.getMainLooper()) }

    init {
        if (crashTrackingRemoteConfig.isCrashTrackingEnabled) {
            register(this)
        }
    }

    override suspend fun consumePreviousAnrs(consumeAndRecord: (() -> List<String>) -> Unit) {
        withContext(ioDispatcher) {
            consumeAndRecord {
                val report = anrStorage.consumePreviousAnr()
                if (report == null) emptyList() else listOf(report)
            }
        }
    }

    /**
     * Unregisters this catcher, stopping the process-wide watchdog only when no catchers remain.
     * Earlier catchers stay registered until retirement, so a failed replacement leaves them active.
     */
    override fun detach() {
        val thread =
            synchronized(watchdogLock) {
                if (!registeredCatchers.remove(this) || registeredCatchers.isNotEmpty()) return
                val running = watchdogThread
                watchdogThread = null
                running
            }
        thread?.interrupt()
        thread?.join(1_000)
    }

    private fun persistAnrReport() {
        if (!crashTrackingRemoteConfig.isCrashTrackingEnabled) return
        anrStorage.saveAnrReport(ANR_TIMEOUT_MS)
    }

    companion object {
        private val watchdogLock = Any()
        private var watchdogThread: Thread? = null

        /** Registration order lets the newest catcher for each instance name write the report. */
        private val registeredCatchers = linkedSetOf<LegacyAnrCatcher>()

        private fun register(catcher: LegacyAnrCatcher) {
            synchronized(watchdogLock) {
                registeredCatchers.add(catcher)
                if (watchdogThread?.isAlive == true) return

                val thread =
                    Thread(
                        { runWatchdog() },
                        "amplitude-anr-watchdog",
                    )
                thread.isDaemon = true
                watchdogThread = thread
                thread.start()
            }
        }

        internal fun stopWatchdog() {
            val thread =
                synchronized(watchdogLock) {
                    val running = watchdogThread
                    watchdogThread = null
                    registeredCatchers.clear()
                    running
                }
            thread?.interrupt()
            thread?.join(1_000)
        }

        private fun runWatchdog() {
            try {
                while (!Thread.currentThread().isInterrupted) {
                    val catcher =
                        synchronized(watchdogLock) {
                            if (watchdogThread !== Thread.currentThread()) return
                            registeredCatchers.lastOrNull()
                        } ?: break
                    val responded = AtomicBoolean(false)
                    catcher.mainHandler.post { responded.set(true) }
                    Thread.sleep(PING_INTERVAL_MS)
                    if (responded.get()) continue
                    if (waitUntil(responded, ANR_TIMEOUT_MS - PING_INTERVAL_MS)) continue
                    waitForSystemAnr(catcher, responded)
                }
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
            }
        }

        private fun waitForSystemAnr(
            catcher: LegacyAnrCatcher,
            responded: AtomicBoolean,
        ) {
            while (!responded.get()) {
                if (Thread.currentThread().isInterrupted) {
                    throw InterruptedException()
                }
                if (isProcessNotResponding(catcher.appContext)) {
                    persistAnrReports()
                    waitUntil(responded, Long.MAX_VALUE)
                    return
                }
                if (waitUntil(responded, ANR_STATE_POLL_MS)) return
            }
        }

        private fun persistAnrReports() {
            synchronized(watchdogLock) {
                if (watchdogThread !== Thread.currentThread()) return
                // Keep removal atomic with persistence so detach cannot leave a pending write.
                for (catcher in registeredCatchers.associateBy { it.instanceName }.values) {
                    try {
                        catcher.persistAnrReport()
                    } catch (_: Throwable) {
                        // A failed instance must not prevent reports for the others.
                    }
                }
            }
        }

        private fun waitUntil(
            flag: AtomicBoolean,
            timeoutMs: Long,
        ): Boolean {
            val deadline =
                if (timeoutMs == Long.MAX_VALUE) {
                    Long.MAX_VALUE
                } else {
                    System.currentTimeMillis() + timeoutMs
                }
            while (!flag.get()) {
                if (Thread.currentThread().isInterrupted) {
                    throw InterruptedException()
                }
                val remaining = deadline - System.currentTimeMillis()
                if (remaining <= 0L) return false
                Thread.sleep(min(50L, remaining))
            }
            return true
        }
    }
}

/**
 * Returns whether the system currently considers this process to be in an ANR.
 * A failed lookup is not an ANR.
 */
internal fun isProcessNotResponding(context: Context): Boolean {
    val processes =
        try {
            val activityManager =
                context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
                    ?: return false
            activityManager.getProcessesInErrorState()
        } catch (_: Exception) {
            return false
        } ?: return false
    val pid = Process.myPid()
    return processes.any { info ->
        info.pid == pid && info.condition == ActivityManager.ProcessErrorStateInfo.NOT_RESPONDING
    }
}
