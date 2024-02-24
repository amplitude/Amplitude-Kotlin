package com.amplitude.android.utilities

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.amplitude.android.events.BaseEvent
import com.amplitude.common.jvm.ConsoleLogger
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class AndroidStorageTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun `test write event and read`() {
        val logger = ConsoleLogger()
        val storageKey = "storageKey"
        val storage = AndroidStorage(context, storageKey, logger, "test")

        runBlocking {
            storage.writeEvent(createEvent("test1"))
            storage.writeEvent(createEvent("test2"))
            storage.rollover()
            storage.writeEvent(createEvent("test3"))
            storage.writeEvent(createEvent("test4"))
            storage.rollover()
        }

        runBlocking {
            val eventsData = storage.readEventsContent()
            eventsData.withIndex().forEach { (index, filePath) ->
                val eventsString = storage.getEventsString(filePath)
                val events = JSONArray(eventsString)
                assertEquals(2, events.length())
                assertEquals("test${index * 2 + 1}", events.getJSONObject(0).getString("event_type"))
                assertEquals("test${index * 2 + 2}", events.getJSONObject(1).getString("event_type"))
            }
        }
    }

    @Test
    fun `could handle earlier version of event files`() {
        val logger = ConsoleLogger()
        val storageKey = "storageKey"
        val prefix = "test"
        createEarlierVersionEventFiles(prefix)
        val storage = AndroidStorage(context, storageKey, logger, prefix)

        runBlocking {
            val eventsData = storage.readEventsContent()
            eventsData.withIndex().forEach { (index, filePath) ->
                val eventsString = storage.getEventsString(filePath)
                val events = JSONArray(eventsString)
                if (index == 4) {
                    assertEquals(1, events.length())
                    assertEquals("test9", events.getJSONObject(0).getString("event_type"))
                } else {
                    assertEquals(2, events.length())
                    assertEquals(
                        "test${index * 2 + 1}",
                        events.getJSONObject(0).getString("event_type"),
                    )
                    assertEquals(
                        "test${index * 2 + 2}",
                        events.getJSONObject(1).getString("event_type"),
                    )
                }
            }
        }
    }

    @Test
    fun `handle earlier and new events`() {
        val logger = ConsoleLogger()
        val storageKey = "storageKey"
        val prefix = "test"
        createEarlierVersionEventFiles(prefix)
        val storage = AndroidStorage(context, storageKey, logger, prefix)

        runBlocking {
            storage.writeEvent(createEvent("test13"))
            storage.writeEvent(createEvent("test14"))
            storage.rollover()
        }

        var eventsCount = 0
        runBlocking {
            val eventsData = storage.readEventsContent()
            eventsData.withIndex().forEach { (_, filePath) ->
                val eventsString = storage.getEventsString(filePath)
                val events = JSONArray(eventsString)
                eventsCount += events.length()
            }
        }
        assertEquals(13, eventsCount)
    }

    @Test
    fun `concurrent writes to the same instance`() {
        val logger = ConsoleLogger()
        val storageKey = "storageKey"
        val storage = AndroidStorage(context, storageKey, logger, "test")

        runBlocking {
            val job1 =
                kotlinx.coroutines.GlobalScope.launch {
                    storage.writeEvent(createEvent("test1"))
                    storage.writeEvent(createEvent("test2"))
                    storage.rollover()
                }
            val job2 =
                kotlinx.coroutines.GlobalScope.launch {
                    storage.writeEvent(createEvent("test3"))
                    storage.writeEvent(createEvent("test4"))
                    storage.rollover()
                }
            val job3 =
                kotlinx.coroutines.GlobalScope.launch {
                    storage.writeEvent(createEvent("test5"))
                    storage.writeEvent(createEvent("test6"))
                    storage.rollover()
                }
            kotlinx.coroutines.joinAll(job1, job2, job3)
        }

        var eventsCount = 0
        runBlocking {
            val eventsData = storage.readEventsContent()
            eventsData.withIndex().forEach { (_index, filePath) ->
                val eventsString = storage.getEventsString(filePath)
                val events = JSONArray(eventsString)
                eventsCount += events.length()
            }
        }
        assertEquals(6, eventsCount)
    }

    @Test
    fun `concurrent write to two instances`() {
        val logger = ConsoleLogger()
        val storageKey = "storageKey"
        val prefix = "test"
        val storage1 = AndroidStorage(context, storageKey, logger, prefix)
        val storage2 = AndroidStorage(context, storageKey, logger, prefix)

        runBlocking {
            val job1 =
                kotlinx.coroutines.GlobalScope.launch {
                    storage1.writeEvent(createEvent("test1"))
                    storage1.writeEvent(createEvent("test2"))
                    storage1.rollover()
                }
            val job2 =
                kotlinx.coroutines.GlobalScope.launch {
                    storage2.writeEvent(createEvent("test3"))
                    storage2.writeEvent(createEvent("test4"))
                    storage2.rollover()
                }
            val job3 =
                kotlinx.coroutines.GlobalScope.launch {
                    storage1.writeEvent(createEvent("test5"))
                    storage1.writeEvent(createEvent("test6"))
                    storage1.rollover()
                }
            val job4 =
                kotlinx.coroutines.GlobalScope.launch {
                    storage2.writeEvent(createEvent("test7"))
                    storage2.writeEvent(createEvent("test8"))
                    storage2.rollover()
                }
            kotlinx.coroutines.joinAll(job1, job2, job3, job4)
        }

        var eventsCount = 0
        runBlocking {
            val eventsData1 = storage1.readEventsContent()
            eventsData1.withIndex().forEach { (_, filePath) ->
                val eventsString = storage1.getEventsString(filePath)
                val events = JSONArray(eventsString)
                eventsCount += events.length()
            }
        }
        assertEquals(8, eventsCount)
    }

    private fun createEarlierVersionEventFiles(prefix: String) {
        val storageDirectory = context.getDir("$prefix-disk-queue", Context.MODE_PRIVATE)
        val file0 = File(storageDirectory, "storageKey-0")
        file0.writeText(
            "[{\"event_type\":\"test1\",\"user_id\":\"159995596214061\",\"device_id\":\"9b935bb3cd75\",\"time\":1708434679570,\"event_properties\":{},\"user_properties\":{},\"groups\":{},\"group_properties\":{},\"platform\":\"Android\",\"os_name\":\"android\",\"os_version\":\"13\",\"device_brand\":\"OP\",\"device_manufacturer\":\"OP\",\"device_model\":\"C71\",\"carrier\":\"WO\",\"language\":\"es\",\"ip\":\"\$remote\",\"version_name\":\"24.1.0\",\"adid\":\"9ea5\",\"event_id\":3681,\"session_id\":1708434677402,\"insert_id\":\"283b4eda-32d4-4919-9817-f97e53f5f288\",\"library\":\"amplitude-analytics-android\\/1.18\",\"android_app_set_id\":\"2a38\"},{\"event_type\":\"test2\",\"user_id\":\"159995596214061\",\"device_id\":\"9b935bb3cd75\",\"time\":1708434679570,\"event_properties\":{},\"user_properties\":{},\"groups\":{},\"group_properties\":{},\"platform\":\"Android\",\"os_name\":\"android\",\"os_version\":\"13\"}]",
        )
        val file1 = File(storageDirectory, "storageKey-1")
        file1.writeText(
            ",{\"event_type\":\"test3\",\"user_id\":\"159995596214061\",\"device_id\":\"9b935bb3cd75\",\"time\":1708434679570,\"event_properties\":{},\"user_properties\":{},\"groups\":{},\"group_properties\":{},\"platform\":\"Android\",\"os_name\":\"android\",\"os_version\":\"13\",\"device_brand\":\"OP\",\"device_manufacturer\":\"OP\",\"device_model\":\"C71\",\"carrier\":\"WO\",\"language\":\"es\",\"ip\":\"\$remote\",\"version_name\":\"24.1.0\",\"adid\":\"9ea5\",\"event_id\":3681,\"session_id\":1708434677402,\"insert_id\":\"283b4eda-32d4-4919-9817-f97e53f5f288\",\"library\":\"amplitude-analytics-android\\/1.18\",\"android_app_set_id\":\"2a38\"},{\"event_type\":\"test4\",\"user_id\":\"159995596214061\",\"device_id\":\"9b935bb3cd75\",\"time\":1708434679570,\"event_properties\":{},\"user_properties\":{},\"groups\":{},\"group_properties\":{},\"platform\":\"Android\",\"os_name\":\"android\",\"os_version\":\"13\"}]",
        )
        val file2 = File(storageDirectory, "storageKey-2")
        file2.writeText(
            "[[{\"event_type\":\"test5\",\"user_id\":\"159995596214061\",\"device_id\":\"9b935bb3cd75\",\"time\":1708434679570,\"event_properties\":{},\"user_properties\":{},\"groups\":{},\"group_properties\":{},\"platform\":\"Android\",\"os_name\":\"android\",\"os_version\":\"13\",\"device_brand\":\"OP\",\"device_manufacturer\":\"OP\",\"device_model\":\"C71\",\"carrier\":\"WO\",\"language\":\"es\",\"ip\":\"\$remote\",\"version_name\":\"24.1.0\",\"adid\":\"9ea5\",\"event_id\":3681,\"session_id\":1708434677402,\"insert_id\":\"283b4eda-32d4-4919-9817-f97e53f5f288\",\"library\":\"amplitude-analytics-android\\/1.18\",\"android_app_set_id\":\"2a38\"},{\"event_type\":\"test6\",\"user_id\":\"159995596214061\",\"device_id\":\"9b935bb3cd75\",\"time\":1708434679570,\"event_properties\":{},\"user_properties\":{},\"groups\":{},\"group_properties\":{},\"platform\":\"Android\",\"os_name\":\"android\",\"os_version\":\"13\"}]]",
        )
        val file3 = File(storageDirectory, "storageKey-3")
        file3.writeText(
            "{\"event_type\":\"test7\",\"user_id\":\"159995596214061\",\"device_id\":\"9b935bb3cd75\",\"time\":1708434679570,\"event_properties\":{},\"user_properties\":{},\"groups\":{},\"group_properties\":{},\"platform\":\"Android\",\"os_name\":\"android\",\"os_version\":\"13\",\"device_brand\":\"OP\",\"device_manufacturer\":\"OP\",\"device_model\":\"C71\",\"carrier\":\"WO\",\"language\":\"es\",\"ip\":\"\$remote\",\"version_name\":\"24.1.0\",\"adid\":\"9ea5\",\"event_id\":3681,\"session_id\":1708434677402,\"insert_id\":\"283b4eda-32d4-4919-9817-f97e53f5f288\",\"library\":\"amplitude-analytics-android\\/1.18\",\"android_app_set_id\":\"2a38\"},{\"event_type\":\"test8\",\"user_id\":\"159995596214061\",\"device_id\":\"9b935bb3cd75\",\"time\":1708434679570,\"event_properties\":{},\"user_properties\":{},\"groups\":{},\"group_properties\":{},\"platform\":\"Android\",\"os_name\":\"android\",\"os_version\":\"13\"}]",
        )
        val file4 = File(storageDirectory, "storageKey-4")
        file4.writeText(
            "[{\"event_type\":\"test9\",\"user_id\":\"159995596214061\",\"device_id\":\"9b935bb3cd75\",\"time\":1708434679570,\"event_properties\":{},\"user_properties\":{},\"groups\":{},\"group_properties\":{},\"platform\":\"Android\",\"os_name\":\"android\",\"os_version\":\"13\",\"device_brand\":\"OP\",\"device_manufacturer\":\"OP\",\"device_model\":\"C71\",\"carrier\":\"WO\",\"language\":\"es\",\"ip\":\"\$remote\",\"version_name\":\"24.1.0\",\"adid\":\"9ea5\",\"event_id\":3681,\"session_id\":1708434677402,\"insert_id\":\"283b4eda-32d4-4919-9817-f97e53f5f288\",\"library\":\"amplitude-analytics-android\\/1.18\",\"android_app_set_id\":\"2a38\"}],{\"event_type\":\"test10\",\"user_id\":\"159995596214061\",\"device_id\":\"9b935bb3cd75\",\"time\":1708434679570,\"event_properties\":{},\"user_properties\":{},\"groups\":{},\"group_properties\":{},\"platform\":\"Android\",\"os_name\":\"android\",\"os_version\":\"13\"}]",
        )
        val file5 = File(storageDirectory, "storageKey-5.tmp")
        file5.writeText(
            "[{\"event_type\":\"test11\",\"user_id\":\"159995596214061\",\"device_id\":\"9b935bb3cd75\",\"time\":1708434679570,\"event_properties\":{},\"user_properties\":{},\"groups\":{},\"group_properties\":{},\"platform\":\"Android\",\"os_name\":\"android\",\"os_version\":\"13\",\"device_brand\":\"OP\",\"device_manufacturer\":\"OP\",\"device_model\":\"C71\",\"carrier\":\"WO\",\"language\":\"es\",\"ip\":\"\$remote\",\"version_name\":\"24.1.0\",\"adid\":\"9ea5\",\"event_id\":3681,\"session_id\":1708434677402,\"insert_id\":\"283b4eda-32d4-4919-9817-f97e53f5f288\",\"library\":\"amplitude-analytics-android\\/1.18\",\"android_app_set_id\":\"2a38\"},{\"event_type\":\"test12\",\"user_id\":\"159995596214061\",\"device_id\":\"9b935bb3cd75\",\"time\":1708434679570,\"event_properties\":{},\"user_properties\":{},\"groups\":{},\"group_properties\":{},\"platform\":\"Android\",\"os_name\":\"android\",\"os_version\":\"13\"}",
        )
    }

    private fun createEvent(eventType: String): BaseEvent {
        val event = BaseEvent()
        event.eventType = eventType
        event.deviceId = "test-device-id"
        return event
    }
}
