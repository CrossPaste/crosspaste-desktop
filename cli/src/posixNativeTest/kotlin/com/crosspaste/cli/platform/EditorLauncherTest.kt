package com.crosspaste.cli.platform

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import okio.FileSystem
import okio.Path
import okio.SYSTEM
import platform.posix.S_IRWXG
import platform.posix.S_IRWXO
import platform.posix.S_IRWXU
import platform.posix.stat
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull

class EditorLauncherTest {

    @Test
    fun shellQuotingNeutralizesExpansionCharacters() {
        assertEquals("'/tmp/plain.txt'", shellQuotePath("/tmp/plain.txt"))
        assertEquals("'/tmp/with space/f.txt'", shellQuotePath("/tmp/with space/f.txt"))
        // $, backticks and globs must not expand: single quotes cover them all
        assertEquals("'/tmp/\$HOME/`id`/*.txt'", shellQuotePath("/tmp/\$HOME/`id`/*.txt"))
        // An embedded single quote uses the close-escape-reopen idiom
        assertEquals("'/tmp/it'\\''s.txt'", shellQuotePath("/tmp/it's.txt"))
    }

    @Test
    fun tempDirIsCreatedPrivateAndUnique() {
        val first = assertNotNull(createEditTempDir())
        val second = assertNotNull(createEditTempDir())
        try {
            assertNotEquals(first, second)
            assertEquals(S_IRWXU, permissionsOf(first))
        } finally {
            FileSystem.SYSTEM.deleteRecursively(first)
            FileSystem.SYSTEM.deleteRecursively(second)
        }
    }

    @Test
    fun editorSeesAPathFullOfShellMetacharacters() {
        val dir = assertNotNull(createEditTempDir())
        try {
            // Every character here would break naive double-quoting: sh would
            // expand $(...) and `...` even inside double quotes
            val wicked = dir / "a \$(reboot) `id` 'quote\".txt"
            FileSystem.SYSTEM.write(wicked) { writeUtf8("payload") }
            // cat only exits 0 when it opened exactly this file
            assertEquals(0, runEditor("cat > /dev/null", wicked.toString()))
            // A failing editor propagates a non-zero status
            assertNotEquals(0, runEditor("false", wicked.toString()))
        } finally {
            FileSystem.SYSTEM.deleteRecursively(dir)
        }
    }

    @OptIn(ExperimentalForeignApi::class)
    private fun permissionsOf(path: Path): Int =
        memScoped {
            val st = alloc<stat>()
            assertEquals(0, stat(path.toString(), st.ptr))
            (st.st_mode.toInt()) and (S_IRWXU or S_IRWXG or S_IRWXO)
        }
}
