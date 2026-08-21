package com.amplitude.android.anr

import android.content.Context
import android.os.Looper
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.io.PrintWriter

private const val STORAGE_PREFIX = "com.amplitude.anr_report"
private const val ANR_REPORT_FILE_NAME = "com.amplitude.anr_report"
private const val AEI_TIMESTAMP_FILE_NAME = "com.amplitude.anr_aei_timestamp"
private const val MAX_STACK_FRAMES = 64
private const val MAX_THREADS = 16

internal class AnrStorage(
    private val appContext: Context,
    private val ioDispatcher: CoroutineDispatcher,
) {
    val directory: File by lazy {
        File(
            appContext.getDir("amplitude", Context.MODE_PRIVATE),
            STORAGE_PREFIX,
        ).also {
            it.mkdirs()
        }
    }

    /** This performs I/O but must be done synchronously */
    fun saveAnrReport(timeoutMs: Long) {
        synchronized(fileLock) {
            val file = File(directory, ANR_REPORT_FILE_NAME)
            FileOutputStream(file).use { stream ->
                PrintWriter(OutputStreamWriter(stream, Charsets.UTF_8)).apply {
                    println("ANR detected")
                    print("Timeout: ")
                    print(timeoutMs)
                    println("ms")
                    println()
                    printThreadDump()
                    flush()
                }
                stream.fd.sync()
            }
        }
    }

    suspend fun consumePreviousAnr(): String? =
        withContext(ioDispatcher) {
            synchronized(fileLock) {
                val file = File(directory, ANR_REPORT_FILE_NAME)
                if (!file.exists()) return@withContext null
                try {
                    file.readText().ifEmpty { null }
                } catch (_: Exception) {
                    null
                } finally {
                    file.delete()
                }
            }
        }

    suspend fun loadLastAeiTimestamp(): Long =
        withContext(ioDispatcher) {
            synchronized(fileLock) {
                val file = File(directory, AEI_TIMESTAMP_FILE_NAME)
                if (!file.exists()) return@withContext 0L
                try {
                    file.readText().trim().toLongOrNull() ?: 0L
                } catch (_: Exception) {
                    0L
                }
            }
        }

    suspend fun saveLastAeiTimestamp(timestamp: Long) {
        withContext(ioDispatcher) {
            synchronized(fileLock) {
                File(directory, AEI_TIMESTAMP_FILE_NAME).writeText(timestamp.toString())
            }
        }
    }

    private fun PrintWriter.printThreadDump() {
        val traces = Thread.getAllStackTraces()
        val mainThread = Looper.getMainLooper().thread
        var remainingThreads = MAX_THREADS

        printThread(mainThread, traces[mainThread] ?: mainThread.stackTrace)
        remainingThreads--

        for ((thread, stack) in traces) {
            if (remainingThreads <= 0) {
                println("\t... anr report truncated")
                return
            }
            if (thread === mainThread) continue
            printThread(thread, stack)
            remainingThreads--
        }
    }

    private fun PrintWriter.printThread(
        thread: Thread,
        stack: Array<StackTraceElement>,
    ) {
        print("Thread: ")
        print(thread.name)
        print(" (id=")
        print(thread.id)
        print(", state=")
        print(thread.state)
        println(")")

        val framesToWrite = minOf(stack.size, MAX_STACK_FRAMES)
        for (index in 0 until framesToWrite) {
            print("\tat ")
            println(stack[index])
        }
        if (stack.size > framesToWrite) {
            println("\t... anr report truncated")
        }
        println()
    }

    companion object {
        private val fileLock = Any()
    }
}
