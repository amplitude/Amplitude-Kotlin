package com.amplitude.android.migration

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import android.database.sqlite.SQLiteOpenHelper
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

    const val STORE_TABLE_NAME = "store"
    const val KEY_FIELD = "key"
    const val VALUE_FIELD = "value"

    const val ROW_ID_FIELD = "\$rowId"
}

/**
 * The SDK doesn't need to write/read from local sqlite database.
 * This storage class is used for migrating events only.
 */
class DatabaseStorage(context: Context, databaseName: String) : SQLiteOpenHelper(
    context,
    databaseName,
    null,
    DatabaseConstants.DATABASE_VERSION
) {
    private var file: File = context.getDatabasePath(databaseName)
    var currentDbVersion: Int = DatabaseConstants.DATABASE_VERSION
        private set

    override fun onCreate(db: SQLiteDatabase) {
        throw NotImplementedError()
    }

    override fun onUpgrade(db: SQLiteDatabase?, oldVersion: Int, newVersion: Int) {
        currentDbVersion = oldVersion
    }

    private fun queryDb(
        db: SQLiteDatabase,
        table: String?,
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
            delete()
        } else {
            throw e
        }
    }

    private fun convertIfCursorWindowException(e: java.lang.RuntimeException) {
        val message = e.message
        if (!message.isNullOrEmpty() && (message.startsWith("Cursor window allocation of") || message.startsWith("Could not allocate CursorWindow"))) {
            throw CursorWindowAllocationException(message)
        } else {
            throw e
        }
    }

    private fun delete() {
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
            cursor = queryDb(
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
                "read events from $table failed: ${e.message}"
            )
            delete()
        } catch (e: StackOverflowError) {
            LogcatLogger.logger.error(
                "read events from $table failed: ${e.message}"
            )
            delete()
        } catch (e: IllegalStateException) { // put before Runtime since IllegalState extends
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

    private fun removeEventFromTable(table: String, rowId: Long) {
        try {
            val db = writableDatabase
            db.delete(
                table,
                "${DatabaseConstants.ID_FIELD} = ?",
                arrayOf(rowId.toString())
            )
        } catch (e: SQLiteException) {
            LogcatLogger.logger.error(
                "remove events from $table failed: ${e.message}"
            )
            delete()
        } catch (e: StackOverflowError) {
            LogcatLogger.logger.error(
                "remove events from $table failed: ${e.message}"
            )
            delete()
        } finally {
            close()
        }
    }

    @Synchronized
    fun getValue(key: String): String? {
        if (!file.exists()) {
            return null
        }

        var value: String? = null
        var cursor: Cursor? = null
        try {
            val db = readableDatabase
            cursor = queryDb(
                db,
                DatabaseConstants.STORE_TABLE_NAME,
                arrayOf(
                    DatabaseConstants.KEY_FIELD,
                    DatabaseConstants.VALUE_FIELD
                ),
                DatabaseConstants.KEY_FIELD + " = ?",
                arrayOf(key),
                null,
            )
            if (cursor!!.moveToFirst()) {
                value = cursor.getString(1)
            }
        } catch (e: SQLiteException) {
            LogcatLogger.logger.error(
                "getValue from ${DatabaseConstants.STORE_TABLE_NAME} failed: ${e.message}"
            )
            // Hard to recover from SQLiteExceptions, just start fresh
            delete()
        } catch (e: StackOverflowError) {
            LogcatLogger.logger.error(
                "getValue from ${DatabaseConstants.STORE_TABLE_NAME} failed: ${e.message}"
            )
            // potential stack overflow error when getting database on custom Android versions
            delete()
        } catch (e: IllegalStateException) { // put before Runtime since IllegalState extends
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
        try {
            val db = writableDatabase
            db.delete(
                DatabaseConstants.STORE_TABLE_NAME,
                "${DatabaseConstants.KEY_FIELD} = ?",
                arrayOf(key)
            )
        } catch (e: SQLiteException) {
            LogcatLogger.logger.error(
                "remove value from ${DatabaseConstants.STORE_TABLE_NAME} failed: ${e.message}"
            )
            delete()
        } catch (e: StackOverflowError) {
            LogcatLogger.logger.error(
                "remove value from ${DatabaseConstants.STORE_TABLE_NAME} failed: ${e.message}"
            )
            delete()
        } finally {
            close()
        }
    }
}

class CursorWindowAllocationException(description: String?) :
    java.lang.RuntimeException(description)

class DatabaseStorageProvider {
    fun getStorage(amplitude: Amplitude): DatabaseStorage {
        val configuration = amplitude.configuration as com.amplitude.android.Configuration

        return DatabaseStorage(configuration.context, getDatabaseName(configuration.instanceName))
    }

    private fun getDatabaseName(instanceName: String?): String {
        val normalizedInstanceName = instanceName?.lowercase(Locale.getDefault())
        return if (normalizedInstanceName.isNullOrEmpty() || normalizedInstanceName == Configuration.DEFAULT_INSTANCE) DatabaseConstants.DATABASE_NAME else "${DatabaseConstants.DATABASE_NAME}_$normalizedInstanceName"
    }
}
