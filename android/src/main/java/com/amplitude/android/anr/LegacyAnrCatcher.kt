package com.amplitude.android.anr

import android.app.ActivityManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.Process
import com.amplitude.android.crash.CrashTrackingRemoteConfig
import kotlinx.coroutines.CoroutineDispatcher
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
 * main thread answers, and writes the report once.
 */
internal class LegacyAnrCatcher(
    context: Context,
    ioDispatcher: CoroutineDispatcher,
    private val crashTrackingRemoteConfig: CrashTrackingRemoteConfig,
) : AnrCatcher {
    private val appContext = context.applicationContext
    private val anrStorage by lazy {
        AnrStorage(
            appContext = appContext,
            ioDispatcher = ioDispatcher,
        )
    }
    private val mainHandler: Handler by lazy { Handler(Looper.getMainLooper()) }

    init {
        if (crashTrackingRemoteConfig.isCrashTrackingEnabled) {
            register(this)
        }
    }

    override suspend fun consumePreviousAnrs(): List<String> {
        val report = anrStorage.consumePreviousAnr()
        return if (report == null) emptyList() else listOf(report)
    }

    /**
     * Stops the process-wide watchdog if this catcher still owns it. A replacement that has
     * already [register]ed keeps the thread and is left alone.
     */
    override fun detach() {
        val thread =
            synchronized(watchdogLock) {
                if (activeCatcher !== this) return
                activeCatcher = null
                val running = watchdogThread
                watchdogThread = null
                running
            }
        thread?.interrupt()
        thread?.join(1_000)
    }

    private fun persistAnrReport() {
        if (!crashTrackingRemoteConfig.isCrashTrackingEnabled) return
        synchronized(storageLock) {
            anrStorage.saveAnrReport(ANR_TIMEOUT_MS)
        }
    }

    companion object {
        private val storageLock = Any()
        private val watchdogLock = Any()
        private var watchdogThread: Thread? = null

        @Volatile
        private var activeCatcher: LegacyAnrCatcher? = null

        private fun register(catcher: LegacyAnrCatcher) {
            synchronized(watchdogLock) {
                activeCatcher = catcher
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
                    activeCatcher = null
                    running
                }
            thread?.interrupt()
            thread?.join(1_000)
        }

        private fun runWatchdog() {
            try {
                while (!Thread.currentThread().isInterrupted) {
                    val catcher = activeCatcher ?: break
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
                    try {
                        catcher.persistAnrReport()
                    } catch (_: Throwable) {
                        // Best-effort: never throw from the watchdog
                    }
                    waitUntil(responded, Long.MAX_VALUE)
                    return
                }
                if (waitUntil(responded, ANR_STATE_POLL_MS)) return
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
