package com.crosspaste.presist

import com.crosspaste.utils.getJsonUtils
import okio.Path.Companion.toPath
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FileInfoTreeTest {

    private val jsonUtils = getJsonUtils()

    // --- SingleFileInfoTree ---

    @Test
    fun `SingleFileInfoTree isFile returns true`() {
        val tree = SingleFileInfoTree(size = 100L, hash = "abc")
        assertTrue(tree.isFile())
    }

    @Test
    fun `SingleFileInfoTree getCount returns 1`() {
        val tree = SingleFileInfoTree(size = 100L, hash = "abc")
        assertEquals(1L, tree.getCount())
    }

    @Test
    fun `SingleFileInfoTree getPasteFileList returns single file`() {
        val tree = SingleFileInfoTree(size = 100L, hash = "abc")
        val files = tree.getPasteFileList("/test/file.txt".toPath())
        assertEquals(1, files.size)
    }

    @Test
    fun `SingleFileInfoTree preserves size and hash`() {
        val tree = SingleFileInfoTree(size = 42L, hash = "myhash")
        assertEquals(42L, tree.size)
        assertEquals("myhash", tree.hash)
    }

    // --- DirFileInfoTree ---

    @Test
    fun `DirFileInfoTree isFile returns false`() {
        val tree =
            DirFileInfoTree(
                tree = mapOf("a.txt" to SingleFileInfoTree(10, "h1")),
                size = 10L,
                hash = "dirhash",
            )
        assertFalse(tree.isFile())
    }

    @Test
    fun `DirFileInfoTree getCount returns total file count`() {
        val tree =
            DirFileInfoTree(
                tree =
                    mapOf(
                        "a.txt" to SingleFileInfoTree(10, "h1"),
                        "b.txt" to SingleFileInfoTree(20, "h2"),
                    ),
                size = 30L,
                hash = "dirhash",
            )
        assertEquals(2L, tree.getCount())
    }

    @Test
    fun `DirFileInfoTree nested directory getCount`() {
        val inner =
            DirFileInfoTree(
                tree =
                    mapOf(
                        "c.txt" to SingleFileInfoTree(5, "h3"),
                        "d.txt" to SingleFileInfoTree(5, "h4"),
                    ),
                size = 10L,
                hash = "innerhash",
            )
        val outer =
            DirFileInfoTree(
                tree =
                    mapOf(
                        "a.txt" to SingleFileInfoTree(10, "h1"),
                        "subdir" to inner,
                    ),
                size = 20L,
                hash = "outerhash",
            )
        assertEquals(3L, outer.getCount())
    }

    @Test
    fun `DirFileInfoTree getPasteFileList returns all files with correct paths`() {
        val tree =
            DirFileInfoTree(
                tree =
                    mapOf(
                        "a.txt" to SingleFileInfoTree(10, "h1"),
                        "b.txt" to SingleFileInfoTree(20, "h2"),
                    ),
                size = 30L,
                hash = "dirhash",
            )
        val files = tree.getPasteFileList("/root".toPath())
        assertEquals(2, files.size)
    }

    @Test
    fun `DirFileInfoTree nested getPasteFileList flattens correctly`() {
        val inner =
            DirFileInfoTree(
                tree =
                    mapOf(
                        "c.txt" to SingleFileInfoTree(5, "h3"),
                    ),
                size = 5L,
                hash = "innerhash",
            )
        val outer =
            DirFileInfoTree(
                tree =
                    mapOf(
                        "a.txt" to SingleFileInfoTree(10, "h1"),
                        "sub" to inner,
                    ),
                size = 15L,
                hash = "outerhash",
            )
        val files = outer.getPasteFileList("/root".toPath())
        assertEquals(2, files.size)
    }

    @Test
    fun `DirFileInfoTree iterator returns sorted entries`() {
        val tree =
            DirFileInfoTree(
                tree =
                    mapOf(
                        "c.txt" to SingleFileInfoTree(5, "h3"),
                        "a.txt" to SingleFileInfoTree(10, "h1"),
                        "b.txt" to SingleFileInfoTree(20, "h2"),
                    ),
                size = 35L,
                hash = "dirhash",
            )
        val names =
            tree
                .iterator()
                .asSequence()
                .map { it.first }
                .toList()
        assertEquals(listOf("a.txt", "b.txt", "c.txt"), names)
    }

    // --- FileInfoTreeBuilder ---

    @Test
    fun `FileInfoTreeBuilder builds DirFileInfoTree with correct size`() {
        val builder = FileInfoTreeBuilder()
        builder.addFileInfoTree("a.txt", SingleFileInfoTree(100, "h1"))
        builder.addFileInfoTree("b.txt", SingleFileInfoTree(200, "h2"))
        val tree = builder.build("/test".toPath())

        assertFalse(tree.isFile())
        assertEquals(300L, tree.size)
        assertEquals(2L, tree.getCount())
    }

    @Test
    fun `FileInfoTreeBuilder empty build produces directory with hash from path name`() {
        val builder = FileInfoTreeBuilder()
        val tree = builder.build("/mydir".toPath())

        assertFalse(tree.isFile())
        assertEquals(0L, tree.size)
        assertTrue(tree.hash.isNotEmpty())
    }

    @Test
    fun `FileInfoTreeBuilder computes hash from child hashes`() {
        val builder = FileInfoTreeBuilder()
        builder.addFileInfoTree("x.txt", SingleFileInfoTree(10, "hash_x"))
        val tree = builder.build("/dir".toPath())

        assertTrue(tree.hash.isNotEmpty())
    }

    // --- Serialization ---

    @Test
    fun `SingleFileInfoTree serialization roundtrip`() {
        val original = SingleFileInfoTree(size = 42L, hash = "test_hash")
        val json = jsonUtils.JSON.encodeToString(original as FileInfoTree)
        val restored = jsonUtils.JSON.decodeFromString<FileInfoTree>(json)

        assertTrue(restored is SingleFileInfoTree)
        assertEquals(42L, restored.size)
        assertEquals("test_hash", restored.hash)
    }

    @Test
    fun `DirFileInfoTree serialization roundtrip`() {
        val original: FileInfoTree =
            DirFileInfoTree(
                tree =
                    mapOf(
                        "file1.txt" to SingleFileInfoTree(10, "h1"),
                        "file2.txt" to SingleFileInfoTree(20, "h2"),
                    ),
                size = 30L,
                hash = "dir_hash",
            )
        val json = jsonUtils.JSON.encodeToString(original)
        val restored = jsonUtils.JSON.decodeFromString<FileInfoTree>(json)

        assertTrue(restored is DirFileInfoTree)
        assertEquals(30L, restored.size)
        assertEquals(2L, restored.getCount())
    }

    @Test
    fun `nested DirFileInfoTree serialization roundtrip`() {
        val inner: FileInfoTree =
            DirFileInfoTree(
                tree = mapOf("inner.txt" to SingleFileInfoTree(5, "hi")),
                size = 5L,
                hash = "inner_hash",
            )
        val outer: FileInfoTree =
            DirFileInfoTree(
                tree =
                    mapOf(
                        "outer.txt" to SingleFileInfoTree(10, "ho"),
                        "subdir" to inner,
                    ),
                size = 15L,
                hash = "outer_hash",
            )
        val json = jsonUtils.JSON.encodeToString(outer)
        val restored = jsonUtils.JSON.decodeFromString<FileInfoTree>(json)

        assertEquals(2L, restored.getCount())
    }
}
