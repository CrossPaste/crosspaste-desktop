package com.crosspaste.cli.platform

import okio.Path.Companion.toPath
import kotlin.test.Test
import kotlin.test.assertEquals

class AppLauncherTest {

    @Test
    fun linuxGuiLaunchCommandHasNoHeadlessFlag() {
        val command = buildLinuxLaunchCommand("/opt/crosspaste/bin/crosspaste".toPath(), headless = false)
        assertEquals("nohup \"/opt/crosspaste/bin/crosspaste\" > /dev/null 2>&1 &", command)
    }

    @Test
    fun linuxHeadlessLaunchCommandPassesHeadlessFlag() {
        val command = buildLinuxLaunchCommand("/opt/crosspaste/bin/crosspaste".toPath(), headless = true)
        assertEquals("nohup \"/opt/crosspaste/bin/crosspaste\" --headless > /dev/null 2>&1 &", command)
    }
}
