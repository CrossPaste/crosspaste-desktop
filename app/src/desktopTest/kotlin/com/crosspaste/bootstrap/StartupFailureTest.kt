package com.crosspaste.bootstrap

import java.io.IOException
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class StartupFailureTest {

    @Test
    fun `writeLog creates the log dir and appends each failure with its stack trace`() {
        val logDir = Files.createTempDirectory("startup-failure").toFile().resolve("logs")

        val first = StartupFailure.writeLog(IOException("metadata locked"), logDir)
        StartupFailure.writeLog(IllegalStateException("second"), logDir)

        assertNotNull(first)
        assertEquals(StartupFailure.FILE_NAME, first.name)
        val content = first.readText()
        assertTrue(content.contains("java.io.IOException: metadata locked"))
        assertTrue(content.contains("at com.crosspaste.bootstrap.StartupFailureTest"))
        assertTrue(content.contains("java.lang.IllegalStateException: second"), "earlier failures are kept")
    }
}
