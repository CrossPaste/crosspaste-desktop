package com.crosspaste.paste.item

import com.crosspaste.paste.PasteCollection
import com.crosspaste.paste.PasteData
import com.crosspaste.paste.PasteType
import com.crosspaste.paste.item.CreatePasteItemHelper.createFilesPasteItem
import com.crosspaste.paste.item.CreatePasteItemHelper.createImagesPasteItem
import com.crosspaste.paste.item.CreatePasteItemHelper.createTextPasteItem
import com.crosspaste.presist.SingleFileInfoTree
import com.crosspaste.utils.getJsonUtils
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotSame

class FileConflictRenameTest {

    @Suppress("unused")
    private val jsonUtils = getJsonUtils()

    // --- Helper ---

    private fun createFilesItem(
        vararg names: String,
        basePath: String? = null,
    ): FilesPasteItem =
        createFilesPasteItem(
            basePath = basePath,
            relativePathList = names.toList(),
            fileInfoTreeMap = names.associate { it to SingleFileInfoTree(size = 100, hash = "h_$it") },
        )

    private fun createImagesItem(
        vararg names: String,
        basePath: String? = null,
    ): ImagesPasteItem =
        createImagesPasteItem(
            basePath = basePath,
            relativePathList = names.toList(),
            fileInfoTreeMap = names.associate { it to SingleFileInfoTree(size = 200, hash = "h_$it") },
        )

    private fun buildPasteData(
        pasteAppearItem: PasteItem? = null,
        collectionItems: List<PasteItem> = emptyList(),
        pasteType: Int = PasteType.FILE_TYPE.type,
    ): PasteData =
        PasteData(
            appInstanceId = "test-instance",
            pasteAppearItem = pasteAppearItem,
            pasteCollection = PasteCollection(collectionItems),
            pasteType = pasteType,
            size = pasteAppearItem?.size ?: 0,
            hash = pasteAppearItem?.hash ?: "test-hash",
        )

    // ===== A. FilesPasteItem.applyRenameMap =====

    @Test
    fun `FilesPasteItem applyRenameMap with empty map returns equivalent item`() {
        val item = createFilesItem("a.txt", "b.txt")
        val result = item.applyRenameMap(emptyMap())

        assertEquals(item.relativePathList, result.relativePathList)
        assertEquals(item.fileInfoTreeMap, result.fileInfoTreeMap)
    }

    @Test
    fun `FilesPasteItem applyRenameMap renames single file`() {
        val item = createFilesItem("report.pdf")
        val result = item.applyRenameMap(mapOf("report.pdf" to "report(1).pdf"))

        assertEquals(listOf("report(1).pdf"), result.relativePathList)
        assertEquals(setOf("report(1).pdf"), result.fileInfoTreeMap.keys)
        assertEquals(item.fileInfoTreeMap["report.pdf"], result.fileInfoTreeMap["report(1).pdf"])
    }

    @Test
    fun `FilesPasteItem applyRenameMap renames multiple files`() {
        val item = createFilesItem("a.txt", "b.txt")
        val renameMap = mapOf("a.txt" to "a(1).txt", "b.txt" to "b(1).txt")
        val result = item.applyRenameMap(renameMap)

        assertEquals(listOf("a(1).txt", "b(1).txt"), result.relativePathList)
        assertEquals(setOf("a(1).txt", "b(1).txt"), result.fileInfoTreeMap.keys)
    }

    @Test
    fun `FilesPasteItem applyRenameMap partial rename only affects matched keys`() {
        val item = createFilesItem("keep.txt", "rename.txt")
        val result = item.applyRenameMap(mapOf("rename.txt" to "rename(1).txt"))

        assertEquals(listOf("keep.txt", "rename(1).txt"), result.relativePathList)
        assertEquals(setOf("keep.txt", "rename(1).txt"), result.fileInfoTreeMap.keys)
    }

    @Test
    fun `FilesPasteItem applyRenameMap preserves hash size basePath extraInfo`() {
        val item = createFilesItem("a.txt", basePath = "/Downloads")
        val result = item.applyRenameMap(mapOf("a.txt" to "a(1).txt"))

        assertNotSame(item, result)
        assertEquals(item.hash, result.hash)
        assertEquals(item.size, result.size)
        assertEquals(item.count, result.count)
        assertEquals(item.basePath, result.basePath)
        assertEquals(item.extraInfo, result.extraInfo)
        assertEquals(item.identifiers, result.identifiers)
    }

