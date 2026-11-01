package com.crosspaste.paste.item

import com.crosspaste.utils.getJsonUtils
import com.crosspaste.utils.getPlatformUtils
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okio.Path.Companion.toPath
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PasteFilesBaseDirTest {

    // Forces DesktopCoreJsonUtils to initialize before any PasteItem is created;
    // see reference_pasteitem_jsonutils_circular_init.
    @Suppress("unused")
    private val jsonUtils = getJsonUtils()

    private val downloadDir = getPlatformUtils().getSystemDownloadDir()

    private val baseDirMark = PasteItemProperties.BASE_DIR to JsonPrimitive(PasteItemProperties.BASE_DIR_DOWNLOAD)

    private fun filesItem(
        basePath: String?,
        extraInfo: JsonObject?,
    ) = FilesPasteItem(
        identifiers = emptyList(),
        count = 1,
        hash = "hash",
        size = 1,
        basePath = basePath,
        fileInfoTreeMap = emptyMap(),
        relativePathList = listOf("a.zip"),
        extraInfo = extraInfo,
    )

    @Test
    fun marksItemsBoundToTheSystemDownloadDir() {
        val extraInfo = buildJsonObject { put(PasteItemProperties.NAME, "n") }
        assertEquals(
            JsonObject(extraInfo + baseDirMark),
            bindBaseDir(extraInfo, downloadDir.toString()),
        )
    }

    @Test
    fun dropsAMarkCarriedOverFromTheSender() {
        val withName = JsonObject(mapOf(PasteItemProperties.NAME to JsonPrimitive("n"), baseDirMark))
        assertEquals(
            buildJsonObject { put(PasteItemProperties.NAME, "n") },
            bindBaseDir(withName, "/elsewhere"),
        )
        assertNull(bindBaseDir(JsonObject(mapOf(baseDirMark)), null))
    }

    @Test
    fun leavesExtraInfoAloneOtherwise() {
        val extraInfo = buildJsonObject { put(PasteItemProperties.NAME, "n") }
        assertEquals(extraInfo, bindBaseDir(extraInfo, "/elsewhere"))
        assertNull(bindBaseDir(null, null))
    }

    @Test
    fun markedItemResolvesToTheCurrentDownloadDir() {
        val stale = "/var/mobile/Containers/Data/Application/1111/Documents"
        assertEquals(downloadDir, filesItem(stale, JsonObject(mapOf(baseDirMark))).resolveBasePath())
    }

    @Test
    fun unmarkedItemKeepsItsStoredBasePath() {
        assertEquals("/custom/dir".toPath(), filesItem("/custom/dir", null).resolveBasePath())
        assertNull(filesItem(null, JsonObject(mapOf(baseDirMark))).resolveBasePath())
    }
}
