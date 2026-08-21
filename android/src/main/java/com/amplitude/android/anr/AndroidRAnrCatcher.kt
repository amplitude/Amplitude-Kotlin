package com.amplitude.android.anr

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
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
    private val ioDispatcher: CoroutineDispatcher,
) : AnrCatcher {
    private val anrStorage by lazy {
        AnrStorage(
            appContext = context.applicationContext,
            ioDispatcher = ioDispatcher,
        )
    }

    override suspend fun consumePreviousAnrs(): List<String> {
        val exits = loadHistoricalExits() ?: return emptyList()
        val unreadAnrs =
            consumeMutex.withLock {
                val lastTimestamp = anrStorage.loadLastAeiTimestamp()
                val unread =
                    exits
                        .filter { exit ->
                            exit.reason == ApplicationExitInfo.REASON_ANR &&
                                exit.timestamp > lastTimestamp
                        }
                        .sortedBy { it.timestamp }
                if (unread.isNotEmpty()) {
                    anrStorage.saveLastAeiTimestamp(unread.maxOf { it.timestamp })
                }
                unread
            }

        return unreadAnrs
            .map { exit ->
                formatReport(exit)
            }
    }

    private suspend fun loadHistoricalExits(): List<ApplicationExitInfo>? =
        withContext(ioDispatcher) {
            try {
                val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
                am.getHistoricalProcessExitReasons(context.packageName, 0, 0)
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
                } catch (_: Exception) {
                    null
                } ?: return@withContext null

            try {
                stream.use { input ->
                    readBounded(input)
                }
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
