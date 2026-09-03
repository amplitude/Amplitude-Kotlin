package com.amplitude.android.streaming

import com.amplitude.core.AmplitudePreview
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

@OptIn(AmplitudePreview::class)
class PlayerContentTest {
    @Test
    fun `defaults are null`() {
        val content = PlayerContent()
        assertNull(content.contentId)
        assertNull(content.title)
        assertNull(content.deliveryMode)
        assertNull(content.extraProperties)
    }

    @Test
    fun `extraProperties is a defensive copy`() {
        val extras = mutableMapOf<String, Any?>("season" to 1)
        val content = PlayerContent(extraProperties = extras)
        extras["season"] = 2
        assertEquals(1, content.extraProperties?.get("season"))
        assertNotSame(extras, content.extraProperties)
    }
}
