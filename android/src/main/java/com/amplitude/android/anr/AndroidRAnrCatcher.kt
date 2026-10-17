package com.amplitude.android.anr

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import com.amplitude.android.crash.CrashTrackingRemoteConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.InputStream

private const val MAX_TRACE_CHARS = 32 * 1024

/**
 * ANR capture via ApplicationExitInfo (API 30+).
 */
@RequiresApi(Build.VERSION_CODES.R)
internal class AndroidRAnrCatcher(
    private val context: Context,
    private val instanceName: String,
    private val ioDispatcher: CoroutineDispatcher,
    private val crashTrackingRemoteConfig: CrashTrackingRemoteConfig,
) : AnrCatcher {
    private val anrStorage by lazy {
        AnrStorage(
            appContext = context.applicationContext,
            instanceName = instanceName,
        )
    }

    override suspend fun consumePreviousAnrs(consumeAndRecord: (() -> List<String>) -> Unit) {
        if (!crashTrackingRemoteConfig.isCrashTrackingEnabled) return
        val collectionStart =
            withContext(ioDispatcher) {
                consumeMutex.withLock {
                    var timestamp: Long? = null
                    var establishedBaseline = false
                    consumeAndRecord {
                        if (crashTrackingRemoteConfig.isCrashTrackingEnabled) {
                            val existing = anrStorage.loadLastAeiTimestamp()
                            if (existing == null) {
                                // Persist the baseline before the system lookup can fail or suspend.
                                timestamp =
                                    System.currentTimeMillis().also { anrStorage.saveLastAeiTimestamp(it) }
                                establishedBaseline = true
                            } else {
                                timestamp = existing
                            }
                        }
                        emptyList()
                    }
                    timestamp?.let { AeiCursor(it, establishedBaseline) }
                }
            } ?: return

        // Exit timestamps can run ahead of the wall clock. The consume that starts collection
        // moves the cursor past ANRs already in history and records none of them.
        if (collectionStart.establishedBaseline) {
            moveCursorPastHistoricalAnrs(collectionStart.timestamp, consumeAndRecord)
            return
        }

        val exits = loadHistoricalExits() ?: return
        // System calls and trace reads can be slow. Prepare them without blocking replacement.
        val prepared =
            exits.filter { it.reason == ApplicationExitInfo.REASON_ANR && it.timestamp > collectionStart.timestamp }
                .sortedBy { it.timestamp }
                .map { PreparedAnr(it.timestamp, formatReport(it)) }
        if (prepared.isEmpty()) return

        withContext(ioDispatcher) {
            consumeMutex.withLock {
                consumeAndRecord {
                    if (!crashTrackingRemoteConfig.isCrashTrackingEnabled) return@consumeAndRecord emptyList()
                    // Another consumer may have claimed the same exits during preparation.
                    val lastTimestamp = anrStorage.loadLastAeiTimestamp() ?: collectionStart.timestamp
                    val unread = prepared.filter { it.timestamp > lastTimestamp }
                    if (unread.isNotEmpty()) {
                        anrStorage.saveLastAeiTimestamp(unread.last().timestamp)
                    }
                    unread.map { it.report }
                }
            }
        }
    }

    private suspend fun moveCursorPastHistoricalAnrs(
        baseline: Long,
        consumeAndRecord: (() -> List<String>) -> Unit,
    ) {
        val exits = loadHistoricalExits() ?: return
        val newestHistorical =
            exits
                .filter { it.reason == ApplicationExitInfo.REASON_ANR && it.timestamp > baseline }
                .maxOfOrNull { it.timestamp }
                ?: return
        withContext(ioDispatcher) {
            consumeMutex.withLock {
                consumeAndRecord {
                    if (!crashTrackingRemoteConfig.isCrashTrackingEnabled) return@consumeAndRecord emptyList()
                    val current = anrStorage.loadLastAeiTimestamp() ?: baseline
                    if (newestHistorical > current) {
                        anrStorage.saveLastAeiTimestamp(newestHistorical)
                    }
                    emptyList()
                }
            }
        }
    }

    private suspend fun loadHistoricalExits(): List<ApplicationExitInfo>? =
        withContext(ioDispatcher) {
            try {
                val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
                am.getHistoricalProcessExitReasons(context.packageName, 0, 0)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }
        }

    private suspend fun formatReport(exit: ApplicationExitInfo): String {
        val trace = readTrace(exit)
        return buildString {
            appendLine("ANR detected")
            appendLine("Source: ApplicationExitInfo")
            append("Pid: ")
            appendLine(exit.pid)
            append("Timestamp: ")
            appendLine(exit.timestamp)
            append("Description: ")
            appendLine(exit.description ?: "")
            if (!trace.isNullOrEmpty()) {
                appendLine()
                appendLine("Trace:")
                append(trace)
            }
        }
    }

    private suspend fun readTrace(exit: ApplicationExitInfo): String? =
        withContext(ioDispatcher) {
            val stream =
                try {
                    exit.traceInputStream
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    null
                } ?: return@withContext null

            try {
                stream.use { input ->
                    readBounded(input)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }
        }

    private fun readBounded(input: InputStream): String {
        val reader = input.reader(Charsets.UTF_8)
        val builder = StringBuilder()
        val buffer = CharArray(4096)
        var remaining = MAX_TRACE_CHARS
        while (remaining > 0) {
            val count = reader.read(buffer, 0, minOf(buffer.size, remaining))
            if (count <= 0) break
            builder.append(buffer, 0, count)
            remaining -= count
        }
        if (remaining <= 0) {
            builder.append("\n\t... anr report truncated")
        }
        return builder.toString()
    }

    private companion object {
        private val consumeMutex = Mutex()
    }
}

private data class AeiCursor(val timestamp: Long, val establishedBaseline: Boolean)

private data class PreparedAnr(val timestamp: Long, val report: String)
