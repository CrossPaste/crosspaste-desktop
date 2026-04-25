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
    fun buildManifest_normalizesWindowsBackslashesInPath() {
        val manifest =
            NativeMessagingHostService.buildManifest(
                extensionIds = listOf("aaa"),
                bridgeScriptPath = "C:\\Users\\me\\data\\native-messaging-host.bat",
            )

        val parsed = Json.parseToJsonElement(manifest).jsonObject
        assertEquals(
            "C:/Users/me/data/native-messaging-host.bat",
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
    fun buildWindowsBridgeScript_normalizesForwardSlashesAndContainsEchoOff() {
        val script =
            NativeMessagingHostService.buildWindowsBridgeScript("C:/Users/me/data/crosspaste.pid")

        assertTrue(script.startsWith("@echo off"), "missing @echo off")
        assertTrue(
            script.contains("\$pidFile='C:\\Users\\me\\data\\crosspaste.pid'"),
            "pidFile path not normalized to backslashes",
        )
    }
}
