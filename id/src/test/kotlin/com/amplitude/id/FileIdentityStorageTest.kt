package com.amplitude.id

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FileIdentityStorageTest {

    @Test
    fun `test FileIdentityStorage create and save success`() {
        val identityStorageProvider = FileIdentityStorageProvider()
        val identityConfiguration = IdentityConfiguration(
            instanceName = "testInstance",
            identityStorageProvider = identityStorageProvider,
            apiKey = "test-api-key"
        )
        val fileIdentityStorage = identityStorageProvider.getIdentityStorage(identityConfiguration)
        fileIdentityStorage.saveUserId("user_id")
        fileIdentityStorage.saveDeviceId("device_id")
        val savedIdentity = fileIdentityStorage.load()
        val expectedIdentity = Identity("user_id", "device_id")
        assertEquals(savedIdentity, expectedIdentity)
    }

    @Test
    fun `test FileIdentityStorage load from file success`() {
        val identityStorageProvider = FileIdentityStorageProvider()
        val identityConfiguration = IdentityConfiguration(
            instanceName = "testInstance",
            identityStorageProvider = identityStorageProvider,
            apiKey = "test-api-key"
        )
        val fileIdentityStorage = identityStorageProvider.getIdentityStorage(identityConfiguration)
        val savedIdentity = fileIdentityStorage.load()
        val expectedIdentity = Identity("user_id", "device_id")
        assertEquals(savedIdentity, expectedIdentity)
    }

    @Test
    fun `test FileIdentityStorage load with different apiKey will clear the data`() {
        val identityStorageProvider = FileIdentityStorageProvider()
        val identityConfiguration = IdentityConfiguration(
            instanceName = "testInstance",
            identityStorageProvider = identityStorageProvider,
            apiKey = "test-different-api-key",
            experimentApiKey = "test-experiment-api-key"
        )
        val fileIdentityStorage = identityStorageProvider.getIdentityStorage(identityConfiguration)
        val savedIdentity = fileIdentityStorage.load()
        val expectedIdentity = Identity(null, null)
        assertEquals(savedIdentity, expectedIdentity)
    }
}
