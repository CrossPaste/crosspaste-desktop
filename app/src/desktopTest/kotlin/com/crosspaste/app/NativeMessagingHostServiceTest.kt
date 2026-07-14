package com.crosspaste.app

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.Properties
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NativeMessagingHostServiceTest {

    @Test
    fun parseExtensionIds_emptyOrMissing() {
        assertEquals(emptyList(), NativeMessagingHostService.parseExtensionIds(Properties()))

        val empty = Properties().apply { setProperty("chrome.extension.ids", "") }
        assertEquals(emptyList(), NativeMessagingHostService.parseExtensionIds(empty))
    }

    @Test
    fun parseExtensionIds_singleAndMultiple() {
        val single = Properties().apply { setProperty("chrome.extension.ids", "abc123") }
        assertEquals(listOf("abc123"), NativeMessagingHostService.parseExtensionIds(single))

        val multiple =
            Properties().apply {
                setProperty("chrome.extension.ids", "aaa,bbb,ccc")
            }
        assertEquals(
            listOf("aaa", "bbb", "ccc"),
            NativeMessagingHostService.parseExtensionIds(multiple),
        )
    }

    @Test
    fun parseExtensionIds_trimsWhitespaceAndDropsEmpties() {
        val messy =
            Properties().apply {
                setProperty("chrome.extension.ids", " aaa , , bbb ,,ccc, ")
            }
        assertEquals(
            listOf("aaa", "bbb", "ccc"),
            NativeMessagingHostService.parseExtensionIds(messy),
        )
    }

    @Test
    fun buildManifest_isParseableJsonWithRequiredFields() {
        val manifest =
            NativeMessagingHostService.buildManifest(
                extensionIds = listOf("aaa", "bbb"),
                bridgeScriptPath = "/Users/me/data/native-messaging-host.sh",
            )

        val parsed = Json.parseToJsonElement(manifest).jsonObject
        assertEquals(
            NativeMessagingHostService.HOST_NAME,
            parsed.getValue("name").jsonPrimitive.content,
        )
        assertEquals("stdio", parsed.getValue("type").jsonPrimitive.content)
        assertEquals(
            "/Users/me/data/native-messaging-host.sh",
            parsed.getValue("path").jsonPrimitive.content,
        )

        val origins: JsonArray = parsed.getValue("allowed_origins").jsonArray
        assertEquals(
            listOf("chrome-extension://aaa/", "chrome-extension://bbb/"),
            origins.map { it.jsonPrimitive.content },
        )
    }

    @Test
    fun buildManifest_preservesWindowsBackslashesInPath() {
        val manifest =
            NativeMessagingHostService.buildManifest(
                extensionIds = listOf("aaa"),
                bridgeScriptPath = "C:\\Users\\me\\data\\native-messaging-host.bat",
            )

        // cmd.exe (which Chromium uses to launch .bat hosts) cannot execute
        // forward-slash paths, so the manifest must keep native backslashes.
        val parsed = Json.parseToJsonElement(manifest).jsonObject
        assertEquals(
            "C:\\Users\\me\\data\\native-messaging-host.bat",
            parsed.getValue("path").jsonPrimitive.content,
        )
    }

    @Test
    fun buildManifest_emptyExtensionIdsProducesValidEmptyArray() {
        val manifest =
            NativeMessagingHostService.buildManifest(
                extensionIds = emptyList(),
                bridgeScriptPath = "/tmp/host.sh",
            )

        val parsed = Json.parseToJsonElement(manifest).jsonObject
        assertEquals(0, parsed.getValue("allowed_origins").jsonArray.size)
    }

    @Test
    fun buildUnixBridgeScript_hasShebangAndPidFileSubstitution() {
        val script = NativeMessagingHostService.buildUnixBridgeScript("/tmp/crosspaste.pid")

        assertTrue(script.startsWith("#!/bin/bash"), "missing shebang")
        assertTrue(
            script.contains("PID_FILE=\"/tmp/crosspaste.pid\""),
            "PID_FILE not substituted",
        )
        assertTrue(
            script.contains("send_message '{\"status\":\"running\"}'"),
            "status payload missing",
        )
    }

    @Test
    fun buildWindowsBridgeScript_locatesPidFileViaScriptDirectory() {
        val script =
            NativeMessagingHostService.buildWindowsBridgeScript(DesktopPidFileService.PID_FILE_NAME)

        assertTrue(script.startsWith("@echo off"), "missing @echo off")
        // The pid file path must be derived from %~dp0 at run time: the script
        // is saved as UTF-8 but cmd.exe reads it in the OEM code page, so a
        // literal non-ASCII profile path would be garbled.
        assertTrue(
            script.contains("\$pidFile='%~dp0crosspaste.pid'"),
            "pidFile must be resolved via %~dp0",
        )
    }

    @Test
    fun buildWindowsBridgeScript_drainsStdinWithoutScriptBlockTask() {
        val script =
            NativeMessagingHostService.buildWindowsBridgeScript(DesktopPidFileService.PID_FILE_NAME)

        // A ScriptBlock passed to Task::Run fails overload resolution on
        // Windows PowerShell 5.1 ("multiple ambiguous overloads found").
        assertTrue(
            !script.contains("[System.Threading.Tasks.Task]::Run"),
            "must not drain stdin via Task::Run(ScriptBlock)",
        )
        assertTrue(
            script.contains("CopyToAsync([System.IO.Stream]::Null)"),
            "stdin must be drained via CopyToAsync",
        )
    }

    @Test
    fun buildWindowsBridgeScript_doesNotAssignReadOnlyPidVariable() {
        val script =
            NativeMessagingHostService.buildWindowsBridgeScript(DesktopPidFileService.PID_FILE_NAME)

        // PowerShell's $PID is a read-only automatic variable; assigning to it throws.
        assertTrue(!script.contains("\$pid="), "script must not assign to read-only \$pid")
        assertTrue(script.contains("\$appPid="), "desktop pid must be read into \$appPid")
        assertTrue(script.contains("Get-Process -Id \$appPid"), "liveness check must use \$appPid")
    }

    @Test
    fun windowsRegistryKeys_coverChromiumFamilyAndEndWithHostName() {
        val keys = NativeMessagingHostService.WINDOWS_REGISTRY_KEYS

        assertTrue(keys.isNotEmpty())
        assertTrue(keys.all { it.endsWith("\\${NativeMessagingHostService.HOST_NAME}") })
        assertTrue(keys.any { it.contains("Google\\Chrome") }, "missing Chrome key")
        assertTrue(keys.any { it.contains("Microsoft\\Edge") }, "missing Edge key")
        assertTrue(keys.any { it.contains("BraveSoftware") }, "missing Brave key")
    }
}
