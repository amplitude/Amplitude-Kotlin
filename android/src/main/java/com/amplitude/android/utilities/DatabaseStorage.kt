package com.amplitude.android.utilities

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import android.database.sqlite.SQLiteOpenHelper
import com.amplitude.common.android.LogcatLogger
import com.amplitude.core.Amplitude
import org.json.JSONObject
import java.io.File
import java.util.LinkedList

/**
 * Store the database related constants.
 * Align with com/amplitude/api/DatabaseHelper.java in previous SDK.
 */
object DatabaseConstants {
    const val DATABASE_NAME = "com.amplitude.api"
    const val DATABASE_VERSION = 3
    const val EVENT_TABLE_NAME = "events"
    const val ID_FIELD = "id"
    const val EVENT_FIELD = "event"
}

/**
 * The SDK doesn't need to write/read from local sqlite database.
 * This storage class is used for migrating events only.
 */
class DatabaseStorage(context: Context) : SQLiteOpenHelper(
    context,
    DatabaseConstants.DATABASE_NAME,
    null,
    DatabaseConstants.DATABASE_VERSION
) {
    private var file: File? = context.getDatabasePath(DatabaseConstants.DATABASE_NAME)

    override fun onCreate(db: SQLiteDatabase) {
        throw NotImplementedError()
    }

    override fun onUpgrade(db: SQLiteDatabase?, oldVersion: Int, newVersion: Int) {
        throw NotImplementedError()
    }

    private fun queryDb(
        db: SQLiteDatabase,
        table: String?,
        columns: Array<String?>?,
        selection: String?,
        selectionArgs: Array<String?>?,
        groupBy: String?,
        having: String?,
        orderBy: String?,
        limit: String?
    ): Cursor? {
        return db.query(table, columns, selection, selectionArgs, groupBy, having, orderBy, limit)
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
            file?.delete()
        } catch (e: Exception) {
            LogcatLogger.logger.error("deletion failed: ${e.message}")
        }
    }

    @Synchronized
    fun readEventsContent(): List<Any> {
        val events: MutableList<JSONObject> = LinkedList()
        var cursor: Cursor? = null
        try {
            val db = readableDatabase
            cursor = queryDb(
                db,
                DatabaseConstants.EVENT_TABLE_NAME,
                arrayOf(DatabaseConstants.ID_FIELD, DatabaseConstants.EVENT_FIELD),
                null,
                null,
                null,
                null,
                DatabaseConstants.ID_FIELD + " ASC",
                null
            )
            while (cursor!!.moveToNext()) {
                val eventId = cursor.getLong(0)
                val event = cursor.getString(1)
                if (event.isNullOrEmpty()) {
                    continue
                }
                val obj = JSONObject(event)
                obj.put("event_id", eventId)
                events.add(obj)
            }
        } catch (e: SQLiteException) {
            LogcatLogger.logger.error(
                "read events from ${DatabaseConstants.EVENT_TABLE_NAME} failed: ${e.message}"
            )
            delete()
        } catch (e: StackOverflowError) {
            LogcatLogger.logger.error(
                "read events from ${DatabaseConstants.EVENT_TABLE_NAME} failed: ${e.message}"
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
    fun removeEvents(maxId: Long) {
        try {
            val db = writableDatabase
            db.delete(
                DatabaseConstants.EVENT_TABLE_NAME,
                DatabaseConstants.ID_FIELD + " <= " + maxId, null
            )
        } catch (e: SQLiteException) {
            LogcatLogger.logger.error(
                "remove events from ${DatabaseConstants.EVENT_TABLE_NAME} failed: ${e.message}"
            )
            delete()
        } catch (e: StackOverflowError) {
            LogcatLogger.logger.error(
                "remove events from ${DatabaseConstants.EVENT_TABLE_NAME} failed: ${e.message}"
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
    object Singleton {
        lateinit var instance: DatabaseStorage
        fun isInstanceInitialized() = ::instance.isInitialized
    }

    fun getStorage(amplitude: Amplitude): DatabaseStorage {
        if (!Singleton.isInstanceInitialized()) {
            val configuration = amplitude.configuration as com.amplitude.android.Configuration
            Singleton.instance = DatabaseStorage(configuration.context)
        }
        return Singleton.instance
    }
}
