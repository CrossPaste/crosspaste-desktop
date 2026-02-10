package com.crosspaste.image.coil

import com.crosspaste.paste.item.PasteCoordinate
import com.crosspaste.paste.item.PasteFileCoordinate
import okio.Path.Companion.toPath
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class ImageItemsTest {

    @Test
    fun `GenerateImageKeyer uses path as key`() {
        val keyer = GenerateImageKeyer()
        val path = "/tmp/test.png".toPath()
        val key = keyer.key(GenerateImageItem(path), io.mockk.mockk())
        assertEquals(path.toString(), key)
    }

    @Test
    fun `AppSourceKeyer uses source as key`() {
        val keyer = AppSourceKeyer()
        assertEquals("com.example.app", keyer.key(AppSourceItem("com.example.app"), io.mockk.mockk()))
    }

    @Test
    fun `AppSourceKeyer returns empty string for null source`() {
        val keyer = AppSourceKeyer()
        assertEquals("", keyer.key(AppSourceItem(null), io.mockk.mockk()))
    }

    @Test
    fun `UrlKeyer uses url as key`() {
        val coordinate = PasteCoordinate(1L, "test-instance")
        val keyer = UrlKeyer()
        val key = keyer.key(UrlItem("https://example.com", coordinate), io.mockk.mockk())
        assertEquals("https://example.com", key)
    }

    @Test
    fun `FileExtKeyer uses path as key`() {
        val keyer = FileExtKeyer()
        val path = "/tmp/file.txt".toPath()
        assertEquals(path.toString(), keyer.key(FileExtItem(path), io.mockk.mockk()))
    }

    @Test
    fun `ImageKeyer combines id filePath and useThumbnail into key`() {
        val coordinate = PasteFileCoordinate(1L, "test-instance", filePath = "/tmp/img.png".toPath())
        val keyer = ImageKeyer()
        val key = keyer.key(ImageItem(coordinate, useThumbnail = false), io.mockk.mockk())
        assertEquals("${coordinate.id}_${coordinate.filePath}_false", key)
    }

    @Test
    fun `ImageKeyer produces different keys for thumbnail vs non-thumbnail`() {
        val coordinate = PasteFileCoordinate(1L, "test-instance", filePath = "/tmp/img.png".toPath())
        val keyer = ImageKeyer()
        val options = io.mockk.mockk<coil3.request.Options>()
        val thumbnailKey = keyer.key(ImageItem(coordinate, useThumbnail = true), options)
        val fullKey = keyer.key(ImageItem(coordinate, useThumbnail = false), options)
        assertNotEquals(thumbnailKey, fullKey)
    }

    @Test
    fun `ImageKeyer produces different keys for different file paths`() {
        val keyer = ImageKeyer()
        val options = io.mockk.mockk<coil3.request.Options>()
        val coord1 = PasteFileCoordinate(1L, "inst", filePath = "/a.png".toPath())
        val coord2 = PasteFileCoordinate(1L, "inst", filePath = "/b.png".toPath())
        assertNotEquals(
            keyer.key(ImageItem(coord1, useThumbnail = false), options),
            keyer.key(ImageItem(coord2, useThumbnail = false), options),
        )
    }
}
