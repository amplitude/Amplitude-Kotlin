package com.amplitude.android.utilities

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import com.amplitude.android.Configuration
import com.amplitude.core.Amplitude
import com.amplitude.core.Storage
import com.amplitude.core.platform.EventPipeline
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.spyk
import io.mockk.verify
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import org.json.JSONObject
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class DatabaseStorageTest {
    var context: Context? = null
    var databaseStorage: DatabaseStorage? = null
    var db: SQLiteDatabase? = null

    @BeforeEach
    fun setUp() {
        context = mockk(relaxed = true)
        databaseStorage = DatabaseStorage(context!!)
        db = mockk()
    }

    @Test
    fun databaseStorage_onCreate_throwsNotImplementedError() {
        Assertions.assertThrows(NotImplementedError::class.java) {
            databaseStorage?.onCreate(db!!)
        }
    }

    @Test
    fun databaseStorage_onUpgrade_throwsNotImplementedError() {
        Assertions.assertThrows(NotImplementedError::class.java) {
            databaseStorage?.onUpgrade(db, 1, 2)
        }
    }

    @Test
    fun databaseStorage_read_throwsNotImplementedError() {
        Assertions.assertThrows(NotImplementedError::class.java) {
            databaseStorage?.read(Storage.Constants.Events)
        }
    }

    @Test
    fun databaseStorage_getEventsString_throwsNotImplementedError() {
        Assertions.assertThrows(NotImplementedError::class.java) {
            databaseStorage?.getEventsString("any")
        }
    }

    @Test
    fun databaseStorage_getResponseHandler_throwsNotImplementedError() {
        Assertions.assertThrows(NotImplementedError::class.java) {
            databaseStorage?.getResponseHandler(
                mockk<EventPipeline>(),
                mockk<com.amplitude.core.Configuration>(),
                mockk<CoroutineScope>(),
                mockk<CoroutineDispatcher>(),
                mockk(),
                "event"
            )
        }
    }

    @Test
    fun databaseStorage_readEventsContent_returnsEventsContent() {
        val mockedCursor = mockk<Cursor>()
        every { mockedCursor.moveToNext() } returnsMany listOf(true, true, false)
        every { mockedCursor.getLong(0) } returnsMany listOf(1, 2)
        every { mockedCursor.getString(1) } returnsMany listOf("json content 1", "json content 2")
        every { mockedCursor.close() } returns Unit

        mockkConstructor(JSONObject::class)
        every { anyConstructed<JSONObject>().put(any(), any<Long>()) } returns JSONObject()
        every { anyConstructed<JSONObject>().get("event_id") } returnsMany listOf(1, 2)

        val mockedDatabaseStorage = spyk(DatabaseStorage(context!!), recordPrivateCalls = true)
        every {
            mockedDatabaseStorage["queryDb"](
                any<SQLiteDatabase>(),
                any<String>(),
                any<Array<String?>>(),
                any<String>(),
                any<Array<String?>>(),
                any<String>(),
                any<String>(),
                any<String>(),
                any<String>(),
            )
        } returns mockedCursor
        every { mockedDatabaseStorage.close() } answers { nothing }
        every { mockedDatabaseStorage.readableDatabase } returns db

        val events = mockedDatabaseStorage.readEventsContent()
        Assertions.assertEquals(events.size, 2)
        Assertions.assertEquals((events[0] as JSONObject).get("event_id"), 1)
        Assertions.assertEquals((events[1] as JSONObject).get("event_id"), 2)
    }

    @Test
    fun databaseStorage_removeEvents_returnsEventsContent() {
        val mockedDatabaseStorage = spyk(DatabaseStorage(context!!), recordPrivateCalls = true)
        every { mockedDatabaseStorage.close() } answers { nothing }
        every { mockedDatabaseStorage.writableDatabase } returns db
        every { db!!.delete(any(), any(), null) } returns 2
        mockedDatabaseStorage.removeEvents(2)
        verify(exactly = 1) { db!!.delete(any(), any(), null) }
    }
}

class DatabaseStorageProviderTest {
    @Test
    fun databaseStorageProvider_returnsSingletonInstance() {
        val amplitude = mockk<Amplitude>(relaxed = true)
        every { amplitude.configuration } returns mockk<Configuration>(relaxed = true)
        val instance1 = DatabaseStorageProvider().getStorage(amplitude)
        val instance2 = DatabaseStorageProvider().getStorage(amplitude)
        // compare reference, not data
        Assertions.assertTrue(instance1 === instance2)
    }
}
