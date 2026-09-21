package com.amplitude.android.streaming

import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class PlayerContentTest {
    @Test
    fun `defaults are null`() {
        val content = PlayerContent()
        assertNull(content.contentId)
        assertNull(content.title)
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

    @Test
    fun `maps mediaId title and json-safe extras`() {
        val item =
            mediaItem(
                mediaId = "ep-42",
                title = "Episode 42",
                extras =
                    bundleOf(
                        "show_id" to "show-7",
                        "season" to 2,
                        "subscriber" to true,
                        "artwork" to Any(),
                    ),
            )

        val content = item.toPlayerContent()

        assertEquals("ep-42", content.contentId)
        assertEquals("Episode 42", content.title)
        assertEquals("show-7", content.extraProperties?.get("show_id"))
        assertEquals(2, content.extraProperties?.get("season"))
        assertEquals(true, content.extraProperties?.get("subscriber"))
        assertFalse(content.extraProperties.orEmpty().containsKey("artwork"))
    }

    @Test
    fun `falls back to displayTitle`() {
        val metadata = MediaMetadata.Builder().setDisplayTitle("Fallback").build()
        val item = MediaItem.Builder().setMediaMetadata(metadata).build()

        assertEquals("Fallback", item.toPlayerContent().title)
    }

    @Test
    fun `drops extras that cannot be read`() {
        val extras =
            mockk<Bundle> {
                every { keySet() } throws RuntimeException("unmarshalling failed")
            }
        val item = mediaItem(mediaId = "ep-42", extras = extras)

        assertEquals("ep-42", item.toPlayerContent().contentId)
        assertNull(item.toPlayerContent().extraProperties)
    }

    @Test
    fun `omits blank ids and empty metadata`() {
        val item = MediaItem.Builder().setMediaId(" ").build()

        val content = item.toPlayerContent()

        assertNull(content.contentId)
        assertNull(content.title)
        assertNull(content.extraProperties)
    }

    @Test
    fun `tolerates Media3 platform-type nulls`() {
        val item = mockk<MediaItem>(relaxed = true)
        val content = item.toPlayerContent()
        assertNull(content.contentId)
        assertNull(content.title)
        assertNull(content.extraProperties)
    }

    private fun mediaItem(
        mediaId: String,
        title: String? = null,
        extras: Bundle? = null,
    ): MediaItem =
        MediaItem.Builder()
            .setMediaId(mediaId)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setExtras(extras)
                    .build(),
            ).build()

    /**
     * Robolectric would give a real [Bundle], but initializing it in this module's shared test
     * JVM replaces the global URL stream handler and breaks the MockWebServer-based tests.
     */
    @Suppress("DEPRECATION")
    private fun bundleOf(vararg entries: Pair<String, Any?>): Bundle {
        val values = entries.toMap()
        val bundle = mockk<Bundle>()
        every { bundle.keySet() } returns values.keys
        every { bundle.get(any<String>()) } answers { values[firstArg()] }
        return bundle
    }
}
