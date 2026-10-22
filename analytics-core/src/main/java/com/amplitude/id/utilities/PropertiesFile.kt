package com.amplitude.id.utilities

import com.amplitude.common.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.lang.ref.WeakReference
import java.util.Properties
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * File-backed key/value store shared by canonical path within this process via [getInstance].
 * The first instance's logger and dispatcher are retained.
 *
 * Loads asynchronously, with synchronous fallback on first access. Failed loads reject mutations.
 * Mutations update memory immediately and queue coalesced saves; acceptance does not mean durability.
 * Temporary and backup files preserve committed values across interrupted saves.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Deprecated("Not intended for public use. Will be internal in a future release.")
public class PropertiesFile private constructor(
    private val propertiesFile: File,
    private val logger: Logger? = null,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : KeyValueStore {
    public constructor(
        directory: File,
        fileNameWithoutExtension: String,
        logger: Logger?,
    ) : this(File(directory, "$fileNameWithoutExtension.properties"), logger)

    private val backupFile by lazy { File("${propertiesFile.path}.bak") }
    private val underlyingProperties: Properties by lazy { Properties() }
    private val fileDispatcher by lazy { ioDispatcher.limitedParallelism(1) }
    private val ioScope by lazy { CoroutineScope(SupervisorJob() + fileDispatcher) }
    private val saveQueued = AtomicBoolean(false)
    private val saveFailed = AtomicBoolean(false)
    private val fileDeleted = AtomicBoolean(false)
    private val loadStarted = AtomicBoolean(false)

    private val fileLock = ReentrantLock()
    private val lock = ReentrantLock()
    private val loadCondition = lock.newCondition()
    private var isLoaded = false
    private var loadFailed = AtomicBoolean(false)

    init {
        ioScope.launch {
            loadBlocking()
        }
    }

    override fun getLong(
        key: String,
        defaultVal: Long,
    ): Long = getString(key, null)?.toLongOrNull() ?: defaultVal

    override fun putLong(
        key: String,
        value: Long,
    ): Boolean = putString(key, value.toString())

    override fun deleteKey(key: String) {
        if (!awaitLoad()) return
        underlyingProperties.remove(key)
        queueSave()
    }

    public fun getString(
        key: String,
        defaultVal: String?,
    ): String? {
        if (!awaitLoad()) return defaultVal
        return underlyingProperties.getProperty(key, defaultVal)
    }

    public fun putString(
        key: String,
        value: String,
    ): Boolean {
        if (!awaitLoad()) return false
        val oldValue = synchronized(underlyingProperties) {
            fileDeleted.set(false)
            underlyingProperties.setProperty(key, value)
        }
        if (oldValue != value || saveFailed.get()) queueSave()
        return true
    }

    public fun remove(key: String): Boolean = remove(listOf(key))

    public fun remove(keys: List<String>): Boolean {
        if (!awaitLoad()) return false
        synchronized(underlyingProperties) {
            keys.forEach { underlyingProperties.remove(it) }
        }
        queueSave()
        return true
    }

    public fun load() {
        awaitLoad()
    }

    public fun deletePropertiesFile() {
        awaitLoad()
        fileLock.withLock {
            synchronized(underlyingProperties) {
                underlyingProperties.clear()
                fileDeleted.set(true)
            }
            propertiesFile.delete()
            backupFile.delete()
            loadFailed.set(false)
        }
    }

    private fun loadBlocking() {
        if (!loadStarted.compareAndSet(false, true)) return
        try {
            fileLock.withLock {
                restoreBackupBlocking()
                if (propertiesFile.exists()) {
                    FileInputStream(propertiesFile).use {
                        underlyingProperties.load(it)
                    }
                }
            }
        } catch (e: Exception) {
            loadFailed.set(true)
            underlyingProperties.clear()
            logger?.error(
                "Failed to load property file with path ${propertiesFile.absolutePath}, error stacktrace: ${e.stackTraceToString()}",
            )
        } finally {
            lock.withLock {
                isLoaded = true
                loadCondition.signalAll()
            }
        }
    }

    private fun awaitLoad(): Boolean {
        loadBlocking()
        return lock.withLock {
            while (!isLoaded) {
                loadCondition.awaitUninterruptibly()
            }
            !loadFailed.get()
        }
    }

    private fun queueSave() {
        if (!saveQueued.compareAndSet(false, true)) return
        ioScope.launch {
            saveQueued.set(false)
            save()
        }
    }

    private suspend fun save() = withContext(NonCancellable + fileDispatcher) {
        fileLock.withLock {
            if (fileDeleted.get()) return@withLock
            var temporaryFile: File? = null
            try {
                restoreBackupBlocking()
                val properties = underlyingProperties.clone() as Properties
                val directory = propertiesFile.absoluteFile.parentFile
                if (!directory.isDirectory && !directory.mkdirs() && !directory.isDirectory) {
                    throw IOException("Could not create directory at $directory")
                }

                temporaryFile = File.createTempFile("amplitude-${propertiesFile.name}-", ".tmp", directory)
                FileOutputStream(temporaryFile).use {
                    properties.store(it, null)
                    it.fd.sync()
                }

                if (propertiesFile.exists() && !propertiesFile.renameTo(backupFile)) {
                    throw IOException("Could not back up property file at ${propertiesFile.absolutePath}")
                }
                if (!temporaryFile.renameTo(propertiesFile)) {
                    throw IOException("Could not replace property file at ${propertiesFile.absolutePath}")
                }
                if (backupFile.exists() && !backupFile.delete()) {
                    throw IOException("Could not remove property file backup at ${backupFile.absolutePath}")
                }
                saveFailed.set(false)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                saveFailed.set(true)
                try {
                    restoreBackupBlocking()
                } catch (recoveryFailure: Exception) {
                    e.addSuppressed(recoveryFailure)
                }
                // Properties.store can throw AssertionError on Android 8.
                logger?.error(
                    "Failed to save property file with path ${propertiesFile.absolutePath}, error stacktrace: ${e.stackTraceToString()}",
                )
            } finally {
                temporaryFile?.delete()
            }
            Unit
        }
    }

    private fun restoreBackupBlocking() {
        if (!backupFile.exists()) return
        if (!backupFile.isFile) {
            throw IOException("Property file backup is not a file at ${backupFile.absolutePath}")
        }
        if (propertiesFile.exists() && !propertiesFile.delete()) {
            throw IOException("Could not remove uncommitted property file at ${propertiesFile.absolutePath}")
        }
        if (!backupFile.renameTo(propertiesFile)) {
            throw IOException("Could not restore property file backup at ${backupFile.absolutePath}")
        }
    }

    internal companion object {
        private val instances = mutableMapOf<String, WeakReference<PropertiesFile>>()

        /** Returns the process-local store for [filename], including its extension. */
        fun getInstance(
            directory: String,
            filename: String,
            logger: Logger? = null,
            ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
        ): PropertiesFile {
            val file = File(directory, filename).canonicalFile
            return synchronized(instances) {
                instances.entries.removeAll { it.value.get() == null }
                instances[file.path]?.get() ?: PropertiesFile(file, logger, ioDispatcher).also {
                    instances[file.path] = WeakReference(it)
                }
            }
        }
    }
}
