package com.amplitude.android.migration

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.amplitude.android.Amplitude
import com.amplitude.android.Configuration
import com.amplitude.core.Storage
import com.amplitude.core.utilities.ConsoleLoggerProvider
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.junit.Test
import org.junit.jupiter.api.Assertions
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

@RunWith(RobolectricTestRunner::class)
class RemnantDataMigrationTest {
    @Test
    fun `legacy data version 4 should be migrated`() {
        checkLegacyDataMigration("legacy_v4.sqlite", 4)
    }

    @Test
    fun `legacy data version 3 should be migrated`() {
        checkLegacyDataMigration("legacy_v3.sqlite", 3)
    }

    @Test
    fun `missing legacy data should not fail`() {
        checkLegacyDataMigration("dummy.sqlite", 0)
    }

    @Test
    fun `no data should be migrated if migrateLegacyData=false`() {
        checkLegacyDataMigration("legacy_v4.sqlite", 4, false)
    }

    @Test
    fun `not database file should not fail`() {
        checkLegacyDataMigration("not_db_file", 0)
    }

    private fun checkLegacyDataMigration(legacyDbName: String, dbVersion: Int, migrateLegacyData: Boolean = true) {
        val context = ApplicationProvider.getApplicationContext<Context>()

        val instanceName = "legacy_v${dbVersion}_$migrateLegacyData"
        val dbPath = context.getDatabasePath("com.amplitude.api_$instanceName")
        val inputStream = javaClass.classLoader?.getResourceAsStream(legacyDbName)
        if (inputStream != null) {
            copyStream(inputStream, dbPath)
        }
        val isValidDbFile = inputStream != null && legacyDbName != "not_db_file"

        val amplitude = Amplitude(
            Configuration(
                "test-api-key",
                context,
                instanceName = instanceName,
                migrateLegacyData = migrateLegacyData,
                loggerProvider = ConsoleLoggerProvider()
            )
        )

        // Check migrated data after RemnantEventsMigrationPlugin
        val deviceId = "22833898-c487-4536-b213-40f207abdce0R"
        val userId = "android-kotlin-sample-user-legacy"
        runBlocking {
            amplitude.isBuilt.await()

            val identity = amplitude.idContainer.identityManager.getIdentity()
            if (isValidDbFile && migrateLegacyData) {
                Assertions.assertEquals(deviceId, identity.deviceId)
                Assertions.assertEquals(userId, identity.userId)
            } else {
                Assertions.assertNotEquals(deviceId, identity.deviceId)
                Assertions.assertNotEquals(userId, identity.userId)
            }

            amplitude.storage.rollover()
            amplitude.identifyInterceptStorage.rollover()

            if (isValidDbFile && migrateLegacyData) {
                Assertions.assertEquals(1684219150343, amplitude.storage.read(Storage.Constants.PREVIOUS_SESSION_ID)?.toLongOrNull())
                Assertions.assertEquals(1684219150344, amplitude.storage.read(Storage.Constants.LAST_EVENT_TIME)?.toLongOrNull())
                Assertions.assertEquals(2, amplitude.storage.read(Storage.Constants.LAST_EVENT_ID)?.toLongOrNull())
            } else {
                Assertions.assertNull(amplitude.storage.read(Storage.Constants.PREVIOUS_SESSION_ID)?.toLongOrNull())
                Assertions.assertNull(amplitude.storage.read(Storage.Constants.LAST_EVENT_TIME)?.toLongOrNull())
                Assertions.assertNull(amplitude.storage.read(Storage.Constants.LAST_EVENT_ID)?.toLongOrNull())
            }

            val eventsData = amplitude.storage.readEventsContent()
            if (isValidDbFile && migrateLegacyData) {
                val jsonEvents = JSONArray()
                for (eventsPath in eventsData) {
                    val eventsString = amplitude.storage.getEventsString(eventsPath)
                    val events = JSONArray(eventsString)
                    for (i in 0 until events.length()) {
                        jsonEvents.put(events.get(i))
                    }
                }
                Assertions.assertEquals(4, jsonEvents.length())
                val event1 = jsonEvents.getJSONObject(0)
                Assertions.assertEquals("\$identify", event1.getString("event_type"))
                Assertions.assertEquals(1684219150343, event1.getLong("time"))
                Assertions.assertEquals("be09ecba-83f7-444a-aba0-fe1f529a3716", event1.getString("insert_id"))
                Assertions.assertEquals("amplitude-android/2.39.3-SNAPSHOT", event1.getString("library"))
                Assertions.assertEquals(deviceId, event1.getString("device_id"))
                Assertions.assertEquals(userId, event1.getString("user_id"))
                val event2 = jsonEvents.getJSONObject(1)
                Assertions.assertEquals("\$identify", event2.getString("event_type"))
                Assertions.assertEquals(1684219150344, event2.getLong("time"))
                Assertions.assertEquals("0894387e-e923-423b-9feb-086ba8cb2cfa", event2.getString("insert_id"))
                Assertions.assertEquals("amplitude-android/2.39.3-SNAPSHOT", event2.getString("library"))
                Assertions.assertEquals(deviceId, event2.getString("device_id"))
                Assertions.assertEquals(userId, event2.getString("user_id"))
                val event3 = jsonEvents.getJSONObject(2)
                Assertions.assertEquals("legacy event 1", event3.getString("event_type"))
                Assertions.assertEquals(1684219150354, event3.getLong("time"))
                Assertions.assertEquals("d6eff10b-9cd4-45d7-85cb-c81cb6cb8b2e", event3.getString("insert_id"))
                Assertions.assertEquals("amplitude-android/2.39.3-SNAPSHOT", event3.getString("library"))
                Assertions.assertEquals(deviceId, event3.getString("device_id"))
                Assertions.assertEquals(userId, event3.getString("user_id"))
                val event4 = jsonEvents.getJSONObject(3)
                Assertions.assertEquals("legacy event 2", event4.getString("event_type"))
                Assertions.assertEquals(1684219150355, event4.getLong("time"))
                Assertions.assertEquals("7b4c5c13-6fdc-4931-9ba1-e4efdf346ee0", event4.getString("insert_id"))
                Assertions.assertEquals("amplitude-android/2.39.3-SNAPSHOT", event4.getString("library"))
                Assertions.assertEquals(deviceId, event4.getString("device_id"))
                Assertions.assertEquals(userId, event4.getString("user_id"))
            } else {
                Assertions.assertEquals(0, eventsData.size)
            }

            val interceptedIdentifiesData = amplitude.identifyInterceptStorage.readEventsContent()
            if (isValidDbFile && dbVersion >= 4 && migrateLegacyData) {
                val jsonInterceptedIdentifies = JSONArray()
                for (eventsPath in interceptedIdentifiesData) {
                    val eventsString = amplitude.storage.getEventsString(eventsPath)
                    val events = JSONArray(eventsString)
                    for (i in 0 until events.length()) {
                        jsonInterceptedIdentifies.put(events.get(i))
                    }
                }
                Assertions.assertEquals(2, jsonInterceptedIdentifies.length())
                val intercepted1 = jsonInterceptedIdentifies.getJSONObject(0)
                Assertions.assertEquals("\$identify", intercepted1.getString("event_type"))
                Assertions.assertEquals(1684219150358, intercepted1.getLong("time"))
                Assertions.assertEquals("1a14d057-8a12-40bb-8217-2d62dd08a525", intercepted1.getString("insert_id"))
                Assertions.assertEquals("amplitude-android/2.39.3-SNAPSHOT", intercepted1.getString("library"))
                Assertions.assertEquals(deviceId, intercepted1.getString("device_id"))
                Assertions.assertEquals(userId, intercepted1.getString("user_id"))
                val intercepted2 = jsonInterceptedIdentifies.getJSONObject(1)
                Assertions.assertEquals("\$identify", intercepted2.getString("event_type"))
                Assertions.assertEquals(1684219150359, intercepted2.getLong("time"))
                Assertions.assertEquals("b115a299-4cc6-495b-8e4e-c2ce6f244be9", intercepted2.getString("insert_id"))
                Assertions.assertEquals("amplitude-android/2.39.3-SNAPSHOT", intercepted2.getString("library"))
                Assertions.assertEquals(deviceId, intercepted2.getString("device_id"))
                Assertions.assertEquals(userId, intercepted2.getString("user_id"))
            } else {
                Assertions.assertEquals(0, interceptedIdentifiesData.size)
            }
        }

        // Check legacy sqlite data are cleaned
        val databaseStorage = DatabaseStorageProvider.getStorage(amplitude)
        if (migrateLegacyData) {
            Assertions.assertEquals(0, databaseStorage.readEventsContent().size)
            Assertions.assertEquals(0, databaseStorage.readIdentifiesContent().size)
            Assertions.assertEquals(0, databaseStorage.readInterceptedIdentifiesContent().size)
        } else {
            Assertions.assertEquals(2, databaseStorage.readEventsContent().size)
            Assertions.assertEquals(2, databaseStorage.readIdentifiesContent().size)
            Assertions.assertEquals(2, databaseStorage.readInterceptedIdentifiesContent().size)
        }

        // User/device id should not be cleaned.
        if (isValidDbFile) {
            Assertions.assertEquals(deviceId, databaseStorage.getValue(RemnantDataMigration.DEVICE_ID_KEY))
            Assertions.assertEquals(userId, databaseStorage.getValue(RemnantDataMigration.USER_ID_KEY))
        } else {
            Assertions.assertNull(databaseStorage.getValue(RemnantDataMigration.DEVICE_ID_KEY))
            Assertions.assertNull(databaseStorage.getValue(RemnantDataMigration.USER_ID_KEY))
        }
    }

    private fun copyStream(src: InputStream, dst: File) {
        src.use { srcStream ->
            FileOutputStream(dst).use { dstStream ->
                // Transfer bytes from in to out
                val buf = ByteArray(1024)
                var len: Int
                while (srcStream.read(buf).also { len = it } > 0) {
                    dstStream.write(buf, 0, len)
                }
            }
        }
    }
}
