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

    @Test
    fun `nested extraProperties values are defensively copied`() {
        val nested = mutableMapOf("quality" to "sd")
        val tags = mutableListOf<Any?>("intro")
        val extras =
            mutableMapOf<String, Any?>(
                "nested" to nested,
                "tags" to tags,
            )

        val content = PlayerContent(extraProperties = extras)
        nested["quality"] = "hd"
        tags.add("credits")
        extras["season"] = 2

        @Suppress("UNCHECKED_CAST")
        val copiedNested = content.extraProperties?.get("nested") as Map<String, Any?>
        @Suppress("UNCHECKED_CAST")
        val copiedTags = content.extraProperties?.get("tags") as List<Any?>

        assertEquals("sd", copiedNested["quality"])
        assertEquals(listOf("intro"), copiedTags)
        assertNull(content.extraProperties?.get("season"))
        assertNotSame(nested, copiedNested)
        assertNotSame(tags, copiedTags)
    }
}
