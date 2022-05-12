package com.amplitude.id

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class IdentityContainerTest {

    @Test
    fun `test getInstance return same instance for certain name`() {
        val configuration = IdentityConfiguration(
            "testInstance",
            identityStorageProvider = IMIdentityStorageProvider()
        )
        val identityContainer1 = IdentityContainer.getInstance(configuration)
        val identityContainer2 = IdentityContainer.getInstance(configuration)
        assertEquals(identityContainer1, identityContainer2)
    }
}
