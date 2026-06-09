package com.crosspaste.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WindowsZipUpdaterTest {

    private val fileName = "crosspaste-2.1.3-2297-windows-amd64.zip"

    private val expectedHash = "91100db3f6e84b7900000000000000000000000000000000000000000000abcd"

    @Test
    fun `parses the digest for the matching file from a shasum listing`() {
        val checksum =
            """
            deadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeef  crosspaste.dmg
            $expectedHash  $fileName
            cafebabecafebabecafebabecafebabecafebabecafebabecafebabecafebabe  crosspaste_2.1.3-2297_amd64.deb
            """.trimIndent()

        assertEquals(expectedHash, WindowsZipUpdater.parseChecksum(checksum, fileName))
    }

    @Test
    fun `tolerates blank lines and trailing whitespace`() {
        val checksum = "\n  $expectedHash  $fileName  \n\n"
        assertEquals(expectedHash, WindowsZipUpdater.parseChecksum(checksum, fileName))
    }

    @Test
    fun `returns null when the file is absent from the listing`() {
        val checksum = "deadbeef  some-other-file.zip"
        assertNull(WindowsZipUpdater.parseChecksum(checksum, fileName))
    }

    @Test
    fun `does not match a filename that only shares a prefix`() {
        val checksum = "$expectedHash  crosspaste-2.1.3-2297-windows-amd64.zip.sig"
        assertNull(WindowsZipUpdater.parseChecksum(checksum, fileName))
    }

    @Test
    fun `treats localhost and loopback addresses as loopback`() {
        assertTrue(WindowsZipUpdater.isLoopbackHost("http://localhost:8077"))
        assertTrue(WindowsZipUpdater.isLoopbackHost("http://127.0.0.1:8077"))
        assertTrue(WindowsZipUpdater.isLoopbackHost("http://[::1]:8077/metadata.properties"))
    }

    @Test
    fun `treats remote hosts as non-loopback`() {
        assertEquals(false, WindowsZipUpdater.isLoopbackHost("https://github.com/CrossPaste/x"))
        assertEquals(false, WindowsZipUpdater.isLoopbackHost("https://oss.crosspaste.com/2.1.5"))
        assertEquals(false, WindowsZipUpdater.isLoopbackHost("not a url"))
    }
}
