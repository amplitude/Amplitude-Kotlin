package com.amplitude.android.migration

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.amplitude.MainDispatcherRule
import com.amplitude.android.Amplitude
import com.amplitude.android.Configuration
import com.amplitude.android.storage.AndroidStorageContextV1
import com.amplitude.android.storage.AndroidStorageContextV2
import com.amplitude.core.Storage
import com.amplitude.core.events.BaseEvent
import com.amplitude.core.utilities.ConsoleLoggerProvider
import com.amplitude.core.utilities.toEvents
import com.amplitude.id.IdentityContainer
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.junit.Assert
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
class MigrationManagerTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    lateinit var context: Context

    private val legacyUserId: String = "android-kotlin-sample-user-legacy"
    private val legacyDeviceId = "22833898-c487-4536-b213-40f207abdce0R"

    @Before
    fun init() {
        context = ApplicationProvider.getApplicationContext()
        IdentityContainer.clearInstanceCache()
    }

    @Test
    fun `test migration from legacy SDK`() {
        val databaseName = "legacy_v4.sqlite"
        val instanceName = "test-instance-legacy"
        val apiKey = "test-api-key"
        val inputStream = javaClass.classLoader?.getResourceAsStream(databaseName)!!
        val dbPath = context.getDatabasePath("com.amplitude.api_$instanceName")
        copyStream(inputStream, dbPath)

        val amplitude = waitAndGetBuiltAmplitudeInstance(instanceName, apiKey)
        runBlocking {
            Assert.assertEquals(legacyDeviceId, amplitude.getDeviceId())
            Assert.assertEquals(legacyUserId, amplitude.getUserId())

            val events = getEventsFromStorage(amplitude.storage)
            Assert.assertEquals(4, events.size)
        }
    }

    @Test
    fun `test migration from legacy SDK with different instance names should not migrate data`() {
        val databaseName = "legacy_v4.sqlite"
        val instanceName = "test-instance"
        val apiKey = "test-api-key"
        val inputStream = javaClass.classLoader?.getResourceAsStream(databaseName)!!
        val dbPath = context.getDatabasePath("com.amplitude.api_$instanceName")
        copyStream(inputStream, dbPath)

        val differentAmplitudeInstance =
            waitAndGetBuiltAmplitudeInstance("different-instance name", apiKey)
        runBlocking {
            Assert.assertNotNull(legacyDeviceId, differentAmplitudeInstance.getDeviceId())
            Assert.assertNull(differentAmplitudeInstance.getUserId())

            val events = getEventsFromStorage(differentAmplitudeInstance.storage)
            Assert.assertEquals(0, events.size)
        }

        val correctAmplitudeInstance = waitAndGetBuiltAmplitudeInstance(instanceName, apiKey)
        runBlocking {
            Assert.assertEquals(legacyDeviceId, correctAmplitudeInstance.getDeviceId())
            Assert.assertEquals(legacyUserId, correctAmplitudeInstance.getUserId())

            val events = getEventsFromStorage(correctAmplitudeInstance.storage)
            Assert.assertEquals(4, events.size)
        }
    }

    @Test
    fun `test migration from api key based storage`() {
        var amplitude = waitAndGetBuiltAmplitudeInstance("test-instance", "test-api-key")
        cleanupMigrationVersionMarker(amplitude.configuration as Configuration)

        // Clear this because the next amplitude instance will just reuse the old instance of id container
        IdentityContainer.clearInstanceCache()

        val storageContextV1 =
            AndroidStorageContextV1(amplitude, amplitude.configuration as Configuration)
        val events = listOf(getBaseEvent("test_event"), getBaseEvent("test_event2"))
        val identifies =
            listOf(
                getBaseEvent("\$identify", mutableMapOf("key1" to "value1", "key2" to "value2")),
                getBaseEvent("\$identify", mutableMapOf("key1" to "value1", "key2" to "value2")),
            )

        // Populate legacy data
        runBlocking {
            storageContextV1.identityStorage.saveUserId(legacyUserId)
            storageContextV1.identityStorage.saveDeviceId(legacyDeviceId)
            events.forEach {
                storageContextV1.eventsStorage.writeEvent(it)
            }
            identifies.forEach {
                storageContextV1.identifyInterceptStorage.writeEvent(it)
            }
        }

        amplitude = waitAndGetBuiltAmplitudeInstance("test-instance", "test-api-key")
        runBlocking {
            val eventsFromStorage = getEventsFromStorage(amplitude.storage)
            Assert.assertEquals(2, eventsFromStorage.size)
            assertEvents(events, eventsFromStorage)

            val identifiesFromStorage = getEventsFromStorage(amplitude.identifyInterceptStorage)
            Assert.assertEquals(2, identifiesFromStorage.size)
            assertEvents(identifies, identifiesFromStorage)

            Assert.assertEquals(legacyUserId, amplitude.getUserId())
            Assert.assertEquals(legacyDeviceId, amplitude.getDeviceId())
        }
    }

    @Test
    fun `test migration from api key based storage with different instance name`() {
        var amplitude = waitAndGetBuiltAmplitudeInstance("test-instance", "test-api-key")
        cleanupMigrationVersionMarker(amplitude.configuration as Configuration)

        // Clear this because the next amplitude instance will just reuse the old instance of id container
        IdentityContainer.clearInstanceCache()

        val storageContextV1 =
            AndroidStorageContextV1(amplitude, amplitude.configuration as Configuration)
        val events = listOf(getBaseEvent("test_event"), getBaseEvent("test_event2"))
        val identifies =
            listOf(
                getBaseEvent("\$identify", mutableMapOf("key1" to "value1", "key2" to "value2")),
                getBaseEvent("\$identify", mutableMapOf("key1" to "value1", "key2" to "value2")),
            )

        // Populate legacy data
        runBlocking {
            storageContextV1.identityStorage.saveUserId(legacyUserId)
            storageContextV1.identityStorage.saveDeviceId(legacyDeviceId)
            events.forEach {
                storageContextV1.eventsStorage.writeEvent(it)
            }
            identifies.forEach {
                storageContextV1.identifyInterceptStorage.writeEvent(it)
            }
        }

        amplitude = waitAndGetBuiltAmplitudeInstance("test-instance-2", "test-api-key-2")
        runBlocking {
            val eventsFromStorage = getEventsFromStorage(amplitude.storage)
            Assert.assertEquals(0, eventsFromStorage.size)

            val identifiesFromStorage = getEventsFromStorage(amplitude.identifyInterceptStorage)
            Assert.assertEquals(0, identifiesFromStorage.size)

            Assert.assertNotEquals(legacyUserId, amplitude.getUserId())
            Assert.assertNotEquals(legacyDeviceId, amplitude.getDeviceId())
        }
    }

    @Test
    fun `test migration from instance name based storage`() {
        var amplitude = waitAndGetBuiltAmplitudeInstance(null, "test-api-key")
        cleanupMigrationVersionMarker(amplitude.configuration as Configuration)

        // Clear this because the next amplitude instance will just reuse the old instance of id container
        IdentityContainer.clearInstanceCache()

        val storageContextV2 =
            AndroidStorageContextV2(amplitude, amplitude.configuration as Configuration)
        val events = listOf(getBaseEvent("test_event"), getBaseEvent("test_event2"))
        val identifies =
            listOf(
                getBaseEvent("\$identify", mutableMapOf("key1" to "value1", "key2" to "value2")),
                getBaseEvent("\$identify", mutableMapOf("key1" to "value1", "key2" to "value2")),
            )

        // Populate legacy data
        runBlocking {
            storageContextV2.identityStorage.saveUserId(legacyUserId)
            storageContextV2.identityStorage.saveDeviceId(legacyDeviceId)
            events.forEach {
                storageContextV2.eventsStorage.writeEvent(it)
            }
            identifies.forEach {
                storageContextV2.identifyInterceptStorage.writeEvent(it)
            }
        }

        amplitude = waitAndGetBuiltAmplitudeInstance(null, "test-api-key")
        runBlocking {
            val eventsFromStorage = getEventsFromStorage(amplitude.storage)
            Assert.assertEquals(2, eventsFromStorage.size)
            assertEvents(events, eventsFromStorage)

            val identifiesFromStorage = getEventsFromStorage(amplitude.identifyInterceptStorage)
            Assert.assertEquals(2, identifiesFromStorage.size)
            assertEvents(identifies, identifiesFromStorage)

            Assert.assertEquals(legacyUserId, amplitude.getUserId())
            Assert.assertEquals(legacyDeviceId, amplitude.getDeviceId())
        }
    }

    @Test
    fun `test migration from instance name based storage for non default instances`() {
        var amplitude = waitAndGetBuiltAmplitudeInstance("test-instance", "test-api-key")
        cleanupMigrationVersionMarker(amplitude.configuration as Configuration)

        // Clear this because the next amplitude instance will just reuse the old instance of id container
        IdentityContainer.clearInstanceCache()

        val storageContextV2 =
            AndroidStorageContextV2(amplitude, amplitude.configuration as Configuration)
        val events = listOf(getBaseEvent("test_event"), getBaseEvent("test_event2"))
        val identifies =
            listOf(
                getBaseEvent("\$identify", mutableMapOf("key1" to "value1", "key2" to "value2")),
                getBaseEvent("\$identify", mutableMapOf("key1" to "value1", "key2" to "value2")),
            )

        // Populate legacy data
        runBlocking {
            storageContextV2.identityStorage.saveUserId(legacyUserId)
            storageContextV2.identityStorage.saveDeviceId(legacyDeviceId)
            events.forEach {
                storageContextV2.eventsStorage.writeEvent(it)
            }
            identifies.forEach {
                storageContextV2.identifyInterceptStorage.writeEvent(it)
            }
        }

        amplitude = waitAndGetBuiltAmplitudeInstance("test-instance", "test-api-key")
        runBlocking {
            // since this is a non default instance name, we shouldn't have migrated events and
            // ident data
            val eventsFromStorage = getEventsFromStorage(amplitude.storage)
            Assert.assertEquals(0, eventsFromStorage.size)

            val identifiesFromStorage = getEventsFromStorage(amplitude.identifyInterceptStorage)
            Assert.assertEquals(0, identifiesFromStorage.size)

            Assert.assertEquals(legacyUserId, amplitude.getUserId())
            Assert.assertEquals(legacyDeviceId, amplitude.getDeviceId())
        }
    }

    private fun assertEvents(
        original: List<BaseEvent>,
        new: List<BaseEvent>,
    ) {
        Assert.assertEquals(original.size, new.size)
        for (i in original.indices) {
            Assert.assertEquals(original[i].eventType, new[i].eventType)
            Assert.assertEquals(original[i].insertId, new[i].insertId)
            Assert.assertEquals(original[i].userProperties, new[i].userProperties)
        }
    }

    private fun getBaseEvent(
        eventType: String,
        userProperties: MutableMap<String, Any?> = mutableMapOf(),
    ): BaseEvent {
        return BaseEvent().apply {
            this.eventType = eventType
            this.insertId = UUID.randomUUID().toString()
            this.userProperties = userProperties
        }
    }

    private fun waitAndGetBuiltAmplitudeInstance(
        instanceName: String?,
        apiKey: String,
    ): Amplitude {
        val amplitude =
            Amplitude(
                generateConfiguration(instanceName, apiKey),
            )
        runBlocking {
            amplitude.isBuilt.await()
        }
        return amplitude
    }

    private fun generateConfiguration(
        instanceName: String?,
        apiKey: String,
    ): Configuration {
        return Configuration(
            apiKey,
            context,
            instanceName = instanceName ?: com.amplitude.core.Configuration.DEFAULT_INSTANCE,
            loggerProvider = ConsoleLoggerProvider(),
        )
    }

    private fun copyStream(
        src: InputStream,
        dst: File,
    ) {
        dst.parentFile?.mkdirs()
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

    private fun cleanupMigrationVersionMarker(configuration: Configuration) {
        val sharedPreferences =
            configuration.context.getSharedPreferences(
                "amplitude-android-${configuration.instanceName}",
                Context.MODE_PRIVATE,
            )
        sharedPreferences.edit().remove("storage_version").apply()
    }

    private suspend fun getEventsFromStorage(storage: Storage): List<BaseEvent> {
        val files = storage.readEventsContent() as List<String>
        val events = mutableListOf<BaseEvent>()
        for (file in files) {
            val content = JSONArray(storage.getEventsString(file))
            events.addAll(content.toEvents())
        }
        return events
    }
}
