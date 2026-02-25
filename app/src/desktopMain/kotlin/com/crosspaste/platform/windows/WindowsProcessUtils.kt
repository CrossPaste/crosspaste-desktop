package com.crosspaste.platform.windows

import com.crosspaste.platform.windows.api.User32
import com.sun.jna.Memory
import com.sun.jna.platform.win32.Kernel32
import com.sun.jna.platform.win32.Psapi
import com.sun.jna.platform.win32.Version
import com.sun.jna.platform.win32.WinDef.HWND
import com.sun.jna.platform.win32.WinNT
import com.sun.jna.ptr.IntByReference
import com.sun.jna.ptr.PointerByReference
import io.github.oshai.kotlinlogging.KotlinLogging
import okio.Path
import okio.Path.Companion.toOkioPath
import java.io.File
import java.nio.file.Paths
import java.util.Locale

object WindowsProcessUtils {

    private val logger = KotlinLogging.logger {}

    private val user32 = User32.INSTANCE

    fun getActiveWindowProcessFilePath(): String? =
        user32.GetForegroundWindow()?.let { hwnd ->
            val processIdRef = IntByReference()
            user32.GetWindowThreadProcessId(hwnd, processIdRef)
            val processHandle =
                Kernel32.INSTANCE.OpenProcess(
                    WinNT.PROCESS_QUERY_INFORMATION or WinNT.PROCESS_VM_READ,
                    false,
                    processIdRef.value,
                )

            runCatching {
                val bufferSize = 1024
                val memory = Memory((bufferSize * 2).toLong())
                if (Psapi.INSTANCE.GetModuleFileNameEx(
                        processHandle,
                        null,
                        memory,
                        bufferSize,
                    ) > 0
                ) {
                    memory.getWideString(0)
                } else {
                    null
                }
            }.apply {
                Kernel32.INSTANCE.CloseHandle(processHandle)
            }.getOrNull()
        }

    fun getFileDescription(path: Path): String? {
        val filePath = path.normalized().toString()
        val intByReference = IntByReference()
        val versionLength: Int =
            Version.INSTANCE.GetFileVersionInfoSize(filePath, intByReference)
        if (versionLength > 0) {
            val memory = Memory(versionLength.toLong())
            val lplpTranslate = PointerByReference()
            if (Version.INSTANCE.GetFileVersionInfo(filePath, 0, versionLength, memory)) {
                val puLen = IntByReference()
                if (Version.INSTANCE.VerQueryValue(
                        memory,
                        "\\VarFileInfo\\Translation",
                        lplpTranslate,
                        puLen,
                    )
                ) {
                    val array: IntArray = lplpTranslate.value.getIntArray(0L, puLen.value / 4)
                    val langAndCodepage =
                        findLangAndCodepage(
                            array,
                        ) ?: return null
                    val l: Int = langAndCodepage and 0xFFFF
                    val m: Int = (langAndCodepage and -65536) shr 16

                    val lang = String.format(Locale.ROOT, "%04x", l).takeLast(4)
                    val codepage = String.format(Locale.ROOT, "%04x", m).takeLast(4)
                    val lpSubBlock =
                        String.format(
                            Locale.ROOT,
                            "\\StringFileInfo\\$lang$codepage\\FileDescription",
                            l,
                            m,
                        )

                    val lplpBuffer = PointerByReference()
                    if (Version.INSTANCE.VerQueryValue(
                            memory,
                            lpSubBlock,
                            lplpBuffer,
                            puLen,
                        )
                    ) {
                        if (puLen.value > 0) {
                            return lplpBuffer.value.getWideString(0)
                        }
                    } else {
                        logger.error { "FileDescription GetLastError ${Kernel32.INSTANCE.GetLastError()}" }
                    }
                } else {
                    logger.error { "Translation GetLastError ${Kernel32.INSTANCE.GetLastError()}" }
                }
            }
        }
        return null
    }

    private fun findLangAndCodepage(array: IntArray): Int? {
        var value: Int? = null
        for (i in array) {
            if ((i and -65536) == 78643200 && (i and 65535) == 1033) {
                return i
            }
            value = i
        }

        return value
    }

    fun getThreadId(hwnd: HWND): Int {
        val processIdRef = IntByReference()
        return user32.GetWindowThreadProcessId(
            hwnd,
            processIdRef,
        )
    }

    fun getExeFilePath(hwnd: HWND): Path? {
        val processIdRef = IntByReference()
        user32.GetWindowThreadProcessId(
            hwnd,
            processIdRef,
        )
        val processHandle =
            Kernel32.INSTANCE.OpenProcess(
                WinNT.PROCESS_QUERY_INFORMATION or WinNT.PROCESS_VM_READ,
                false,
                processIdRef.value,
            )
        return try {
            val bufferSize = 1024
            val memory = Memory((bufferSize * 2).toLong())
            if (Psapi.INSTANCE.GetModuleFileNameEx(
                    processHandle,
                    null,
                    memory,
                    bufferSize,
                ) > 0
            ) {
                File(memory.getWideString(0)).toOkioPath()
            } else {
                null
            }
        } finally {
            Kernel32.INSTANCE.CloseHandle(processHandle)
        }
    }

    fun isInstalledFromMicrosoftStore(path: java.nio.file.Path): Boolean {
        val windowsAppsPath: java.nio.file.Path = Paths.get("C:\\Program Files\\WindowsApps").toAbsolutePath()
        return path.startsWith(windowsAppsPath)
    }
}
