package com.amplitude.core.remoteconfig

import com.amplitude.common.jvm.ConsoleLogger
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class FileRemoteConfigStorageTest {
    @TempDir
    lateinit var directory: File

    private val value = CachedRemoteConfig("""{"sessionReplay":{"a":1}}""", 1234567890L)

    private fun storage(
        apiKey: String = "api-key",
        namespace: String = "instance",
    ) = FileRemoteConfigStorage(directory, apiKey, namespace, ConsoleLogger.logger)

    private fun cacheFiles() = File(directory, "com.amplitude.remote-config").listFiles().orEmpty()

    @Test
    fun `round trips a written config`() {
        runBlocking { storage().write(value) }

        val read = storage().read()

        assertEquals(value.timestamp, read?.timestamp)
        assertEquals(1, org.json.JSONObject(read?.configJson.orEmpty()).getJSONObject("sessionReplay").getInt("a"))
    }

    @Test
    fun `missing file reads as null`() {
        assertNull(storage().read())
    }

    @Test
    fun `corrupt or truncated file reads as null without throwing`() {
        runBlocking { storage().write(value) }
        val file = cacheFiles().single()

        file.writeText("not json")
        assertNull(storage().read())

        file.writeText("""{"apiKey":"api-key","config":{"a":""")
        assertNull(storage().read())
    }

    @Test
    fun `apiKey mismatch is ignored`() {
        runBlocking { storage(apiKey = "other-key").write(value) }

        assertNull(storage(apiKey = "api-key").read())
    }

    @Test
    fun `namespaces are isolated`() {
        runBlocking { storage(namespace = "one").write(value) }

        assertNull(storage(namespace = "two").read())
        assertEquals(value.timestamp, storage(namespace = "one").read()?.timestamp)
    }

    @Test
    fun `write replaces the previous value and leaves no temp files`() {
        runBlocking {
            storage().write(value)
            storage().write(CachedRemoteConfig("""{"next":{}}""", 42L))
        }

        val read = storage().read()

        assertEquals(42L, read?.timestamp)
        assertNotEquals(value.configJson, read?.configJson)
        assertEquals(1, cacheFiles().size)
        assertFalse(cacheFiles().any { it.name.endsWith(".tmp") })
    }
}
