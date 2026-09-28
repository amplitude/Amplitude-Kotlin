package com.amplitude.android.crash

import android.content.Context
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.io.PrintWriter

private const val STORAGE_PREFIX = "com.amplitude.crash_report"
private const val CRASH_REPORT_FILE_NAME = "com.amplitude.crash_report"
private const val MAX_STACK_FRAMES = 64
private const val MAX_CAUSE_DEPTH = 4

internal class CrashStorage(
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
    fun saveCrashReport(throwable: Throwable) {
        synchronized(fileLock) {
            val file = File(directory, CRASH_REPORT_FILE_NAME)
            FileOutputStream(file).use { stream ->
                PrintWriter(OutputStreamWriter(stream, Charsets.UTF_8)).apply {
                    print("Uncaught Exception: ")
                    println(throwable.javaClass.name)
                    print("Message: ")
                    println(throwable.message ?: "")
                    println()
                    println("Stack Trace:")
                    printThrowable(throwable)
                    flush()
                }
                stream.fd.sync()
            }
        }
    }

    suspend fun consumePreviousCrash(): String? =
        withContext(ioDispatcher) {
            synchronized(fileLock) {
                val file = File(directory, CRASH_REPORT_FILE_NAME)
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

    private fun PrintWriter.printThrowable(throwable: Throwable) {
        var current: Throwable? = throwable
        var remainingFrames = MAX_STACK_FRAMES
        var causeDepth = 0
        var truncated = false

        while (current != null && remainingFrames > 0 && causeDepth < MAX_CAUSE_DEPTH) {
            if (causeDepth > 0) {
                print("Caused by: ")
                print(current.javaClass.name)
                current.message?.let { message ->
                    print(": ")
                    print(message)
                }
                println()
            }

            val stackFrames = current.stackTrace
            val framesToWrite = minOf(stackFrames.size, remainingFrames)
            for (index in 0 until framesToWrite) {
                print("\tat ")
                println(stackFrames[index])
            }
            if (stackFrames.size > framesToWrite) {
                truncated = true
            }
            remainingFrames -= framesToWrite
            current = current.cause?.takeUnless { it === current }
            causeDepth++
        }

        if (truncated || current != null) {
            println("\t... crash report truncated")
        }
    }

    companion object {
        private val fileLock = Any()
    }
}
