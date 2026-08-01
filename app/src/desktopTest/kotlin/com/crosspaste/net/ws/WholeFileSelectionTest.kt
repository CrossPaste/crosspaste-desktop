package com.crosspaste.net.ws

import com.crosspaste.dto.pull.WsPullFileRequest
import okio.Path.Companion.toPath
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class WholeFileSelectionTest {

    private val candidates =
        listOf(
            WholeFileCandidate("dir-a/file.txt", "/paste/dir-a/file.txt".toPath()),
            WholeFileCandidate("dir-b/file.txt", "/paste/dir-b/file.txt".toPath()),
            WholeFileCandidate("unique.txt", "/paste/unique.txt".toPath()),
        )

    @Test
    fun `relative path selects exact file when basenames collide`() {
        val request =
            WsPullFileRequest.WholeFileRequest(
                fileName = "file.txt",
                relativePath = "dir-b/file.txt",
            )

        assertEquals("/paste/dir-b/file.txt".toPath(), selectWholeFilePath(candidates, request))
    }

    @Test
    fun `legacy basename request rejects ambiguous match`() {
        val request = WsPullFileRequest.WholeFileRequest(fileName = "file.txt")

        assertNull(selectWholeFilePath(candidates, request))
    }

    @Test
    fun `legacy basename request keeps unique fallback`() {
        val request = WsPullFileRequest.WholeFileRequest(fileName = "unique.txt")

        assertEquals("/paste/unique.txt".toPath(), selectWholeFilePath(candidates, request))
    }
}
