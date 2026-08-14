package com.crosspaste.cli.platform

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import platform.windows.CloseHandle
import platform.windows.DWORDVar
import platform.windows.ERROR_ACCESS_DENIED
import platform.windows.GetExitCodeProcess
import platform.windows.GetLastError
import platform.windows.OpenProcess
import platform.windows.PROCESS_QUERY_LIMITED_INFORMATION
import platform.windows.STILL_ACTIVE

@OptIn(ExperimentalForeignApi::class)
internal fun isProcessAlive(pid: Long): Boolean {
    if (pid <= 0) return false
    val handle =
        OpenProcess(PROCESS_QUERY_LIMITED_INFORMATION.toUInt(), 0, pid.toUInt())
            // ACCESS_DENIED still proves a live process owns this pid
            ?: return GetLastError() == ERROR_ACCESS_DENIED.toUInt()
    try {
        memScoped {
            val exitCode = alloc<DWORDVar>()
            if (GetExitCodeProcess(handle, exitCode.ptr) == 0) {
                // We could open the handle, so assume it is alive
                return true
            }
            // A process that really exited with code 259 (STILL_ACTIVE) is
            // misread as alive — a known Windows API ambiguity we accept
            return exitCode.value == STILL_ACTIVE
        }
    } finally {
        CloseHandle(handle)
    }
}
