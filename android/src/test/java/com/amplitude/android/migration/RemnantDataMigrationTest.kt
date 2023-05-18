package com.amplitude.android.migration

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.amplitude.android.Amplitude
import com.amplitude.android.Configuration
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

    private fun checkLegacyDataMigration(legacyDbName: String, dbVersion: Int) {
        val context = ApplicationProvider.getApplicationContext<Context>()

        val instanceName = "legacy_v$dbVersion"
        val dbPath = context.getDatabasePath("com.amplitude.api_$instanceName")
        val inputStream = javaClass.classLoader?.getResourceAsStream(legacyDbName)
        if (inputStream != null) {
            copyStream(inputStream, dbPath)
        }

        val migration = RemnantDataMigration()

        val amplitude = Amplitude(
            Configuration(
                "test-api-key",
                context,
                instanceName = instanceName,
                initializers = listOf(migration)
            )
        )

        // Check migrated data after RemnantEventsMigrationPlugin
        runBlocking {
            amplitude.build().await()

            val identity = amplitude.idContainer.identityManager.getIdentity()
            if (inputStream != null) {
                Assertions.assertEquals("22833898-c487-4536-b213-40f207abdce0R", identity.deviceId)
                Assertions.assertEquals("android-kotlin-sample-user-legacy", identity.userId)
            }

            amplitude.storage.rollover()
            amplitude.identifyInterceptStorage.rollover()

            val eventsData = amplitude.storage.readEventsContent()
            if (inputStream != null) {
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
                Assertions.assertEquals(1684219150343, event1.getLong("timestamp"))
                val event2 = jsonEvents.getJSONObject(1)
                Assertions.assertEquals("\$identify", event2.getString("event_type"))
                Assertions.assertEquals(1684219150344, event2.getLong("timestamp"))
                val event3 = jsonEvents.getJSONObject(2)
                Assertions.assertEquals("legacy event 1", event3.getString("event_type"))
                Assertions.assertEquals(1684219150354, event3.getLong("timestamp"))
                val event4 = jsonEvents.getJSONObject(3)
                Assertions.assertEquals("legacy event 2", event4.getString("event_type"))
                Assertions.assertEquals(1684219150355, event4.getLong("timestamp"))
            } else {
                Assertions.assertEquals(0, eventsData.size)
            }

            val interceptedIdentifiesData = amplitude.identifyInterceptStorage.readEventsContent()
            if (inputStream != null && dbVersion >= 4) {
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
                Assertions.assertEquals(1684219150358, intercepted1.getLong("timestamp"))
                val intercepted2 = jsonInterceptedIdentifies.getJSONObject(1)
                Assertions.assertEquals("\$identify", intercepted2.getString("event_type"))
                Assertions.assertEquals(1684219150359, intercepted2.getLong("timestamp"))
            } else {
                Assertions.assertEquals(0, interceptedIdentifiesData.size)
            }
        }

        // Check legacy sqlite data are cleaned
        val databaseStorage = migration.databaseStorage
        Assertions.assertNull(databaseStorage.getValue(RemnantDataMigration.DEVICE_ID_KEY))
        Assertions.assertNull(databaseStorage.getValue(RemnantDataMigration.USER_ID_KEY))
        Assertions.assertEquals(0, databaseStorage.readEventsContent().size)
        Assertions.assertEquals(0, databaseStorage.readIdentifiesContent().size)
        Assertions.assertEquals(0, databaseStorage.readInterceptedIdentifiesContent().size)
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