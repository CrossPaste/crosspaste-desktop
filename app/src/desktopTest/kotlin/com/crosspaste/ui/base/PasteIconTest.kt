package com.crosspaste.ui.base

import com.crosspaste.app.generated.resources.Res
import com.crosspaste.app.generated.resources.color
import com.crosspaste.app.generated.resources.file
import com.crosspaste.app.generated.resources.file_slash
import com.crosspaste.app.generated.resources.folder
import com.crosspaste.app.generated.resources.html
import com.crosspaste.app.generated.resources.image
import com.crosspaste.app.generated.resources.image_slash
import com.crosspaste.app.generated.resources.link
import com.crosspaste.app.generated.resources.rtf
import com.crosspaste.app.generated.resources.text
import java.io.File
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PasteIconTest {

    @Test
    fun testAllDrawableResourcesExist() {
        val expectedResources =
            listOf(
                Res.drawable.color,
                Res.drawable.file,
                Res.drawable.file_slash,
                Res.drawable.folder,
                Res.drawable.html,
                Res.drawable.image,
                Res.drawable.image_slash,
                Res.drawable.link,
                Res.drawable.rtf,
                Res.drawable.text,
            )

        expectedResources.forEach { resource ->
            assertNotNull(resource, "Drawable resource should not be null")
        }
    }

    fun getResourceFile(resourcePath: String): File {
        val projectRoot =
            System.getProperty("project.root")
                ?: error("project.root system property not set")
        return File("$projectRoot/app/src/commonMain/composeResources/$resourcePath")
    }

    @Test
    fun testIconResourceFilesExist() {
        val projectRoot =
            System.getProperty("project.root")
                ?: error("project.root system property not set")

        val expectedResources =
            listOf(
                "color.svg",
                "file.svg",
                "file-slash.svg",
                "folder.svg",
                "html.svg",
                "image.svg",
                "image-slash.svg",
                "link.svg",
                "rtf.svg",
                "text.svg",
            )

        expectedResources.forEach { resource ->
            val file = File("$projectRoot/app/src/commonMain/composeResources/drawable/$resource")
            assertTrue(file.exists(), "Resource file $resource should exist at $resource")
            assertTrue(file.isFile(), "Resource $resource should be a file")
            assertTrue(file.length() > 0, "Resource file $resource should not be empty")
        }
    }

    @Test
    fun testPasteIconResourcesMatchImplementation() {
        val iconResourceMap =
            mapOf(
                "color" to Res.drawable.color,
                "file" to Res.drawable.file,
                "fileSlash" to Res.drawable.file_slash,
                "folder" to Res.drawable.folder,
                "html" to Res.drawable.html,
                "image" to Res.drawable.image,
                "imageSlash" to Res.drawable.image_slash,
                "link" to Res.drawable.link,
                "rtf" to Res.drawable.rtf,
                "text" to Res.drawable.text,
            )

        iconResourceMap.forEach { (iconName, resource) ->
            assertNotNull(resource, "Resource for $iconName should not be null")
        }
    }
}
