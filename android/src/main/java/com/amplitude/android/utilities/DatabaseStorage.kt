package com.amplitude.android.utilities

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import android.database.sqlite.SQLiteOpenHelper
import com.amplitude.common.android.LogcatLogger
import com.amplitude.core.*
import com.amplitude.core.events.BaseEvent
import com.amplitude.core.platform.EventPipeline
import com.amplitude.core.utilities.ResponseHandler
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import org.json.JSONObject
import java.io.File
import java.util.*

object DatabaseConstants {
    const val DATABASE_NAME = "com.amplitude.api"
    const val DATABASE_VERSION = 3
    const val EVENT_TABLE_NAME = "events"
    const val ID_FIELD = "id"
    const val EVENT_FIELD = "event"
}

class DatabaseStorage(context: Context) : Storage, SQLiteOpenHelper(
    context,
    DatabaseConstants.DATABASE_NAME,
    null,
    DatabaseConstants.DATABASE_VERSION
) {
    companion object {
        val TAG: String = DatabaseStorage::class.java.name
    }

    private var file: File? = context.getDatabasePath(DatabaseConstants.DATABASE_NAME)

    override fun onCreate(db: SQLiteDatabase) {
    }

    override fun onUpgrade(db: SQLiteDatabase?, oldVersion: Int, newVersion: Int) {
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
        if (!message.isNullOrEmpty() && (message.startsWith("Cursor window allocation of") || message.startsWith(
                "Could not allocate CursorWindow"
            ))
        ) {
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
            LogcatLogger.logger.error(String.format("deletion failed with error: %s", e))
        }
    }

    override suspend fun writeEvent(event: BaseEvent) {
        throw NotImplementedError()
    }

    override suspend fun write(key: Storage.Constants, value: String) {
        throw NotImplementedError()
    }

    override suspend fun rollover() {
        throw NotImplementedError()
    }

    override fun read(key: Storage.Constants): String? {
        throw NotImplementedError()
    }

    override fun readEventsContent(): List<Any> {
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
                String.format(
                    "getEvents from %s failed with error: %s",
                    DatabaseConstants.EVENT_TABLE_NAME,
                    e
                )
            )
            delete()
        } catch (e: StackOverflowError) {
            LogcatLogger.logger.error(
                String.format(
                    "getEvents from %s failed with error: %s",
                    DatabaseConstants.EVENT_TABLE_NAME,
                    e
                )
            )
            delete()
        } catch (e: IllegalStateException) {  // put before Runtime since IllegalState extends
            handleIfCursorRowTooLargeException(e)
        } catch (e: RuntimeException) {
            convertIfCursorWindowException(e)
        } finally {
            cursor?.close()
            close()
        }
        return events
    }

    override fun getEventsString(content: Any): String {
        throw NotImplementedError()
    }

    override fun getResponseHandler(
        eventPipeline: EventPipeline,
        configuration: Configuration,
        scope: CoroutineScope,
        dispatcher: CoroutineDispatcher,
        events: Any,
        eventsString: String
    ): ResponseHandler {
        throw NotImplementedError()
    }
}

class CursorWindowAllocationException(description: String?) :
    java.lang.RuntimeException(description)

class DatabaseStorageProvider : StorageProvider {
    override fun getStorage(amplitude: Amplitude): DatabaseStorage {
        val configuration = amplitude.configuration as com.amplitude.android.Configuration
        return DatabaseStorage(configuration.context)
    }
}
