package com.amplitude.android.migration

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import android.database.sqlite.SQLiteOpenHelper
import com.amplitude.common.Logger
import com.amplitude.common.android.LogcatLogger
import com.amplitude.core.Amplitude
import com.amplitude.core.Configuration
import org.json.JSONObject
import java.io.File
import java.util.LinkedList
import java.util.Locale

/**
 * Store the database related constants.
 * Align with com/amplitude/api/DatabaseHelper.java in previous SDK.
 */
object DatabaseConstants {
    const val DATABASE_NAME = "com.amplitude.api"
    const val DATABASE_VERSION = 4

    const val EVENT_TABLE_NAME = "events"
    const val IDENTIFY_TABLE_NAME = "identifys"
    const val IDENTIFY_INTERCEPTOR_TABLE_NAME = "identify_interceptor"
    const val ID_FIELD = "id"
    const val EVENT_FIELD = "event"

    const val LONG_STORE_TABLE_NAME = "long_store"
    const val STORE_TABLE_NAME = "store"
    const val KEY_FIELD = "key"
    const val VALUE_FIELD = "value"

    const val ROW_ID_FIELD = "\$rowId"
}

/**
 * The SDK doesn't need to write/read from local sqlite database.
 * This storage class is used for migrating events only.
 */
class DatabaseStorage(
    context: Context,
    databaseName: String,
    private val logger: Logger,
) : SQLiteOpenHelper(
        context,
        databaseName,
        null,
        DatabaseConstants.DATABASE_VERSION,
    ) {
    private var file: File = context.getDatabasePath(databaseName)
    private var isValidDatabaseFile = true
    var currentDbVersion: Int = DatabaseConstants.DATABASE_VERSION
        private set

    override fun onCreate(db: SQLiteDatabase) {
        // File exists but it is not a legacy database for some reason.
        this.isValidDatabaseFile = false
        logger.error("Attempt to re-create existing legacy database file ${file.absolutePath}")
    }

    override fun onUpgrade(
        db: SQLiteDatabase?,
        oldVersion: Int,
        newVersion: Int,
    ) {
        currentDbVersion = oldVersion
    }

    private fun queryDb(
        db: SQLiteDatabase,
        table: String,
        columns: Array<String?>?,
        selection: String?,
        selectionArgs: Array<String?>?,
        orderBy: String?,
    ): Cursor? {
        return db.query(table, columns, selection, selectionArgs, null, null, orderBy, null)
    }

    private fun handleIfCursorRowTooLargeException(e: java.lang.IllegalStateException) {
        val message = e.message
        if (!message.isNullOrEmpty() && message.contains("Couldn't read") && message.contains("CursorWindow")) {
            closeDb()
        } else {
            throw e
        }
    }

    private fun convertIfCursorWindowException(e: java.lang.RuntimeException) {
        val message = e.message
        if (message.isNullOrEmpty()) throw e

        if (message.startsWith("Cursor window allocation of") ||
            message.startsWith("Could not allocate CursorWindow")
        ) {
            throw CursorWindowAllocationException(message)
        } else {
            throw e
        }
    }

    private fun closeDb() {
        try {
            close()
        } catch (e: Exception) {
            LogcatLogger.logger.error("close failed: ${e.message}")
        }
    }

    @Synchronized
    fun readEventsContent(): List<JSONObject> {
        return readEventsFromTable(DatabaseConstants.EVENT_TABLE_NAME)
    }

    @Synchronized
    fun readIdentifiesContent(): List<JSONObject> {
        return readEventsFromTable(DatabaseConstants.IDENTIFY_TABLE_NAME)
    }

    @Synchronized
    fun readInterceptedIdentifiesContent(): List<JSONObject> {
        if (currentDbVersion < 4) {
            return listOf()
        }
        return readEventsFromTable(DatabaseConstants.IDENTIFY_INTERCEPTOR_TABLE_NAME)
    }

    private fun readEventsFromTable(table: String): List<JSONObject> {
        if (!file.exists()) {
            return arrayListOf()
        }

        val events: MutableList<JSONObject> = LinkedList()
        var cursor: Cursor? = null
        try {
            val db = readableDatabase
            if (!isValidDatabaseFile) {
                return arrayListOf()
            }

            cursor =
                queryDb(
                    db,
                    table,
                    arrayOf(DatabaseConstants.ID_FIELD, DatabaseConstants.EVENT_FIELD),
                    null,
                    null,
                    DatabaseConstants.ID_FIELD + " ASC",
                )
            while (cursor!!.moveToNext()) {
                val rowId = cursor.getLong(0)
                val event = cursor.getString(1)
                if (event.isNullOrEmpty()) {
                    continue
                }
                val obj = JSONObject(event)
                obj.put(DatabaseConstants.ROW_ID_FIELD, rowId)
                events.add(obj)
            }
        } catch (e: SQLiteException) {
            LogcatLogger.logger.error(
                "read events from $table failed: ${e.message}",
            )
            closeDb()
        } catch (e: StackOverflowError) {
            LogcatLogger.logger.error(
                "read events from $table failed: ${e.message}",
            )
            closeDb()
        } catch (e: IllegalStateException) {
            // put before Runtime since IllegalState extends
            handleIfCursorRowTooLargeException(e)
        } catch (e: RuntimeException) {
            convertIfCursorWindowException(e)
        } finally {
            cursor?.close()
            close()
        }
        return events
    }

    @Synchronized
    fun removeEvent(rowId: Long) {
        removeEventFromTable(DatabaseConstants.EVENT_TABLE_NAME, rowId)
    }

    @Synchronized
    fun removeIdentify(rowId: Long) {
        removeEventFromTable(DatabaseConstants.IDENTIFY_TABLE_NAME, rowId)
    }

    @Synchronized
    fun removeInterceptedIdentify(rowId: Long) {
        if (currentDbVersion < 4) {
            return
        }
        removeEventFromTable(DatabaseConstants.IDENTIFY_INTERCEPTOR_TABLE_NAME, rowId)
    }

    private fun removeEventFromTable(
        table: String,
        rowId: Long,
    ) {
        try {
            val db = writableDatabase
            db.delete(
                table,
                "${DatabaseConstants.ID_FIELD} = ?",
                arrayOf(rowId.toString()),
            )
        } catch (e: SQLiteException) {
            LogcatLogger.logger.error(
                "remove events from $table failed: ${e.message}",
            )
            closeDb()
        } catch (e: StackOverflowError) {
            LogcatLogger.logger.error(
                "remove events from $table failed: ${e.message}",
            )
            closeDb()
        } finally {
            close()
        }
    }

    @Synchronized
    fun getValue(key: String): String? {
        return getValueFromTable(DatabaseConstants.STORE_TABLE_NAME, key) as String?
    }

    @Synchronized
    fun getLongValue(key: String): Long? {
        return getValueFromTable(DatabaseConstants.LONG_STORE_TABLE_NAME, key) as Long?
    }

    private fun getValueFromTable(
        table: String,
        key: String,
    ): Any? {
        if (!file.exists()) {
            return null
        }

        var value: Any? = null
        var cursor: Cursor? = null
        try {
            val db = readableDatabase
            if (!isValidDatabaseFile) {
                return null
            }

            cursor =
                queryDb(
                    db,
                    table,
                    arrayOf<String?>(
                        DatabaseConstants.KEY_FIELD,
                        DatabaseConstants.VALUE_FIELD,
                    ),
                    DatabaseConstants.KEY_FIELD + " = ?",
                    arrayOf(key),
                    null,
                )
            if (cursor!!.moveToFirst()) {
                value = if (table == DatabaseConstants.STORE_TABLE_NAME) cursor.getString(1) else cursor.getLong(1)
            }
        } catch (e: SQLiteException) {
            LogcatLogger.logger.error(
                "getValue from $table failed: ${e.message}",
            )
            // Hard to recover from SQLiteExceptions, just start fresh
            closeDb()
        } catch (e: StackOverflowError) {
            LogcatLogger.logger.error(
                "getValue from $table failed: ${e.message}",
            )
            // potential stack overflow error when getting database on custom Android versions
            closeDb()
        } catch (e: IllegalStateException) {
            // put before Runtime since IllegalState extends
            // cursor window row too big exception
            handleIfCursorRowTooLargeException(e)
        } catch (e: RuntimeException) {
            // cursor window allocation exception
            convertIfCursorWindowException(e)
        } finally {
            cursor?.close()
            close()
        }
        return value
    }

    @Synchronized
    fun removeValue(key: String) {
        removeValueFromTable(DatabaseConstants.STORE_TABLE_NAME, key)
    }

    @Synchronized
    fun removeLongValue(key: String) {
        removeValueFromTable(DatabaseConstants.LONG_STORE_TABLE_NAME, key)
    }

    private fun removeValueFromTable(
        table: String,
        key: String,
    ) {
        try {
            val db = writableDatabase
            db.delete(
                table,
                "${DatabaseConstants.KEY_FIELD} = ?",
                arrayOf(key),
            )
        } catch (e: SQLiteException) {
            LogcatLogger.logger.error(
                "remove value from $table failed: ${e.message}",
            )
            closeDb()
        } catch (e: StackOverflowError) {
            LogcatLogger.logger.error(
                "remove value from $table failed: ${e.message}",
            )
            closeDb()
        } finally {
            close()
        }
    }
}

class CursorWindowAllocationException(description: String?) :
    java.lang.RuntimeException(description)

object DatabaseStorageProvider {
    private val instances: MutableMap<String, DatabaseStorage> = mutableMapOf()

    fun getStorage(amplitude: Amplitude): DatabaseStorage {
        val configuration = amplitude.configuration as com.amplitude.android.Configuration
        val databaseName = getDatabaseName(configuration.instanceName)
        var storage = instances[databaseName]
        if (storage == null) {
            storage = DatabaseStorage(configuration.context, databaseName, configuration.loggerProvider.getLogger(amplitude))
            instances[databaseName] = storage
        }

        return storage
    }

    private fun getDatabaseName(instanceName: String?): String {
        val normalizedInstanceName = instanceName?.lowercase(Locale.getDefault())
        return if (normalizedInstanceName.isNullOrEmpty() || normalizedInstanceName == Configuration.DEFAULT_INSTANCE) {
            DatabaseConstants.DATABASE_NAME
        } else {
            "${DatabaseConstants.DATABASE_NAME}_$normalizedInstanceName"
        }
    }
}