    // ===== B. ImagesPasteItem.applyRenameMap =====

    @Test
    fun `ImagesPasteItem applyRenameMap with empty map returns equivalent item`() {
        val item = createImagesItem("photo.png", "icon.jpg")
        val result = item.applyRenameMap(emptyMap())

        assertEquals(item.relativePathList, result.relativePathList)
        assertEquals(item.fileInfoTreeMap, result.fileInfoTreeMap)
    }

    @Test
    fun `ImagesPasteItem applyRenameMap renames single image`() {
        val item = createImagesItem("photo.png")
        val result = item.applyRenameMap(mapOf("photo.png" to "photo(1).png"))

        assertEquals(listOf("photo(1).png"), result.relativePathList)
        assertEquals(setOf("photo(1).png"), result.fileInfoTreeMap.keys)
        assertEquals(item.fileInfoTreeMap["photo.png"], result.fileInfoTreeMap["photo(1).png"])
    }

    @Test
    fun `ImagesPasteItem applyRenameMap partial rename only affects matched keys`() {
        val item = createImagesItem("keep.png", "rename.jpg")
        val result = item.applyRenameMap(mapOf("rename.jpg" to "rename(1).jpg"))

        assertEquals(listOf("keep.png", "rename(1).jpg"), result.relativePathList)
        assertEquals(setOf("keep.png", "rename(1).jpg"), result.fileInfoTreeMap.keys)
    }

    // ===== D. PasteData-level composition =====

    @Test
    fun `rename applies to pasteAppearItem when it is FilesPasteItem`() {
        val filesItem = createFilesItem("doc.pdf", basePath = "/Downloads")
        val pasteData = buildPasteData(pasteAppearItem = filesItem)
        val renameMap = mapOf("doc.pdf" to "doc(1).pdf")

        val updated = applyRenameMapToPasteData(pasteData, renameMap)

        val updatedItem = assertIs<FilesPasteItem>(updated.pasteAppearItem)
        assertEquals(listOf("doc(1).pdf"), updatedItem.relativePathList)
        assertEquals(setOf("doc(1).pdf"), updatedItem.fileInfoTreeMap.keys)
    }

    @Test
    fun `rename applies to items in pasteCollection`() {
        val textItem = createTextPasteItem(text = "hello")
        val filesItem = createFilesItem("data.csv", basePath = "/Downloads")
        val pasteData =
            buildPasteData(
                pasteAppearItem = textItem,
                collectionItems = listOf(filesItem),
                pasteType = PasteType.TEXT_TYPE.type,
            )
        val renameMap = mapOf("data.csv" to "data(1).csv")

        val updated = applyRenameMapToPasteData(pasteData, renameMap)

        // pasteAppearItem (text) unchanged
        assertIs<TextPasteItem>(updated.pasteAppearItem)
        // collection item renamed
        val collectionItem = assertIs<FilesPasteItem>(updated.pasteCollection.pasteItems.first())
        assertEquals(listOf("data(1).csv"), collectionItem.relativePathList)
    }

    @Test
    fun `rename with no PasteFiles items leaves PasteData unchanged`() {
        val textItem = createTextPasteItem(text = "hello")
        val pasteData =
            buildPasteData(
                pasteAppearItem = textItem,
                pasteType = PasteType.TEXT_TYPE.type,
            )
        val renameMap = mapOf("anything.txt" to "anything(1).txt")

        val updated = applyRenameMapToPasteData(pasteData, renameMap)

        assertEquals(pasteData.pasteAppearItem, updated.pasteAppearItem)
        assertEquals(pasteData.pasteCollection.pasteItems, updated.pasteCollection.pasteItems)
    }

    // Mirrors PullFileTaskExecutor.applyRenameMapToPasteData (private)
    private fun applyRenameMapToPasteData(
        pasteData: PasteData,
        renameMap: Map<String, String>,
    ): PasteData {
        val updatedAppearItem =
            (pasteData.pasteAppearItem as? PasteFiles)?.applyRenameMap(renameMap) as? PasteItem
                ?: pasteData.pasteAppearItem
        val updatedCollectionItems =
            pasteData.pasteCollection.pasteItems.map { item ->
                (item as? PasteFiles)?.applyRenameMap(renameMap) as? PasteItem ?: item
            }
        return pasteData.copy(
            pasteAppearItem = updatedAppearItem,
            pasteCollection = PasteCollection(updatedCollectionItems),
        )
    }
}
