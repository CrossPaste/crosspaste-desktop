package com.crosspaste.app

import com.crosspaste.path.AppPathProvider
import com.crosspaste.platform.Platform
import com.crosspaste.presist.FilePersist
import com.sun.jna.platform.win32.Advapi32Util
import com.sun.jna.platform.win32.WinReg
import io.github.oshai.kotlinlogging.KotlinLogging
import okio.Path
import java.util.Properties

class NativeMessagingHostService(
    private val appPathProvider: AppPathProvider,
    private val pidFileService: DesktopPidFileService,
    private val platform: Platform,
) {

    private val logger = KotlinLogging.logger {}

    companion object {
        const val HOST_NAME = "com.crosspaste.desktop"
        const val MANIFEST_FILE = "$HOST_NAME.json"

        // On Windows, Chromium-family browsers discover native messaging hosts
        // ONLY through the registry — manifest files inside the browser profile
        // are never scanned (https://developer.chrome.com/docs/extensions/develop/concepts/native-messaging).
        // Edge and Brave also fall back to the Chrome key, but registering their
        // own vendor keys keeps discovery independent of that fallback.
        internal val WINDOWS_REGISTRY_KEYS =
            listOf(
                "Software\\Google\\Chrome\\NativeMessagingHosts\\$HOST_NAME",
                "Software\\Chromium\\NativeMessagingHosts\\$HOST_NAME",
                "Software\\Microsoft\\Edge\\NativeMessagingHosts\\$HOST_NAME",
                "Software\\BraveSoftware\\Brave-Browser\\NativeMessagingHosts\\$HOST_NAME",
            )

        val CHROME_EXTENSION_IDS: List<String> by lazy {
            val props = Properties()
            NativeMessagingHostService::class.java
                .getResourceAsStream("/native-messaging.properties")
                ?.use { props.load(it) }
            parseExtensionIds(props)
        }

        internal fun parseExtensionIds(properties: Properties): List<String> =
            properties
                .getProperty("chrome.extension.ids", "")
                .split(",")
                .map { it.trim() }
                .filter { it.isNotEmpty() }

        // The path must keep native separators: Chromium launches non-.exe hosts
        // via `cmd.exe /d /s /c "<path> <origin>"` (launch_context_win.cc), and
        // cmd.exe cannot execute a forward-slash path — so on Windows the
        // backslashes are JSON-escaped rather than normalized to "/".
        internal fun buildManifest(
            extensionIds: List<String>,
            bridgeScriptPath: String,
        ): String {
            val origins = extensionIds.joinToString(", ") { "\"chrome-extension://$it/\"" }
            return """
                {
                  "name": "$HOST_NAME",
                  "description": "CrossPaste Desktop Native Messaging Host",
                  "path": "${bridgeScriptPath.replace("\\", "\\\\")}",
                  "type": "stdio",
                  "allowed_origins": [$origins]
                }
                """.trimIndent()
        }

        internal fun buildUnixBridgeScript(pidFilePath: String): String =
            """
            #!/bin/bash
            cat > /dev/null &
            PID_FILE="$pidFilePath"
            send_message() {
                local msg="${'$'}1"
                local len=${'$'}{#msg}
                printf "\\x$(printf '%02x' ${'$'}((len & 0xFF)))\\x$(printf '%02x' ${'$'}(((len >> 8) & 0xFF)))\\x$(printf '%02x' ${'$'}(((len >> 16) & 0xFF)))\\x$(printf '%02x' ${'$'}(((len >> 24) & 0xFF)))"
                printf '%s' "${'$'}msg"
            }
            is_running() {
                [ -f "${'$'}PID_FILE" ] || return 1
                local pid
                pid=${'$'}(cat "${'$'}PID_FILE" 2>/dev/null) || return 1
                kill -0 "${'$'}pid" 2>/dev/null
            }
            while true; do
                if ! is_running; then
                    exit 0
                fi
                send_message '{"status":"running"}'
                sleep 5
            done
            """.trimIndent()

        // NOTE: PowerShell's $PID is a read-only automatic variable — assigning to it
        // throws, so the desktop process id must live in its own variable ($appPid).
        // The pid file is located via %~dp0 (the script's own directory — both files
        // live in pasteUserPath): the script is saved as UTF-8 but cmd.exe reads it
        // in the OEM code page, so a literal non-ASCII path (e.g. a Chinese user
        // profile) would be garbled, while %~dp0 expands at run time in Unicode.
        // Stdin draining uses CopyToAsync because a ScriptBlock passed to
        // Task::Run fails overload resolution on Windows PowerShell 5.1.
        internal fun buildWindowsBridgeScript(pidFileName: String): String =
            """
            @echo off
            powershell -NoProfile -Command "${'$'}stdin=[Console]::OpenStandardInput(); ${'$'}null=${'$'}stdin.CopyToAsync([System.IO.Stream]::Null); ${'$'}stdout=[Console]::OpenStandardOutput(); ${'$'}msg=[System.Text.Encoding]::UTF8.GetBytes('{\"status\":\"running\"}'); ${'$'}lenBytes=[System.BitConverter]::GetBytes(${'$'}msg.Length); ${'$'}pidFile='%~dp0$pidFileName'; while(${'$'}true){ if(-not(Test-Path ${'$'}pidFile)){exit}; ${'$'}appPid=[int](Get-Content ${'$'}pidFile -ErrorAction SilentlyContinue); if(-not(Get-Process -Id ${'$'}appPid -ErrorAction SilentlyContinue)){exit}; ${'$'}stdout.Write(${'$'}lenBytes,0,4); ${'$'}stdout.Write(${'$'}msg,0,${'$'}msg.Length); ${'$'}stdout.Flush(); Start-Sleep -Seconds 5 }"
            """.trimIndent()
    }

    fun register() {
        runCatching {
            val bridgeScriptPath = writeBridgeScript()
            writeManifests(bridgeScriptPath)
            logger.info { "Native messaging host registered" }
        }.onFailure { e ->
            logger.error(e) { "Failed to register native messaging host" }
        }
    }

    private fun writeBridgeScript(): Path {
        val scriptPath = getBridgeScriptPath()
        val content =
            if (platform.isWindows()) {
                buildWindowsBridgeScript(DesktopPidFileService.PID_FILE_NAME)
            } else {
                buildUnixBridgeScript(pidFileService.pidFilePath.toString())
            }
        FilePersist
            .createOneFilePersist(scriptPath)
            .saveBytes(content.encodeToByteArray())
        if (!platform.isWindows()) {
            scriptPath.toFile().setExecutable(true)
        }
        return scriptPath
    }

    private fun writeManifests(bridgeScriptPath: Path) {
        val manifest = buildManifest(CHROME_EXTENSION_IDS, bridgeScriptPath.toString())

        if (platform.isWindows()) {
            registerWindowsManifest(manifest)
            return
        }

        for (dir in getManifestDirs()) {
            val dirFile = dir.toFile()
            if (!dirFile.parentFile.exists()) continue
            dirFile.mkdirs()
            runCatching {
                FilePersist
                    .createOneFilePersist(dir.resolve(MANIFEST_FILE))
                    .saveBytes(manifest.encodeToByteArray())
            }.onFailure { e ->
                logger.warn(e) { "Failed to write manifest to $dir" }
            }
        }
    }

    // Windows browsers resolve native messaging hosts via the registry, not via
    // manifest files in the profile directory: write one manifest under the app
    // data dir and point each vendor's HKCU key at it.
    private fun registerWindowsManifest(manifest: String) {
        val manifestPath = appPathProvider.pasteUserPath.resolve(MANIFEST_FILE)
        FilePersist
            .createOneFilePersist(manifestPath)
            .saveBytes(manifest.encodeToByteArray())
        val manifestFilePath = manifestPath.toFile().absolutePath

        for (keyPath in WINDOWS_REGISTRY_KEYS) {
            runCatching {
                Advapi32Util.registryCreateKey(WinReg.HKEY_CURRENT_USER, keyPath)
                Advapi32Util.registrySetStringValue(
                    WinReg.HKEY_CURRENT_USER,
                    keyPath,
                    "",
                    manifestFilePath,
                )
            }.onFailure { e ->
                logger.warn(e) { "Failed to register native messaging host key: $keyPath" }
            }
        }
    }

    private fun getBridgeScriptPath(): Path {
        val dataDir = appPathProvider.pasteUserPath
        return if (platform.isWindows()) {
            dataDir.resolve("native-messaging-host.bat")
        } else {
            dataDir.resolve("native-messaging-host.sh")
        }
    }

    private fun getManifestDirs(): List<Path> {
        val home = appPathProvider.userHome
        return if (platform.isMacos()) {
            listOf(
                "Library/Application Support/Google/Chrome/NativeMessagingHosts",
                "Library/Application Support/Chromium/NativeMessagingHosts",
                "Library/Application Support/BraveSoftware/Brave-Browser/NativeMessagingHosts",
                "Library/Application Support/Microsoft Edge/NativeMessagingHosts",
                "Library/Application Support/Arc/User Data/NativeMessagingHosts",
            ).map { home.resolve(it) }
        } else if (platform.isLinux()) {
            listOf(
                ".config/google-chrome/NativeMessagingHosts",
                ".config/chromium/NativeMessagingHosts",
                ".config/BraveSoftware/Brave-Browser/NativeMessagingHosts",
                ".config/microsoft-edge/NativeMessagingHosts",
            ).map { home.resolve(it) }
        } else {
            emptyList()
        }
    }
}
