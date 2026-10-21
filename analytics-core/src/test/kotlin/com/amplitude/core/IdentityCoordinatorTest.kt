package com.amplitude.core

import com.amplitude.id.Identity
import com.amplitude.id.IdentityManager
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
internal class IdentityCoordinatorTest {
    private lateinit var state: State
    private lateinit var coordinator: IdentityCoordinator

    @BeforeEach
    fun setup() {
        state = State()
        coordinator = IdentityCoordinator(state)
    }

    @Nested
    inner class BeforeBootstrap {
        @Test
        fun `setUserId writes to State immediately`() {
            coordinator.setUserId("pre-build-user")
            assertEquals("pre-build-user", state.userId)
        }

        @Test
        fun `setDeviceId writes to State immediately`() {
            coordinator.setDeviceId("pre-build-device")
            assertEquals("pre-build-device", state.deviceId)
        }

        @Test
        fun `setUserId null writes null to State`() {
            state.userId = "existing"
            coordinator.setUserId(null)
            assertNull(state.userId)
        }
    }

    @Nested
    inner class Bootstrap {
        @Test
        fun `complete identity set before bootstrap replaces persisted ids and keeps runtime properties`() {
            coordinator.setIdentity(IdentitySnapshot("new-user", null, mapOf("plan" to "paid")))
            coordinator.bootstrap(mockIdentityManager(Identity("persisted-user", "persisted-device")))

            assertEquals("new-user", coordinator.snapshot().userId)
            assertNull(coordinator.snapshot().deviceId)
            assertEquals(mapOf("plan" to "paid"), coordinator.snapshot().userProperties)
        }

        @Test
        fun `unchanged individual ids still override persisted ids during bootstrap`() {
            assertFalse(coordinator.setUserId(null))
            coordinator.setDeviceId("device")
            assertFalse(coordinator.setDeviceId("device"))
            coordinator.bootstrap(mockIdentityManager(Identity("persisted-user", "persisted-device")))

            assertNull(state.userId)
            assertEquals("device", state.deviceId)
        }

        @Test
        fun `setting an unchanged null identity before bootstrap still clears persisted ids`() {
            assertNull(coordinator.setIdentity(IdentitySnapshot(null, null, emptyMap())))
            coordinator.bootstrap(mockIdentityManager(Identity("persisted-user", "persisted-device")))

            assertNull(coordinator.snapshot().userId)
            assertNull(coordinator.snapshot().deviceId)
        }

        @Test
        fun `pending properties survive bootstrap without being persisted`() {
            coordinator.applyUserProperties(mapOf("\$set" to mapOf("plan" to "paid")))
            coordinator.bootstrap(mockIdentityManager(Identity("persisted-user", "persisted-device")))

            assertEquals("persisted-user", coordinator.snapshot().userId)
            assertEquals(mapOf("plan" to "paid"), coordinator.snapshot().userProperties)
        }

        @Test
        fun `without pending changes - uses persisted identity`() {
            val identityManager = mockIdentityManager(Identity("persisted-user", "persisted-device"))

            coordinator.bootstrap(identityManager)

            assertEquals("persisted-user", state.userId)
            assertEquals("persisted-device", state.deviceId)
        }

        @Test
        fun `pending userId wins over persisted`() {
            val identityManager = mockIdentityManager(Identity("persisted-user", "persisted-device"))

            coordinator.setUserId("pre-build-user")
            coordinator.bootstrap(identityManager)

            assertEquals("pre-build-user", state.userId)
            assertEquals("persisted-device", state.deviceId)
        }

        @Test
        fun `pending deviceId wins over persisted`() {
            val identityManager = mockIdentityManager(Identity("persisted-user", "persisted-device"))

            coordinator.setDeviceId("pre-build-device")
            coordinator.bootstrap(identityManager)

            assertEquals("persisted-user", state.userId)
            assertEquals("pre-build-device", state.deviceId)
        }

        @Test
        fun `pending null userId is preserved over persisted non-null`() {
            val identityManager = mockIdentityManager(Identity("persisted-user", "persisted-device"))

            coordinator.setUserId(null)
            coordinator.bootstrap(identityManager)

            assertNull(state.userId)
            assertEquals("persisted-device", state.deviceId)
        }
    }

    @Nested
    inner class AfterBootstrap {
        private lateinit var identityManager: IdentityManager
        private lateinit var editor: IdentityManager.Editor

        @BeforeEach
        fun bootstrapFirst() {
            editor =
                mockk<IdentityManager.Editor>(relaxed = true) {
                    every { setUserId(any()) } returns this@mockk
                    every { setDeviceId(any()) } returns this@mockk
                }
            identityManager =
                mockk<IdentityManager> {
                    every { getIdentity() } returns Identity()
                    every { editIdentity() } returns editor
                }
            coordinator.bootstrap(identityManager)
        }

        @Test
        fun `unchanged user id still commits to IdentityManager`() {
            assertFalse(coordinator.setUserId(null))

            verify(exactly = 2) { editor.setUserId(null) }
            verify(exactly = 2) { editor.commit() }
        }

        @Test
        fun `unchanged device id still commits to IdentityManager`() {
            coordinator.setDeviceId("device")
            assertFalse(coordinator.setDeviceId("device"))

            verify(exactly = 2) { editor.setDeviceId("device") }
            verify(exactly = 3) { editor.commit() }
        }

        @Test
        fun `identify option ids commit together once`() {
            clearMocks(editor, answers = false)
            val identitiesAtCommit = mutableListOf<AnalyticsIdentity>()
            every { editor.commit() } answers { identitiesAtCommit += coordinator.snapshot() }

            val change = coordinator.identify("user", "device", mapOf("\$set" to mapOf("plan" to "paid")))

            assertEquals(mapOf("plan" to "paid"), identitiesAtCommit.single().userProperties)
            assertEquals("user", change?.current?.userId)
            assertEquals("device", change?.current?.deviceId)
            assertEquals(mapOf("plan" to "paid"), change?.current?.userProperties)
            verifyOrder {
                editor.setUserId("user")
                editor.setDeviceId("device")
                editor.commit()
            }
            verify(exactly = 1) { editor.commit() }
        }

        @Test
        fun `unchanged identify option ids still commit without a change`() {
            coordinator.identify("user", "device", null)
            clearMocks(editor, answers = false)

            assertNull(coordinator.identify("user", "device", null))
            verify(exactly = 1) { editor.commit() }
        }

        @Test
        fun `setUserId writes to State and commits to IdentityManager`() {
            coordinator.setUserId("new-user")

            assertEquals("new-user", state.userId)
            verify {
                editor.setUserId("new-user")
                editor.commit()
            }
        }

        @Test
        fun `setDeviceId writes to State and commits to IdentityManager`() {
            coordinator.setDeviceId("new-device")

            assertEquals("new-device", state.deviceId)
            verify {
                editor.setDeviceId("new-device")
                editor.commit()
            }
        }
    }

    private fun mockIdentityManager(persisted: Identity): IdentityManager {
        val editor =
            mockk<IdentityManager.Editor>(relaxed = true) {
                every { setUserId(any()) } returns this@mockk
                every { setDeviceId(any()) } returns this@mockk
            }
        return mockk {
            every { getIdentity() } returns persisted
            every { editIdentity() } returns editor
        }
    }
}
