package com.crosspaste.app

import com.crosspaste.path.AppPathProvider
import com.crosspaste.platform.Platform
import com.crosspaste.presist.FilePersist
import io.github.oshai.kotlinlogging.KotlinLogging
import okio.Path
import okio.Path.Companion.toOkioPath
import java.io.File
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

        val CHROME_EXTENSION_IDS: List<String> by lazy {
            val props = Properties()
            NativeMessagingHostService::class.java
                .getResourceAsStream("/native-messaging.properties")
                ?.use { props.load(it) }
            props
                .getProperty("chrome.extension.ids", "")
                .split(",")
                .map { it.trim() }
                .filter { it.isNotEmpty() }
        }
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
                windowsBridgeScript()
            } else {
                unixBridgeScript()
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
        val origins = CHROME_EXTENSION_IDS.joinToString(", ") { "\"chrome-extension://$it/\"" }
        val manifest =
            """
            {
              "name": "$HOST_NAME",
              "description": "CrossPaste Desktop Native Messaging Host",
              "path": "${bridgeScriptPath.toString().replace("\\", "/")}",
              "type": "stdio",
              "allowed_origins": [$origins]
            }
            """.trimIndent()

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
        } else if (platform.isWindows()) {
            windowsManifestDirs()
        } else {
            emptyList()
        }
    }

    private fun windowsManifestDirs(): List<Path> {
        val localAppData = System.getenv("LOCALAPPDATA") ?: return emptyList()
        return listOf(
            "Google/Chrome/User Data/NativeMessagingHosts",
            "Chromium/User Data/NativeMessagingHosts",
            "BraveSoftware/Brave-Browser/User Data/NativeMessagingHosts",
            "Microsoft/Edge/User Data/NativeMessagingHosts",
        ).map { File(localAppData).resolve(it).toOkioPath() }
    }

    private fun unixBridgeScript(): String {
        val pidFilePath = pidFileService.pidFilePath.toString()
        return """
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
    }

    private fun windowsBridgeScript(): String {
        val pidFilePath = pidFileService.pidFilePath.toString().replace("/", "\\")
        return """
            @echo off
            powershell -NoProfile -Command "${'$'}stdin=[Console]::OpenStandardInput(); ${'$'}null=[System.Threading.Tasks.Task]::Run({${'$'}buf=New-Object byte[] 4096; while(${'$'}stdin.Read(${'$'}buf,0,4096) -gt 0){}}); ${'$'}stdout=[Console]::OpenStandardOutput(); ${'$'}msg=[System.Text.Encoding]::UTF8.GetBytes('{\"status\":\"running\"}'); ${'$'}lenBytes=[System.BitConverter]::GetBytes(${'$'}msg.Length); ${'$'}pidFile='$pidFilePath'; while(${'$'}true){ if(-not(Test-Path ${'$'}pidFile)){exit}; ${'$'}pid=[int](Get-Content ${'$'}pidFile -ErrorAction SilentlyContinue); if(-not(Get-Process -Id ${'$'}pid -ErrorAction SilentlyContinue)){exit}; ${'$'}stdout.Write(${'$'}lenBytes,0,4); ${'$'}stdout.Write(${'$'}msg,0,${'$'}msg.Length); ${'$'}stdout.Flush(); Start-Sleep -Seconds 5 }"
            """.trimIndent()
    }
}
