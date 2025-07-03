package com.amplitude.id

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.io.File

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class IdentityContainerTest {
    val storageDirectory = File("/tmp/amplitude-kotlin-file-identity-test")

    @BeforeEach
    fun before() {
        storageDirectory.deleteRecursively()
    }

    @Test
    fun `test getInstance return same instance for certain name`() {
        val configuration =
            IdentityConfiguration(
                "testInstance",
                identityStorageProvider = IMIdentityStorageProvider(),
                storageDirectory = storageDirectory,
                fileName = "identity.properties",
            )
        val identityContainer1 = IdentityContainer.getInstance(configuration)
        val identityContainer2 = IdentityContainer.getInstance(configuration)
        assertEquals(identityContainer1, identityContainer2)
    }
}
