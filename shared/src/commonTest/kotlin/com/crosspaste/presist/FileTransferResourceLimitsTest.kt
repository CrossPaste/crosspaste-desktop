package com.crosspaste.presist

import com.crosspaste.paste.item.PasteFiles
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class FileTransferResourceLimitsTest {

    @Test
    fun `valid metadata returns computed totals`() {
        val item =
            filesItem(
                mapOf(
                    "a.bin" to SingleFileInfoTree(10, "a"),
                    "b.bin" to SingleFileInfoTree(20, "b"),
                ),
            )

        val stats = validateFileTransferMetadata(listOf(item), FileTransferValidationLimits())

        assertEquals(2, stats.fileCount)
        assertEquals(30, stats.totalSize)
    }

    @Test
    fun `metadata rejects depth and node count limits`() {
        var deepTree: FileInfoTree = SingleFileInfoTree(1, "file")
        repeat(3) { depth ->
            deepTree = DirFileInfoTree(mapOf("d$depth" to deepTree), 1, "dir-$depth")
        }
        val deepItem = filesItem(mapOf("root" to deepTree))
        assertFailsWith<IllegalArgumentException> {
            validateFileTransferMetadata(
                listOf(deepItem),
                FileTransferValidationLimits(maxTreeDepth = 2),
            )
        }

        val wideItem =
            filesItem(
                mapOf(
                    "a" to SingleFileInfoTree(1, "a"),
                    "b" to SingleFileInfoTree(1, "b"),
                    "c" to SingleFileInfoTree(1, "c"),
                ),
            )
        assertFailsWith<IllegalArgumentException> {
            validateFileTransferMetadata(
                listOf(wideItem),
                FileTransferValidationLimits(maxTreeNodes = 2),
            )
        }
    }

    @Test
    fun `metadata rejects single and total size limits without allocating files`() {
        val oversized = filesItem(mapOf("large.bin" to SingleFileInfoTree(11, "large")))
        assertFailsWith<IllegalArgumentException> {
            validateFileTransferMetadata(
                listOf(oversized),
                FileTransferValidationLimits(maxSingleFileSize = 10, maxTotalSize = 20),
            )
        }

        val first = filesItem(mapOf("first.bin" to SingleFileInfoTree(6, "first")))
        val second = filesItem(mapOf("second.bin" to SingleFileInfoTree(5, "second")))
        assertFailsWith<IllegalArgumentException> {
            validateFileTransferMetadata(
                listOf(first, second),
                FileTransferValidationLimits(maxSingleFileSize = 10, maxTotalSize = 10),
            )
        }
    }

    @Test
    fun `metadata rejects long overflow`() {
        val first = filesItem(mapOf("first.bin" to SingleFileInfoTree(Long.MAX_VALUE, "first")))
        val second = filesItem(mapOf("second.bin" to SingleFileInfoTree(1, "second")))

        assertFailsWith<IllegalArgumentException> {
            validateFileTransferMetadata(
                listOf(first, second),
                FileTransferValidationLimits(
                    maxSingleFileSize = Long.MAX_VALUE,
                    maxTotalSize = Long.MAX_VALUE,
                ),
            )
        }
    }

    @Test
    fun `metadata rejects inconsistent directory and PasteFiles declarations`() {
        val inconsistentDirectory =
            DirFileInfoTree(
                tree = mapOf("child.bin" to SingleFileInfoTree(5, "child")),
                size = 6,
                hash = "dir",
            )
        assertFailsWith<IllegalArgumentException> {
            validateFileTransferMetadata(
                listOf(filesItem(mapOf("dir" to inconsistentDirectory))),
                FileTransferValidationLimits(),
            )
        }

        val valid = filesItem(mapOf("file.bin" to SingleFileInfoTree(5, "file")))
        val inconsistent = valid.copy(count = 2, size = 6)
        assertFailsWith<IllegalArgumentException> {
            validateFileTransferMetadata(listOf(inconsistent), FileTransferValidationLimits())
        }
    }

    private fun filesItem(fileInfoTreeMap: Map<String, FileInfoTree>): TestPasteFiles =
        TestPasteFiles(
            count = fileInfoTreeMap.values.sumOf { it.getCount() },
            size = fileInfoTreeMap.values.sumOf { it.size },
            relativePathList = fileInfoTreeMap.keys.toList(),
            fileInfoTreeMap = fileInfoTreeMap,
        )

    private data class TestPasteFiles(
        override val count: Long,
        override val size: Long,
        override val relativePathList: List<String>,
        override val fileInfoTreeMap: Map<String, FileInfoTree>,
        override val basePath: String? = null,
    ) : PasteFiles {
        override fun applyRenameMap(renameMap: Map<String, String>): PasteFiles = this
    }
}
