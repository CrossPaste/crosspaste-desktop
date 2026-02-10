package com.crosspaste.image

import com.crosspaste.info.createPasteInfoWithoutConverter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ImageInfoTest {

    @Test
    fun `ImageInfoBuilder builds empty ImageInfo`() {
        val imageInfo = ImageInfoBuilder().build()
        assertTrue(imageInfo.map.isEmpty())
    }

    @Test
    fun `ImageInfoBuilder accumulates entries keyed by PasteInfo key`() {
        val imageInfo =
            ImageInfoBuilder()
                .add(createPasteInfoWithoutConverter("width", "100"))
                .add(createPasteInfoWithoutConverter("height", "200"))
                .add(createPasteInfoWithoutConverter("format", "png"))
                .build()
        assertEquals(3, imageInfo.map.size)
        assertEquals("100", imageInfo.map["width"]?.value)
        assertEquals("200", imageInfo.map["height"]?.value)
        assertEquals("png", imageInfo.map["format"]?.value)
    }

    @Test
    fun `ImageInfoBuilder overwrites duplicate keys with latest value`() {
        val imageInfo =
            ImageInfoBuilder()
                .add(createPasteInfoWithoutConverter("width", "100"))
                .add(createPasteInfoWithoutConverter("width", "200"))
                .build()
        assertEquals(1, imageInfo.map.size)
        assertEquals("200", imageInfo.map["width"]?.value)
    }

    @Test
    fun `ImageInfoBuilder add returns self for fluent chaining`() {
        val builder = ImageInfoBuilder()
        val result = builder.add(createPasteInfoWithoutConverter("key", "value"))
        assertTrue(result === builder)
    }
}
