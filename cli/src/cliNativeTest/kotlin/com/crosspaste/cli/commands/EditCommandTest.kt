package com.crosspaste.cli.commands

import com.crosspaste.cli.CrossPasteCommand
import com.github.ajalt.clikt.testing.test
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNull

class EditCommandTest {

    // An editor needs the terminal on both ends: a piped stdin or a redirected
    // stdout must fail fast as a usage error before any network round-trip.
    // The Clikt harness reports UsageError's raw status 1; main() remaps to 2.

    @Test
    fun editRefusesANonInteractiveStdin() {
        val result = CrossPasteCommand().test("edit", inputInteractive = false)
        assertEquals(1, result.statusCode)
        assertContains(result.stderr, "edit requires an interactive terminal")
        // The usage error must carry the subcommand's context, not the root's
        assertContains(result.stderr, " edit [<options>]")
    }

    @Test
    fun editRefusesARedirectedStdout() {
        val result =
            CrossPasteCommand().test(
                "edit 42",
                inputInteractive = true,
                outputInteractive = false,
            )
        assertEquals(1, result.statusCode)
        assertContains(result.stderr, "edit requires an interactive terminal")
    }

    @Test
    fun visualWinsOverEditor() {
        val env = mapOf("VISUAL" to "code --wait", "EDITOR" to "vi")
        assertEquals("code --wait", resolveEditor { env[it] })
    }

    @Test
    fun editorIsTheFallbackAndBlankValuesAreIgnored() {
        val env = mapOf("VISUAL" to "  ", "EDITOR" to "nano")
        assertEquals("nano", resolveEditor { env[it] })
        assertNull(resolveEditor { null })
    }

    @Test
    fun editorCommandQuotesOnlyTheFilePath() {
        assertEquals(
            "code --wait \"/tmp dir/crosspaste-edit-7.txt\"",
            buildEditorCommand("code --wait", "/tmp dir/crosspaste-edit-7.txt"),
        )
    }

    @Test
    fun tempFileExtensionFollowsThePasteType() {
        assertEquals("crosspaste-edit-1.html", editTempFileName(1, "html"))
        assertEquals("crosspaste-edit-2.rtf", editTempFileName(2, "rtf"))
        assertEquals("crosspaste-edit-3.txt", editTempFileName(3, "text"))
        assertEquals("crosspaste-edit-4.txt", editTempFileName(4, "link"))
    }

    @Test
    fun imagesAndFilesAreNotEditable() {
        assertNull(editableContent(detail(typeName = "image", content = "shot.png")))
        assertNull(editableContent(detail(typeName = "file", content = "a.txt")))
    }

    @Test
    fun rawMarkupIsPreferredOverTheSummary() {
        assertEquals(
            "<b>hi</b>",
            editableContent(detail(typeName = "html", content = "hi", rawContent = "<b>hi</b>")),
        )
        // An app that predates includeRaw returns no rawContent: fall back
        assertEquals(
            "hi",
            editableContent(detail(typeName = "text", content = "hi", rawContent = null)),
        )
    }

    @Test
    fun editorAddedTrailingNewlineDoesNotCountAsAChange() {
        // vi terminates the file with a newline the original never had
        assertEquals("foo", normalizeEditedContent(original = "foo", edited = "foo\n"))
        assertEquals("foo", normalizeEditedContent(original = "foo", edited = "foo\r\n"))
        assertEquals("bar", normalizeEditedContent(original = "foo", edited = "bar\n"))
    }

    @Test
    fun originalTrailingNewlineIsPreservedVerbatim() {
        assertEquals("foo\n", normalizeEditedContent(original = "foo\n", edited = "foo\n"))
        assertEquals("bar\n\n", normalizeEditedContent(original = "foo\n", edited = "bar\n\n"))
    }

    private fun detail(
        typeName: String,
        content: String?,
        rawContent: String? = null,
    ): PasteDetailResponse =
        PasteDetailResponse(
            id = 1,
            typeName = typeName,
            source = null,
            size = 0,
            tagged = false,
            createTime = 0,
            remote = false,
            hash = "h",
            content = content,
            rawContent = rawContent,
        )
}
