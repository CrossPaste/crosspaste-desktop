package com.crosspaste.utils

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FileNameNormalizerTest {

    @Test
    fun testBasicFileNames() {
        assertEquals("test.txt", FileNameNormalizer.normalize("test.txt"))
        assertEquals("document.pdf", FileNameNormalizer.normalize("document.pdf"))
        assertEquals("image.png", FileNameNormalizer.normalize("image.png"))
    }

    @Test
    fun testFileNameWithExtensionPreservation() {
        // This test reveals the bug: data.zip becomes datazip
        assertEquals("data.zip", FileNameNormalizer.normalize("data.zip"))
        assertEquals("archive.tar.gz", FileNameNormalizer.normalize("archive.tar.gz"))
        assertEquals("backup.7z", FileNameNormalizer.normalize("backup.7z"))
        assertEquals("script.js", FileNameNormalizer.normalize("script.js"))
        assertEquals("style.css", FileNameNormalizer.normalize("style.css"))
    }

    @Test
    fun testIllegalCharacterReplacement() {
        assertEquals("test_file.txt", FileNameNormalizer.normalize("test<file.txt"))
        assertEquals("test_file.txt", FileNameNormalizer.normalize("test>file.txt"))
        assertEquals("test_file.txt", FileNameNormalizer.normalize("test:file.txt"))
        assertEquals("test_file.txt", FileNameNormalizer.normalize("test\"file.txt"))
        assertEquals("test_file.txt", FileNameNormalizer.normalize("test|file.txt"))
        assertEquals("test_file.txt", FileNameNormalizer.normalize("test?file.txt"))
        assertEquals("test_file.txt", FileNameNormalizer.normalize("test*file.txt"))
        assertEquals("test_file.txt", FileNameNormalizer.normalize("test\\file.txt"))
        assertEquals("test_file.txt", FileNameNormalizer.normalize("test/file.txt"))
    }

    @Test
    fun testControlCharacterReplacement() {
        assertEquals("test_file.txt", FileNameNormalizer.normalize("test\u0000file.txt"))
        assertEquals("test_file.txt", FileNameNormalizer.normalize("test\u0001file.txt"))
        assertEquals("test_file.txt", FileNameNormalizer.normalize("test\u001Ffile.txt"))
        assertEquals("test_file.txt", FileNameNormalizer.normalize("test\u007Ffile.txt"))
    }

    @Test
    fun testWindowsReservedNames() {
        assertEquals("CON_", FileNameNormalizer.normalize("CON"))
        assertEquals("PRN_.txt", FileNameNormalizer.normalize("PRN.txt"))
        assertEquals("AUX_", FileNameNormalizer.normalize("AUX"))
        assertEquals("NUL_.log", FileNameNormalizer.normalize("NUL.log"))
        assertEquals("COM1_", FileNameNormalizer.normalize("COM1"))
        assertEquals("COM9_.cfg", FileNameNormalizer.normalize("COM9.cfg"))
        assertEquals("LPT1_", FileNameNormalizer.normalize("LPT1"))
        assertEquals("LPT9_.txt", FileNameNormalizer.normalize("LPT9.txt"))
        // Case-insensitive
        assertEquals("con_", FileNameNormalizer.normalize("con"))
        assertEquals("prn_.txt", FileNameNormalizer.normalize("prn.txt"))
    }

    @Test
    fun testSpaceAndDotTrimming() {
        assertEquals("test.txt", FileNameNormalizer.normalize(" test.txt "))
        assertEquals("test.txt", FileNameNormalizer.normalize(".test.txt."))
        assertEquals("test.txt", FileNameNormalizer.normalize("  ..test.txt..  "))
        assertEquals("unnamed", FileNameNormalizer.normalize("   "))
        assertEquals("file", FileNameNormalizer.normalize("..."))
    }

    @Test
    fun testEmptyAndBlankNames() {
        assertEquals("unnamed", FileNameNormalizer.normalize(""))
        assertEquals("unnamed", FileNameNormalizer.normalize("   "))
        assertEquals("unnamed", FileNameNormalizer.normalize("\t\n"))
    }

    @Test
    fun testExtensionNormalization() {
        assertEquals("test.tx_t", FileNameNormalizer.normalize("test.tx<t"))
        assertEquals("test.t_xt", FileNameNormalizer.normalize("test.t|xt"))
        assertEquals("test._xt", FileNameNormalizer.normalize("test.*xt"))
    }

    @Test
    fun testCustomReplacementCharacter() {
        assertEquals("test-file.txt", FileNameNormalizer.normalize("test<file.txt", '-'))
        assertEquals("test@file.txt", FileNameNormalizer.normalize("test|file.txt", '@'))
        assertEquals("test+file.txt", FileNameNormalizer.normalize("test?file.txt", '+'))
    }

    @Test
    fun testExtensionPreservationFlag() {
        // With extension preservation (default)
        assertEquals("data.zip", FileNameNormalizer.normalize("data.zip", preserveExtension = true))
        assertEquals("data.zip", FileNameNormalizer.normalize("data.zip", preserveExtension = false))

        // Multiple dots - only the last one is treated as extension separator
        assertEquals("archive.tar.gz", FileNameNormalizer.normalize("archive.tar.gz", preserveExtension = true))
        assertEquals("archive.tar.gz", FileNameNormalizer.normalize("archive.tar.gz", preserveExtension = false))
    }

    @Test
    fun testLengthLimiting() {
        val longName = "a".repeat(300) + ".txt"
        val normalized = FileNameNormalizer.normalize(longName)
        assertTrue(normalized.encodeToByteArray().size <= 255)
        assertTrue(normalized.endsWith(".txt"))
    }

    @Test
    fun testUnicodeHandling() {
        assertEquals("测试文件.txt", FileNameNormalizer.normalize("测试文件.txt"))
        assertEquals("файл.pdf", FileNameNormalizer.normalize("файл.pdf"))
        assertEquals("ファイル.doc", FileNameNormalizer.normalize("ファイル.doc"))
        assertEquals("🚀_test.txt", FileNameNormalizer.normalize("🚀<test.txt"))
    }

    @Test
    fun testSpecialEdgeCases() {
        // File with only extension - should add unnamed prefix and preserve extension
        assertEquals("unnamed.txt", FileNameNormalizer.normalize(".txt"))
        assertEquals("unnamed.pdf", FileNameNormalizer.normalize(".pdf"))
        assertEquals("unnamed.config", FileNameNormalizer.normalize(".config"))

        // Multiple consecutive illegal chars
        assertEquals("test____file.txt", FileNameNormalizer.normalize("test<>|?file.txt"))

        // File ending with space before extension
        assertEquals("test.txt", FileNameNormalizer.normalize("test .txt"))

        // File with dots in name but no real extension
        assertEquals("version.1.2.3", FileNameNormalizer.normalize("version.1.2.3"))

        // Test extension preservation disabled for dot files
        assertEquals("txt", FileNameNormalizer.normalize(".txt", preserveExtension = false))
    }

    @Test
    fun testValidationFunction() {
        // Valid names
        assertTrue(FileNameNormalizer.isValid("test.txt"))
        assertTrue(FileNameNormalizer.isValid("document.pdf"))
        assertTrue(FileNameNormalizer.isValid("image123.png"))
        assertTrue(FileNameNormalizer.isValid("valid_name.zip"))

        // Invalid names
        assertFalse(FileNameNormalizer.isValid(""))
        assertFalse(FileNameNormalizer.isValid("   "))
        assertFalse(FileNameNormalizer.isValid("test<file.txt"))
        assertFalse(FileNameNormalizer.isValid("test>file.txt"))
        assertFalse(FileNameNormalizer.isValid("CON"))
        assertFalse(FileNameNormalizer.isValid("PRN.txt"))
        assertFalse(FileNameNormalizer.isValid(" test.txt"))
        assertFalse(FileNameNormalizer.isValid("test.txt "))
        assertFalse(FileNameNormalizer.isValid(".test.txt"))
        assertFalse(FileNameNormalizer.isValid("test.txt."))
        assertFalse(FileNameNormalizer.isValid("test\u0000file.txt"))

        // Length validation
        val longName = "a".repeat(300)
        assertFalse(FileNameNormalizer.isValid(longName))
    }

    @Test
    fun testRealWorldExamples() {
        // Common problematic filenames
        assertEquals("Document_ Title.docx", FileNameNormalizer.normalize("Document: Title.docx"))
        assertEquals("File_Backup_2023.zip", FileNameNormalizer.normalize("File|Backup|2023.zip"))
        assertEquals("Project_v1.2_final.pdf", FileNameNormalizer.normalize("Project*v1.2*final.pdf"))
        assertEquals("Data_Analysis_.xlsx", FileNameNormalizer.normalize("Data<Analysis>.xlsx"))
        assertEquals("Meeting Notes_Jan_15.txt", FileNameNormalizer.normalize("Meeting Notes/Jan/15.txt"))

        // Names that should remain unchanged
        assertEquals("valid-filename.txt", FileNameNormalizer.normalize("valid-filename.txt"))
        assertEquals("file_name_123.pdf", FileNameNormalizer.normalize("file_name_123.pdf"))
        assertEquals("Document-v2.docx", FileNameNormalizer.normalize("Document-v2.docx"))
    }

    @Test
    fun testBugReproduction() {
        // This test specifically reproduces the reported bug
        // Expected: "data.zip", Actual (buggy): "datazip"
        val result = FileNameNormalizer.normalize("data.zip")
        assertEquals("data.zip", result, "Bug: dot should be preserved in filename with extension")

        // Additional cases that should preserve dots correctly
        assertEquals("test.file.zip", FileNameNormalizer.normalize("test.file.zip"))
        assertEquals("my.data.backup.tar.gz", FileNameNormalizer.normalize("my.data.backup.tar.gz"))
        assertEquals("config.v1.2.json", FileNameNormalizer.normalize("config.v1.2.json"))
    }
}
