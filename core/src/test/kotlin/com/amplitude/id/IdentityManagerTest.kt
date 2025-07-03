package com.amplitude.id

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class IdentityManagerTest {
    @Test
    fun `test editIdentity, setUserId setDeviceId, getIdentity, success`() {
        val identityStorage = IMIdentityStorage()
        val identityManager = IdentityManagerImpl(identityStorage)
        identityManager.editIdentity()
            .setUserId("user_id")
            .setDeviceId("device_id")
            .commit()
        val identity = identityManager.getIdentity()
        assertEquals(Identity("user_id", "device_id"), identity)
        assertTrue(identityManager.isInitialized())
    }

    @Test
    fun `test editIdentity, setUserId setDeviceId, identity listener called`() {
        val expectedIdentity = Identity("user_id", "device_id")
        val identityStorage = IMIdentityStorage()
        val identityManager = IdentityManagerImpl(identityStorage)
        var listenerCalled = false
        identityManager.addIdentityListener(
            object : IdentityListener {
                override fun onUserIdChange(userId: String?) {
                    assertEquals("user_id", userId)
                }

                override fun onDeviceIdChange(deviceId: String?) {
                    assertEquals("device_id", deviceId)
                }

                override fun onIdentityChanged(
                    identity: Identity,
                    updateType: IdentityUpdateType,
                ) {
                    assertEquals(expectedIdentity, identity)
                    listenerCalled = true
                }
            },
        )
        identityManager.editIdentity()
            .setUserId("user_id")
            .setDeviceId("device_id")
            .commit()
        assertTrue(listenerCalled)
    }

    @Test
    fun `test setIdentity, getIdentity, success`() {
        val expectedIdentity = Identity("user_id", "device_id")
        val identityStorage = IMIdentityStorage()
        val identityManager = IdentityManagerImpl(identityStorage)
        identityManager.setIdentity(expectedIdentity)
        val identity = identityManager.getIdentity()
        assertEquals(expectedIdentity, identity)
    }

    @Test
    fun `test setIdentity, identity listener called`() {
        val expectedIdentity = Identity("user_id", "device_id")
        val identityStorage = IMIdentityStorage()
        val identityManager = IdentityManagerImpl(identityStorage)
        var listenerCalled = false
        identityManager.addIdentityListener(
            object : IdentityListener {
                override fun onUserIdChange(userId: String?) {
                    assertEquals("user_id", userId)
                }

                override fun onDeviceIdChange(deviceId: String?) {
                    assertEquals("device_id", deviceId)
                }

                override fun onIdentityChanged(
                    identity: Identity,
                    updateType: IdentityUpdateType,
                ) {
                    assertEquals(expectedIdentity, identity)
                    listenerCalled = true
                }
            },
        )
        identityManager.setIdentity(expectedIdentity)
        assertTrue(listenerCalled)
    }

    @Test
    fun `test setIdentity with unchanged identity, identity listener not called`() {
        val expectedIdentity = Identity("user_id", "device_id")
        val identityStorage = IMIdentityStorage()
        val identityManager = IdentityManagerImpl(identityStorage)
        identityManager.setIdentity(expectedIdentity)
        var listenerCalled = false
        identityManager.addIdentityListener(
            object : IdentityListener {
                override fun onUserIdChange(userId: String?) {
                }

                override fun onDeviceIdChange(deviceId: String?) {
                }

                override fun onIdentityChanged(
                    identity: Identity,
                    updateType: IdentityUpdateType,
                ) {
                    listenerCalled = true
                }
            },
        )
        identityManager.setIdentity(expectedIdentity)
        assertFalse(listenerCalled)
    }
}
