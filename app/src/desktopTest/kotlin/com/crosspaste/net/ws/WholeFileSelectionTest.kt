package com.crosspaste.net.ws

import com.crosspaste.dto.pull.WsPullFileRequest
import com.crosspaste.presist.SingleFileInfoTree
import com.crosspaste.sync.isValidWholeFilePayload
import com.crosspaste.utils.getCodecsUtils
import okio.Path.Companion.toPath
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

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

    @Test
    fun `whole-file payload requires matching size and hash`() {
        val payload = "expected".encodeToByteArray()
        val expected = SingleFileInfoTree(payload.size.toLong(), getCodecsUtils().hash(payload))

        assertTrue(isValidWholeFilePayload(expected, payload))
        assertFalse(isValidWholeFilePayload(expected, "tampered".encodeToByteArray()))
        assertFalse(isValidWholeFilePayload(expected, payload + 0))
    }
}
