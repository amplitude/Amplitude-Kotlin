package com.amplitude.android.utilities

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SemVerTest {
    @Test
    fun `create parses major minor patch`() {
        assertEquals(SemVer(1, 8, 0), SemVer.create("1.8.0"))
    }

    @Test
    fun `create pads missing minor and patch`() {
        assertEquals(SemVer(1, 0, 0), SemVer.create("1"))
        assertEquals(SemVer(1, 8, 0), SemVer.create("1.8"))
    }

    @Test
    fun `create strips v prefix and prerelease suffix`() {
        assertEquals(SemVer(1, 8, 0), SemVer.create("v1.8.0-SNAPSHOT"))
    }

    @Test
    fun `create returns null for invalid versions`() {
        assertNull(SemVer.create("not-a-version"))
    }

    @Test
    fun `compareTo orders by major minor patch`() {
        assertTrue(SemVer.create("1.8.0")!! >= SemVer.create("1.7.9")!!)
        assertTrue(SemVer.create("1.8.0")!! < SemVer.create("1.9.0")!!)
    }
}
